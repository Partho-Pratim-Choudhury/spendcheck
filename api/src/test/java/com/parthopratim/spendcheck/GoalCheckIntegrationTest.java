package com.parthopratim.spendcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.parthopratim.spendcheck.check.CheckDecision;
import com.parthopratim.spendcheck.check.SpendCheckRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GoalCheckIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);

    @Autowired SpendCheckRepository checks;

    // ---------- goals ----------

    @Test
    void goalCrud() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        JsonNode bike = readJson(mvc.perform(asJson(u, post("/api/v1/goals"), Map.of(
                        "name", " Bike ", "targetPaise", 100_000 * RUPEE, "savedPaise", 25_000 * RUPEE,
                        "monthlyContributionPaise", 10_000 * RUPEE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bike"))
                .andExpect(jsonPath("$.targetDate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.progressPct").value(25))
                .andExpect(jsonPath("$.onTrackDate").value("2027-06-22")));   // 75,000 / 10,000 -> 8 months
        mvc.perform(asJson(u, post("/api/v1/goals"), Map.of(
                        "name", "Goa trip", "targetPaise", 50_000 * RUPEE, "monthlyContributionPaise", 0,
                        "targetDate", "2027-03-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedPaise").value(0))
                .andExpect(jsonPath("$.progressPct").value(0))
                .andExpect(jsonPath("$.onTrackDate").value(Matchers.nullValue()));

        // dated goal is primary, listed first
        mvc.perform(as(u, get("/api/v1/goals")))
                .andExpect(jsonPath("$[*].name", contains("Goa trip", "Bike")));

        long id = bike.get("id").asLong();
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("savedPaise", 100_000 * RUPEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bike"))
                .andExpect(jsonPath("$.progressPct").value(100))
                .andExpect(jsonPath("$.onTrackDate").value("2026-10-22"));
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("targetDate", "2026-12-01", "name", "Bike!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2026-12-01"));
        mvc.perform(as(u, get("/api/v1/goals"))).andExpect(jsonPath("$[0].name").value("Bike!"));
        // a target date can be cleared, but not cleared and set at once
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("clearTargetDate", true, "targetDate",
                        "2027-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.clearTargetDate").exists());
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("clearTargetDate", false)))
                .andExpect(jsonPath("$.targetDate").value("2026-12-01"));
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("clearTargetDate", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value(Matchers.nullValue()));
        mvc.perform(as(u, get("/api/v1/goals"))).andExpect(jsonPath("$[0].name").value("Goa trip"));

        mvc.perform(as(u, delete("/api/v1/goals/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(u, delete("/api/v1/goals/" + id))).andExpect(status().isNotFound());
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("name", "x"))).andExpect(status().isNotFound());
        mvc.perform(as(u, get("/api/v1/goals"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void goalValidation() throws Exception {
        TestUser u = newUser(7);
        mvc.perform(asJson(u, post("/api/v1/goals"), Map.of("name", " ", "targetPaise", 0,
                        "monthlyContributionPaise", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.targetPaise").exists())
                .andExpect(jsonPath("$.errors.monthlyContributionPaise").exists());
        mvc.perform(asJson(u, post("/api/v1/goals"), Map.of("name", "x", "targetPaise", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.monthlyContributionPaise").exists());
        long id = readJson(mvc.perform(asJson(u, post("/api/v1/goals"), Map.of("name", "x", "targetPaise", 10,
                "monthlyContributionPaise", 1)))).get("id").asLong();
        mvc.perform(asJson(u, patch("/api/v1/goals/" + id), Map.of("name", "  ", "savedPaise", -5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.savedPaise").exists());
    }

    // ---------- checks ----------

    @Test
    void referenceScenarioCheckIsRedAndSkipCountsAsPause() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        spend(u, 5_800 * RUPEE, "Shopping", LocalDate.of(2026, 10, 12), "Myntra", null);
        mvc.perform(asJson(u, post("/api/v1/goals"), Map.of("name", "Goa trip", "targetPaise", 60_000 * RUPEE,
                "monthlyContributionPaise", 10_000 * RUPEE, "targetDate", "2027-03-01")))
                .andExpect(status().isCreated());

        JsonNode v = readJson(mvc.perform(asJson(u, post("/api/v1/checks"),
                        Map.of("amountPaise", 3_500 * RUPEE, "categoryId", u.cat("Shopping"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkId").isNumber())
                .andExpect(jsonPath("$.level").value("RED"))
                .andExpect(jsonPath("$.amountPaise").value(350_000))
                .andExpect(jsonPath("$.categoryId").value((int) u.cat("Shopping")))
                .andExpect(jsonPath("$.categoryName").value("Shopping"))
                .andExpect(jsonPath("$.categoryLimitPaise").value(700_000))
                .andExpect(jsonPath("$.categorySpentPaise").value(580_000))
                .andExpect(jsonPath("$.categoryLeftPaise").value(120_000))
                .andExpect(jsonPath("$.overflowPaise").value(230_000))
                .andExpect(jsonPath("$.savingsHitPaise").value(230_000))
                .andExpect(jsonPath("$.goalName").value("Goa trip"))
                .andExpect(jsonPath("$.goalDelayDays").value(7))
                .andExpect(jsonPath("$.todayBudgetPaise").value(276_250))
                .andExpect(jsonPath("$.todaySpentPaise").value(0))
                .andExpect(jsonPath("$.safePerDayBeforePaise").value(276_250))
                .andExpect(jsonPath("$.safePerDayAfterPaise").value(254_375))
                .andExpect(jsonPath("$.reasons[0]").value("over category limit"))
                .andExpect(jsonPath("$.message").value(
                        "Over plan. ₹2,300 more than Shopping has left — it comes out of savings. "
                                + "Goa trip delayed ~7 days.")));
        long checkId = v.get("checkId").asLong();
        assertThat(checks.findById(checkId).orElseThrow().getDecision()).isEqualTo(CheckDecision.UNKNOWN);

        mvc.perform(asJson(u, post("/api/v1/checks/" + checkId + "/decision"), Map.of("decision", "SKIPPED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checkId))
                .andExpect(jsonPath("$.decision").value("SKIPPED"));
        mvc.perform(as(u, get("/api/v1/dashboard")))
                .andExpect(jsonPath("$.pausesSavedPaise").value(350_000))
                .andExpect(jsonPath("$.skippedCount").value(1));
        mvc.perform(as(u, get("/api/v1/summary/daily"))).andExpect(jsonPath("$.pausesSavedPaise").value(350_000));
    }

    @Test
    void greenCheckAndProceededDecisionLinksTransaction() throws Exception {
        TestUser u = newUser(7);
        TestUser other = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        long checkId = readJson(mvc.perform(asJson(u, post("/api/v1/checks"),
                        Map.of("amountPaise", 200 * RUPEE, "categoryId", u.cat("Food"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("GREEN"))
                .andExpect(jsonPath("$.goalName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.goalDelayDays").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.reasons", hasSize(0)))
                .andExpect(jsonPath("$.message").value("Fits. Food left after: ₹7,800.")))
                .get("checkId").asLong();

        UUID mine = spend(u, 200 * RUPEE, "Food", OCT_22, null, null);
        UUID theirs = spend(other, 200 * RUPEE, "Food", OCT_22, null, null);
        String url = "/api/v1/checks/" + checkId + "/decision";
        mvc.perform(asJson(u, post(url), Map.of("decision", "PROCEEDED", "transactionId", theirs.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.transactionId").exists());
        mvc.perform(asJson(u, post(url), Map.of("decision", "UNKNOWN"))).andExpect(status().isBadRequest());
        mvc.perform(asJson(u, post(url), Map.of("decision", "MAYBE"))).andExpect(status().isBadRequest());
        mvc.perform(asJson(u, post(url), Map.of())).andExpect(status().isBadRequest());
        mvc.perform(asJson(u, post(url), Map.of("decision", "PROCEEDED", "transactionId", mine.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PROCEEDED"));
        assertThat(checks.findById(checkId).orElseThrow().getTransactionId()).isEqualTo(mine);

        mvc.perform(asJson(u, post("/api/v1/checks/999999999/decision"), Map.of("decision", "SKIPPED")))
                .andExpect(status().isNotFound());
    }

    @Test
    void amberWhenOverTodaysSafeSpend() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        // today budget = 50,000 / 16 = 3,125; Groceries limit 10,000 so the half rule allows up to 5,000
        mvc.perform(asJson(u, post("/api/v1/checks"),
                        Map.of("amountPaise", 3_200 * RUPEE, "categoryId", u.cat("Groceries"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("AMBER"))
                .andExpect(jsonPath("$.reasons", contains("over today's safe spend")))
                .andExpect(jsonPath("$.message").value(
                        "Careful. Over today's safe spend. Groceries left after: ₹6,800."));
    }

    @Test
    void checkValidation() throws Exception {
        TestUser u = newUser(7);
        TestUser other = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        Map<String, Object> body = new HashMap<>();
        body.put("amountPaise", 0);
        mvc.perform(asJson(u, post("/api/v1/checks"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountPaise").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists());
        mvc.perform(asJson(u, post("/api/v1/checks"), Map.of("amountPaise", 100, "categoryId", other.cat("Food"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").value("category does not exist"));
        mvc.perform(asJson(u, post("/api/v1/checks"), Map.of("amountPaise", 100, "categoryId", u.cat("Old"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").value("category is archived"));
    }
}
