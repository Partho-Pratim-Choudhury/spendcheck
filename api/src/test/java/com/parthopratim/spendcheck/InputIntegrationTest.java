package com.parthopratim.spendcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.parthopratim.spendcheck.check.SpendCheckRepository;
import com.parthopratim.spendcheck.input.AiErrors;
import com.parthopratim.spendcheck.input.Extraction;
import com.parthopratim.spendcheck.input.InputCategory;
import com.parthopratim.spendcheck.input.InputIntent;
import com.parthopratim.spendcheck.input.QueryType;
import com.parthopratim.spendcheck.input.RawInput;
import com.parthopratim.spendcheck.input.RawInputRepository;
import com.parthopratim.spendcheck.input.RawInputType;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.NeedOrWant;
import com.parthopratim.spendcheck.ledger.TransactionRepository;
import com.parthopratim.spendcheck.ledger.TransactionStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class InputIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);
    private static final String QUOTA_BODY = "429 - {\"error\":{\"message\":\"You have no credits remaining.\","
            + "\"type\":\"insufficient_quota\",\"code\":\"credit_balance_exhausted\"}}";
    private static final String RATE_LIMIT_BODY = "429 - {\"error\":{\"message\":\"Rate limit reached for requests\","
            + "\"type\":\"requests\",\"code\":\"rate_limit_exceeded\"}}";

    @Autowired RawInputRepository rawInputs;
    @Autowired TransactionRepository transactions;
    @Autowired SpendCheckRepository checks;

    // ---------- helpers ----------

    private static Extraction spend(InputIntent intent, String amount, String category, String merchant,
                                    String date) {
        return new Extraction(intent, amount == null ? null : new BigDecimal(amount), category, merchant, null, date,
                null, null, null, 0.95);
    }

    private static Extraction query(QueryType type, String category) {
        return new Extraction(InputIntent.QUERY, null, null, null, null, null, null, type, category, 0.95);
    }

    private static Extraction bare(InputIntent intent) {
        return new Extraction(intent, null, null, null, null, null, null, null, null, 0.95);
    }

    private ResultActions text(TestUser u, String text) throws Exception {
        return mvc.perform(asJson(u, post("/api/v1/inputs/text"), Map.of("text", text)));
    }

    private ResultActions voice(TestUser u, MockMultipartFile file) throws Exception {
        return mvc.perform(as(u, multipart("/api/v1/inputs/voice").file(file)));
    }

    private static MockMultipartFile audio(String filename, String type, int bytes) {
        return new MockMultipartFile("audio", filename, type, new byte[bytes]);
    }

    private TestUser planned() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        return u;
    }

    private RawInput onlyRawInput(TestUser u) {
        List<RawInput> rows = rawInputs.findByUserIdOrderByIdAsc(u.id());
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    // ---------- LOG_EXPENSE ----------

    @Test
    void logExpenseReturnsDraftWithAfterAndPersistsRawInput() throws Exception {
        TestUser u = planned();
        spend(u, 1_000 * RUPEE, "Food", OCT_22.minusDays(1), "Cafe", null);
        extractor.respond(spend(InputIntent.LOG_EXPENSE, "450", "Food", "swiggy", null));

        JsonNode r = readJson(text(u, "  swiggy 450 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("LOG_EXPENSE"))
                .andExpect(jsonPath("$.transcript").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.draft.amountPaise").value(45_000))
                .andExpect(jsonPath("$.draft.categoryId").value(u.cat("Food")))
                .andExpect(jsonPath("$.draft.categoryName").value("Food"))
                .andExpect(jsonPath("$.draft.merchant").value("Swiggy"))
                .andExpect(jsonPath("$.draft.spentAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.draft.after.categorySpentPaise").value(1_450 * RUPEE))
                .andExpect(jsonPath("$.draft.after.categoryLimitPaise").value(8_000 * RUPEE))
                .andExpect(jsonPath("$.verdict").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.answer").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.followUp").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.undoneTransactionId").value(Matchers.nullValue())));

        assertThat(extractor.lastText).isEqualTo("swiggy 450");
        assertThat(extractor.lastToday).isEqualTo(OCT_22);
        RawInput raw = onlyRawInput(u);
        assertThat(r.get("rawInputId").asLong()).isEqualTo(raw.getId());
        assertThat(raw.getType()).isEqualTo(RawInputType.TEXT);
        assertThat(raw.getText()).isEqualTo("swiggy 450");
        assertThat(raw.getTranscript()).isNull();
        assertThat(raw.getModel()).isEqualTo("scripted-model");
        assertThat(raw.getLatencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
        JsonNode llm = json.readTree(raw.getLlmOutput());
        assertThat(llm.get("intent").asText()).isEqualTo("LOG_EXPENSE");
        assertThat(llm.get("merchant").asText()).isEqualTo("swiggy");

        // drafts are never saved; the app confirms with the rawInputId
        assertThat(transactions.findAll().stream().filter(t -> t.getUserId().equals(u.id()))).hasSize(1);
        Map<String, Object> body = new HashMap<>();
        body.put("id", UUID.randomUUID().toString());
        body.put("amountPaise", 45_000);
        body.put("categoryId", u.cat("Food"));
        body.put("spentAt", r.at("/draft/spentAt").asText());
        body.put("source", "TEXT");
        body.put("rawInputId", raw.getId());
        mvc.perform(asJson(u, post("/api/v1/transactions"), body)).andExpect(status().isCreated());
    }

    @Test
    void pastDateDraftIsNoonThatDayAndNeedOrWantPassesThrough() throws Exception {
        TestUser u = planned();
        extractor.respond(new Extraction(InputIntent.LOG_EXPENSE, new BigDecimal("2340"), "Groceries", "dmart",
                "weekly shop", "2026-10-21", NeedOrWant.NEED, null, null, 0.9));
        text(u, "kal dmart 2340 need")
                .andExpect(jsonPath("$.draft.spentAt")
                        .value(OCT_22.minusDays(1).atTime(LocalTime.NOON).atZone(IST).toInstant().toString()))
                .andExpect(jsonPath("$.draft.merchant").value("DMart"))
                .andExpect(jsonPath("$.draft.note").value("weekly shop"))
                .andExpect(jsonPath("$.draft.needOrWant").value("NEED"))
                .andExpect(jsonPath("$.draft.after.categorySpentPaise").value(2_340 * RUPEE));
    }

    @Test
    void logExpenseWithoutPlanUsesLimitZeroAndPeriodSpend() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        spend(u, 300 * RUPEE, "Food", OCT_22.minusDays(2), null, null);
        spend(u, 999 * RUPEE, "Food", LocalDate.of(2026, 10, 1), null, null);   // previous salary period
        extractor.respond(spend(InputIntent.LOG_EXPENSE, "450", "Food", null, null));
        text(u, "food 450")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.after.categorySpentPaise").value(750 * RUPEE))
                .andExpect(jsonPath("$.draft.after.categoryLimitPaise").value(0));
    }

    @Test
    void missingFieldsGiveFollowUpAndNoDraft() throws Exception {
        TestUser u = planned();
        extractor.respond(spend(InputIntent.LOG_EXPENSE, null, null, "Swiggy", null));
        text(u, "swiggy")   // "swiggy" is not one of this user's aliases, so no category either
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("LOG_EXPENSE"))
                .andExpect(jsonPath("$.draft").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.followUp.question").value(Matchers.startsWith("How much was it, and which")))
                .andExpect(jsonPath("$.followUp.missing", Matchers.contains("amount", "category")));
        assertThat(rawInputs.findByUserIdOrderByIdAsc(u.id())).hasSize(1);
    }

    @Test
    void archivedCategoryFromLlmIsRejected() throws Exception {
        TestUser u = planned();
        extractor.respond(spend(InputIntent.LOG_EXPENSE, "200", "Old", null, null));
        text(u, "200 thing")
                .andExpect(jsonPath("$.draft").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.followUp.missing", Matchers.contains("category")));
        assertThat(extractor.lastCategories).extracting(InputCategory::name).doesNotContain("Old");
    }

    @Test
    void aliasBeatsLlmCategory() throws Exception {
        TestUser u = planned();
        Category groceries = categories.findById(u.cat("Groceries")).orElseThrow();
        groceries.setAliases(List.of("dmart"));
        categories.save(groceries);
        extractor.respond(spend(InputIntent.LOG_EXPENSE, "2340", "Shopping", "DMart", null));
        text(u, "dmart 2340").andExpect(jsonPath("$.draft.categoryName").value("Groceries"));
    }

    @Test
    void futureOrOldDatesAskForTheDay() throws Exception {
        TestUser u = planned();
        extractor.respond(spend(InputIntent.LOG_EXPENSE, "450", "Food", null, "2026-10-23"));
        text(u, "food 450 tomorrow")
                .andExpect(jsonPath("$.draft").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.followUp.missing", Matchers.contains("date")));
        extractor.respond(spend(InputIntent.LOG_EXPENSE, "450", "Food", null, "2026-09-01"));
        text(u, "food 450 on 1 sep").andExpect(jsonPath("$.followUp.missing", Matchers.contains("date")));
    }

    @Test
    void lowConfidenceAsksToConfirm() throws Exception {
        TestUser u = planned();
        extractor.respond(new Extraction(InputIntent.LOG_EXPENSE, new BigDecimal("450"), "Food", null, null, null,
                null, null, null, 0.4));
        text(u, "sw 45o")
                .andExpect(jsonPath("$.draft").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.followUp.missing", Matchers.contains("confirmation")));
    }

    // ---------- PRE_CHECK ----------

    @Test
    void preCheckReturnsVerdictAndDraftAndRecordsCheck() throws Exception {
        TestUser u = planned();
        extractor.respond(spend(InputIntent.PRE_CHECK, "3500", "Shopping", null, null));
        JsonNode r = readJson(text(u, "can I buy shoes for 3500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PRE_CHECK"))
                .andExpect(jsonPath("$.verdict.level").value("AMBER"))
                .andExpect(jsonPath("$.verdict.amountPaise").value(350_000))
                .andExpect(jsonPath("$.verdict.categoryName").value("Shopping"))
                .andExpect(jsonPath("$.draft.amountPaise").value(350_000))
                .andExpect(jsonPath("$.draft.after.categorySpentPaise").value(350_000))
                .andExpect(jsonPath("$.draft.after.categoryLimitPaise").value(700_000))
                .andExpect(jsonPath("$.answer").value(Matchers.nullValue())));
        assertThat(checks.findByIdAndUserId(r.at("/verdict/checkId").asLong(), u.id())).isPresent();
    }

    @Test
    void preCheckWithoutPlanExplainsInsteadOfFailing() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        extractor.respond(spend(InputIntent.PRE_CHECK, "3500", "Shopping", null, null));
        text(u, "can I buy shoes for 3500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.draft.amountPaise").value(350_000))
                .andExpect(jsonPath("$.answer.text").value(Matchers.containsString("budget plan")));
    }

    // ---------- QUERY ----------

    @Test
    void queriesAreAnsweredFromEngineNumbers() throws Exception {
        TestUser u = planned();
        spend(u, 4_800 * RUPEE, "Food", OCT_22, null, null);
        extractor.respond(query(QueryType.CATEGORY_LEFT, "Food"));
        text(u, "how much left for food?")
                .andExpect(jsonPath("$.intent").value("QUERY"))
                .andExpect(jsonPath("$.answer.text").value("Food: ₹3,200 left of ₹8,000 (₹4,800 spent)."));

        extractor.respond(query(QueryType.TOTAL_SPENT, null));
        // discretionary = 1,20,000 − 50,000 fixed − 20,000 savings = 50,000; period Oct 7..Nov 6 → 16 days left
        text(u, "total spent so far")
                .andExpect(jsonPath("$.answer.text")
                        .value("You've spent ₹4,800 of ₹50,000 this period — ₹45,200 left with 16 days to go."));

        extractor.respond(query(QueryType.SAFE_TODAY, null));
        // today budget = 50,000 / 16 = 3,125; spent 4,800 today → over
        text(u, "how much can I spend today")
                .andExpect(jsonPath("$.answer.text").value(Matchers.startsWith("You've used today's safe spend — ")));

        extractor.respond(query(QueryType.GOAL_STATUS, null));
        text(u, "goal status").andExpect(jsonPath("$.answer.text").value(Matchers.containsString("goal yet")));
        mvc.perform(asJson(u, post("/api/v1/goals"), Map.of("name", "Goa trip", "targetPaise", 50_000 * RUPEE,
                "savedPaise", 20_000 * RUPEE, "monthlyContributionPaise", 10_000 * RUPEE)))
                .andExpect(status().isCreated());
        text(u, "goal status").andExpect(jsonPath("$.answer.text")
                .value("Goa trip: ₹20,000 of ₹50,000 saved (40%). On track for Jan 2027."));
    }

    @Test
    void goalStatusTellsNoContributionFromBeyondTheHorizon() throws Exception {
        TestUser u = planned();
        extractor.respond(query(QueryType.GOAL_STATUS, null));
        // ₹50,000 at ₹1/month: 50,000 months, past the 1,200-month horizon, so onTrackDate is null
        long id = readJson(mvc.perform(asJson(u, post("/api/v1/goals"), Map.of("name", "Car",
                "targetPaise", 50_000 * RUPEE, "savedPaise", 0, "monthlyContributionPaise", RUPEE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onTrackDate").value(Matchers.nullValue()))).get("id").asLong();
        text(u, "goal status").andExpect(jsonPath("$.answer.text").value("Car: ₹0 of ₹50,000 saved (0%). At ₹1/month "
                + "it would take over 100 years — raise the contribution to get there sooner."));
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("monthlyContributionPaise", 0)))
                .andExpect(status().isOk());
        text(u, "goal status").andExpect(jsonPath("$.answer.text").value("Car: ₹0 of ₹50,000 saved (0%). Set a "
                + "monthly contribution to see when you'll get there."));
    }

    @Test
    void safeTodayWithRoomLeft() throws Exception {
        TestUser u = planned();
        extractor.respond(query(QueryType.SAFE_TODAY, null));
        text(u, "safe spend today").andExpect(jsonPath("$.answer.text").value("You can safely spend ₹3,125 today."));
    }

    @Test
    void queryWithoutPlanExplainsPlanNeeded() throws Exception {
        TestUser u = newUser(7);
        extractor.respond(query(QueryType.TOTAL_SPENT, null));
        text(u, "how much have I spent")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer.text").value(Matchers.containsString("budget plan")));
    }

    @Test
    void categoryQueryWithoutCategoryAsks() throws Exception {
        TestUser u = planned();
        extractor.respond(query(QueryType.CATEGORY_LEFT, null));
        text(u, "how much is left in it")
                .andExpect(jsonPath("$.answer").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.followUp.missing", Matchers.contains("category")));
    }

    @Test
    void starterModelBeansAreNotAutoConfigured(@Autowired ApplicationContext ctx) {
        assertThat(ctx.getBeanNamesForType(ChatModel.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(OpenAiAudioTranscriptionModel.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(OpenAiAudioSpeechModel.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(ImageModel.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(ModerationModel.class)).isEmpty();
    }

    // ---------- UNDO ----------

    @Test
    void undoProposesMostRecentChatSpendAndCancelsNothing() throws Exception {
        TestUser u = newUser(7);
        Instant now = clock.instant();
        UUID older = spendVia(u, "TEXT", 100 * RUPEE, "Groceries", now.minus(Duration.ofDays(1)), null, null);
        UUID latest = spendVia(u, "VOICE", 450 * RUPEE, "Food", now.minus(Duration.ofDays(3)), "Swiggy", null);
        spend(u, 999 * RUPEE, "Shopping", LocalDate.now(IST), null, null);   // newest, but MANUAL: never proposed
        extractor.respond(bare(InputIntent.UNDO));

        for (int i = 0; i < 2; i++) {   // asking twice proposes the same spend: nothing was cancelled
            text(u, "undo")
                    .andExpect(jsonPath("$.intent").value("UNDO"))
                    .andExpect(jsonPath("$.undoCandidate.transactionId").value(latest.toString()))
                    .andExpect(jsonPath("$.undoCandidate.amountPaise").value(45_000))
                    .andExpect(jsonPath("$.undoCandidate.categoryName").value("Food"))
                    .andExpect(jsonPath("$.undoCandidate.merchant").value("Swiggy"))
                    .andExpect(jsonPath("$.undoCandidate.spentAt").exists())
                    .andExpect(jsonPath("$.undoneTransactionId").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.answer.text").value("Remove ₹450 · Food · Swiggy?"));
        }
        assertThat(transactions.findById(latest).orElseThrow().getStatus()).isEqualTo(TransactionStatus.CONFIRMED);

        // the app confirms with DELETE; then the next one is proposed
        mvc.perform(as(u, delete("/api/v1/transactions/" + latest))).andExpect(status().isNoContent());
        text(u, "undo")
                .andExpect(jsonPath("$.undoCandidate.transactionId").value(older.toString()))
                .andExpect(jsonPath("$.undoCandidate.merchant").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.answer.text").value("Remove ₹100 · Groceries?"));
        mvc.perform(as(u, delete("/api/v1/transactions/" + older))).andExpect(status().isNoContent());
        text(u, "undo")
                .andExpect(jsonPath("$.undoCandidate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.undoneTransactionId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.answer.text").value("Nothing recent to undo."));
    }

    @Test
    void undoIgnoresTransactionsOlderThan24hAndOtherUsers() throws Exception {
        TestUser u = newUser(7);
        TestUser other = newUser(7);
        UUID mine = spendVia(u, "TEXT", 100 * RUPEE, "Food", clock.instant(), null, null);
        UUID theirs = spendVia(other, "TEXT", 200 * RUPEE, "Food", clock.instant(), null, null);
        extractor.respond(bare(InputIntent.UNDO));

        clock.set(clock.instant().plus(Duration.ofHours(25)));
        text(u, "undo")
                .andExpect(jsonPath("$.undoCandidate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.answer.text").value("Nothing recent to undo."));
        clock.reset();

        TestUser third = newUser(7);
        text(third, "undo").andExpect(jsonPath("$.undoCandidate").value(Matchers.nullValue()));
        assertThat(transactions.findById(mine).orElseThrow().getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(transactions.findById(theirs).orElseThrow().getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
    }

    @Test
    void unsureUndoAsksFirst() throws Exception {
        TestUser u = newUser(7);
        UUID t = spend(u, 100 * RUPEE, "Food", LocalDate.now(IST), null, null);
        extractor.respond(new Extraction(InputIntent.UNDO, null, null, null, null, null, null, null, null, 0.5));
        text(u, "remove?").andExpect(jsonPath("$.followUp.missing", Matchers.contains("confirmation")));
        assertThat(transactions.findById(t).orElseThrow().getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
    }

    // ---------- OTHER / validation ----------

    @Test
    void otherGetsHelp() throws Exception {
        TestUser u = newUser(7);
        extractor.respond(bare(InputIntent.OTHER));
        text(u, "hi")
                .andExpect(jsonPath("$.intent").value("OTHER"))
                .andExpect(jsonPath("$.answer.text").value(Matchers.containsString("undo")));
    }

    @Test
    void textValidation() throws Exception {
        TestUser u = newUser(7);
        text(u, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.text").exists());
        text(u, "x".repeat(501)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.text").exists());
        mvc.perform(asJson(u, post("/api/v1/inputs/text"), Map.of())).andExpect(status().isBadRequest());
        text(u, "x".repeat(500)).andExpect(status().isServiceUnavailable());   // valid; unscripted → real → 503
        mvc.perform(post("/api/v1/inputs/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- provider failures ----------

    @Nested
    class ProviderFailures {

        @Test
        void unconfiguredKeyIs503AndInputStillRecorded() throws Exception {
            TestUser u = newUser(7);
            text(u, "swiggy 450")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value(AiErrors.NOT_CONFIGURED));
            RawInput raw = onlyRawInput(u);
            assertThat(raw.getText()).isEqualTo("swiggy 450");
            assertThat(raw.getLlmOutput()).isNull();
            voice(u, audio("voice.m4a", "audio/mp4", 100))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value(AiErrors.NOT_CONFIGURED));
        }

        @Test
        void extractorFailureIs502() throws Exception {
            TestUser u = newUser(7);
            extractor.fail(new IllegalStateException("LLM returned malformed JSON"));
            text(u, "swiggy 450")
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value(AiErrors.EXTRACT_FAILED));
            extractor.fail(new TransientAiException("500 - upstream error"));
            text(u, "swiggy 450").andExpect(status().isBadGateway());
        }

        @Test
        void noCreditsIs503WithBillingMessage() throws Exception {
            TestUser u = newUser(7);
            extractor.fail(new NonTransientAiException(QUOTA_BODY));
            text(u, "swiggy 450")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value("The OpenAI account has no credits. Add credits at "
                            + "platform.openai.com/settings/billing, then try again."));
            speechToText.fail(new NonTransientAiException(QUOTA_BODY));
            voice(u, audio("voice.m4a", "audio/mp4", 100))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value(AiErrors.NO_CREDITS));
        }

        @Test
        void plainRateLimitIs502Busy() throws Exception {
            TestUser u = newUser(7);
            extractor.fail(new NonTransientAiException(RATE_LIMIT_BODY));
            text(u, "swiggy 450")
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value(AiErrors.BUSY));
            speechToText.fail(new NonTransientAiException(RATE_LIMIT_BODY));
            voice(u, audio("voice.m4a", "audio/mp4", 100))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value(AiErrors.BUSY));
        }

        @Test
        void transcriptionFailureIs502() throws Exception {
            TestUser u = newUser(7);
            speechToText.fail(new RuntimeException("connection reset"));
            voice(u, audio("voice.m4a", "audio/mp4", 100))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value(Matchers.startsWith("Couldn't transcribe")));
            RawInput raw = onlyRawInput(u);
            assertThat(raw.getType()).isEqualTo(RawInputType.VOICE);
            assertThat(raw.getTranscript()).isNull();
        }
    }

    // ---------- voice ----------

    @Nested
    class Voice {

        @Test
        void validVoiceNoteIsTranscribedAndUnderstood() throws Exception {
            TestUser u = planned();
            Category food = categories.findById(u.cat("Food")).orElseThrow();
            food.setAliases(List.of("swiggy", "zomato"));
            categories.save(food);
            speechToText.respond(" Swiggy 450 ");
            extractor.respond(spend(InputIntent.LOG_EXPENSE, "450", "Food", "Swiggy", null));

            JsonNode r = readJson(voice(u, audio("voice.m4a", "audio/mp4", 2_000))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transcript").value("Swiggy 450"))
                    .andExpect(jsonPath("$.draft.amountPaise").value(45_000)));
            assertThat(speechToText.lastFilename).isEqualTo("voice.m4a");
            assertThat(speechToText.lastSize).isEqualTo(2_000);
            assertThat(speechToText.lastHints).contains("swiggy", "zomato");
            assertThat(extractor.lastText).isEqualTo("Swiggy 450");
            RawInput raw = onlyRawInput(u);
            assertThat(raw.getId()).isEqualTo(r.get("rawInputId").asLong());
            assertThat(raw.getType()).isEqualTo(RawInputType.VOICE);
            assertThat(raw.getText()).isNull();
            assertThat(raw.getTranscript()).isEqualTo("Swiggy 450");
            assertThat(raw.getLlmOutput()).contains("LOG_EXPENSE");
        }

        @Test
        void acceptedTypes() throws Exception {
            TestUser u = newUser(7);
            speechToText.respond("hi");
            extractor.respond(bare(InputIntent.OTHER));
            for (String type : List.of("audio/m4a", "audio/x-m4a", "audio/aac", "audio/mpeg", "audio/webm;codecs=opus",
                    "audio/ogg", "audio/wav", "audio/x-wav")) {
                voice(u, audio("rec", type, 10)).andExpect(status().isOk());
            }
            voice(u, audio("rec.webm", "application/octet-stream", 10)).andExpect(status().isOk());
            assertThat(speechToText.lastFilename).isEqualTo("voice.webm");
        }

        @Test
        void emptyTooLargeOrWrongTypeIs400() throws Exception {
            TestUser u = newUser(7);
            speechToText.respond("hi");
            extractor.respond(bare(InputIntent.OTHER));
            voice(u, audio("voice.m4a", "audio/mp4", 0))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.audio").exists());
            voice(u, audio("voice.m4a", "audio/mp4", 2 * 1024 * 1024 + 1))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.audio").exists());
            voice(u, audio("notes.txt", "text/plain", 10))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.audio").exists());
            voice(u, audio("blob.bin", "application/octet-stream", 10)).andExpect(status().isBadRequest());
            mvc.perform(as(u, multipart("/api/v1/inputs/voice"))).andExpect(status().isBadRequest());
            assertThat(rawInputs.findByUserIdOrderByIdAsc(u.id())).isEmpty();
        }

        @Test
        void silentVoiceNoteSaysSo() throws Exception {
            TestUser u = newUser(7);
            speechToText.respond("  ");
            voice(u, audio("voice.m4a", "audio/mp4", 100))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.intent").value("OTHER"))
                    .andExpect(jsonPath("$.answer.text").value(Matchers.containsString("couldn't hear")));
            assertThat(extractor.lastText).isNull();
        }
    }

    // ---------- scoping ----------

    @Test
    void pipelineSeesOnlyTheUsersOwnCategoriesAndRawInputs() throws Exception {
        TestUser a = newUser(7);
        TestUser b = newUser(7);
        extractor.respond(bare(InputIntent.OTHER));
        long rawA = readJson(text(a, "hi")).get("rawInputId").asLong();
        assertThat(extractor.lastCategories).extracting(InputCategory::id)
                .containsExactlyInAnyOrderElementsOf(a.cats().entrySet().stream()
                        .filter(e -> !e.getKey().equals("Old")).map(Map.Entry::getValue).toList());

        Map<String, Object> body = new HashMap<>();
        body.put("id", UUID.randomUUID().toString());
        body.put("amountPaise", 100);
        body.put("categoryId", b.cat("Food"));
        body.put("spentAt", clock.instant().toString());
        body.put("source", "TEXT");
        body.put("rawInputId", rawA);
        mvc.perform(asJson(b, post("/api/v1/transactions"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rawInputId").exists());
        mvc.perform(as(b, get("/api/v1/transactions"))).andExpect(jsonPath("$", Matchers.hasSize(0)));
    }
}
