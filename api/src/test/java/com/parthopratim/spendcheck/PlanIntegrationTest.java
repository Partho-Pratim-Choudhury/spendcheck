package com.parthopratim.spendcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parthopratim.spendcheck.auth.User;
import com.parthopratim.spendcheck.check.SpendCheckRepository;
import com.parthopratim.spendcheck.input.RawInput;
import com.parthopratim.spendcheck.input.RawInputRepository;
import com.parthopratim.spendcheck.input.RawInputType;
import com.parthopratim.spendcheck.ledger.TransactionRepository;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.plan.PlanService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class PlanIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);

    @Autowired PlanService planService;
    @Autowired TransactionRepository transactions;
    @Autowired RawInputRepository rawInputs;
    @Autowired SpendCheckRepository checks;

    @Test
    void noPlanMeans404ForCurrentAndDashboardAnd409ForChecks() throws Exception {
        TestUser u = newUser(7);
        mvc.perform(as(u, get("/api/v1/plans/current")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(as(u, get("/api/v1/dashboard"))).andExpect(status().isNotFound());
        mvc.perform(as(u, get("/api/v1/reports/monthly"))).andExpect(status().isNotFound());
        mvc.perform(as(u, get("/api/v1/summary/daily"))).andExpect(status().isNotFound());
        mvc.perform(as(u, get("/api/v1/plans"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(asJson(u, post("/api/v1/checks"), Map.of("amountPaise", 100, "categoryId", u.cat("Food"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void putCreatesPlanForPeriodContainingTodayWithDerivedNumbers() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        mvc.perform(as(u, get("/api/v1/plans/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.periodStart").value("2026-10-07"))
                .andExpect(jsonPath("$.periodEnd").value("2026-11-06"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.incomePaise").value(12_000_000))
                .andExpect(jsonPath("$.savingsTargetPaise").value(2_000_000))
                .andExpect(jsonPath("$.limits", hasSize(6)))
                .andExpect(jsonPath("$.limits[?(@.categoryName == 'Rent')].kind").value("FIXED"))
                .andExpect(jsonPath("$.limits[?(@.categoryName == 'Shopping')].limitPaise").value(700_000))
                .andExpect(jsonPath("$.limits[?(@.categoryName == 'Shopping')].categoryId").value((int) u.cat("Shopping")))
                .andExpect(jsonPath("$.fixedTotalPaise").value(5_000_000))
                .andExpect(jsonPath("$.variableLimitsPaise").value(2_500_000))
                .andExpect(jsonPath("$.discretionaryPaise").value(5_000_000))
                .andExpect(jsonPath("$.unallocatedPaise").value(2_500_000))
                .andExpect(jsonPath("$.dailyBudgetPaise").value(5_000_000 / 31));
    }

    @Test
    void putReplacesTheActivePlanInPlace() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        long id = putReferencePlan(u).get("id").asLong();
        Map<String, Long> limits = new LinkedHashMap<>();
        limits.put("Rent", 30_000 * RUPEE);       // updated
        limits.put("Shopping", 7_000 * RUPEE);    // kept
        limits.put("Misc", 1_000 * RUPEE);        // added (Food, Groceries, EMI, SIP removed)
        mvc.perform(asJson(u, put("/api/v1/plans/current"), planBody(u, 100_000 * RUPEE, 0, limits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.incomePaise").value(10_000_000))
                .andExpect(jsonPath("$.limits", hasSize(3)))
                .andExpect(jsonPath("$.limits[?(@.categoryName == 'Rent')].limitPaise").value(3_000_000))
                .andExpect(jsonPath("$.fixedTotalPaise").value(3_000_000))
                .andExpect(jsonPath("$.discretionaryPaise").value(7_000_000));
        mvc.perform(as(u, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void putValidation() throws Exception {
        TestUser u = newUser(7);
        TestUser other = newUser(7);
        today(OCT_22);
        String url = "/api/v1/plans/current";

        mvc.perform(asJson(u, put(url), Map.of("incomePaise", 0, "savingsTargetPaise", -1, "limits", List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.incomePaise").exists())
                .andExpect(jsonPath("$.errors.savingsTargetPaise").exists());
        mvc.perform(asJson(u, put(url), Map.of("incomePaise", 100, "savingsTargetPaise", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limits").exists());
        mvc.perform(asJson(u, put(url), Map.of("incomePaise", 100, "savingsTargetPaise", 0,
                        "limits", List.of(Map.of("categoryId", u.cat("Food"), "limitPaise", -1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['limits[0].limitPaise']").exists());

        List<Map<String, Object>> bad = List.of(
                Map.of("categoryId", u.cat("Food"), "limitPaise", 10),
                Map.of("categoryId", 999_999_999L, "limitPaise", 10),
                Map.of("categoryId", u.cat("Old"), "limitPaise", 10),
                Map.of("categoryId", u.cat("Food"), "limitPaise", 10),
                Map.of("categoryId", other.cat("Food"), "limitPaise", 10));
        mvc.perform(asJson(u, put(url), Map.of("incomePaise", 100_000, "savingsTargetPaise", 0, "limits", bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors['limits[0].categoryId']").doesNotExist())
                .andExpect(jsonPath("$.errors['limits[1].categoryId']").value("category does not exist"))
                .andExpect(jsonPath("$.errors['limits[2].categoryId']").value("category is archived"))
                .andExpect(jsonPath("$.errors['limits[3].categoryId']").value("duplicate category"))
                .andExpect(jsonPath("$.errors['limits[4].categoryId']").value("category does not exist"));

        // income − fixed − savings < 0
        mvc.perform(asJson(u, put(url), planBody(u, 50_000, 10_000, Map.of("Rent", 40_001L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.savingsTargetPaise").exists());
        mvc.perform(as(u, get("/api/v1/plans/current"))).andExpect(status().isNotFound());

        // exactly zero discretionary is fine; over-allocation is allowed but reported
        mvc.perform(asJson(u, put(url), planBody(u, 50_000, 10_000, Map.of("Rent", 40_000L, "Food", 200L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discretionaryPaise").value(0))
                .andExpect(jsonPath("$.unallocatedPaise").value(-200));
    }

    @Test
    void getCurrentRollsAStalePlanForwardThroughMissedPeriods() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        today(LocalDate.of(2026, 12, 10));
        mvc.perform(as(u, get("/api/v1/plans/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-12-07"))
                .andExpect(jsonPath("$.periodEnd").value("2027-01-06"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.incomePaise").value(12_000_000))
                .andExpect(jsonPath("$.limits", hasSize(6)));
        mvc.perform(as(u, get("/api/v1/plans")))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].periodStart").value("2026-12-07"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$[1].periodEnd").value("2026-12-06"))
                .andExpect(jsonPath("$[1].status").value("CLOSED"))
                .andExpect(jsonPath("$[2].periodStart").value("2026-10-07"))
                .andExpect(jsonPath("$[2].status").value("CLOSED"));
    }

    @Test
    void dailyJobRollsEndedPlansAndLeavesCurrentOnesAlone() throws Exception {
        TestUser ended = newUser(7);
        TestUser current = newUser(20);
        today(OCT_22);
        putReferencePlan(ended);     // 10-07..11-06
        putReferencePlan(current);   // 10-20..11-19
        today(LocalDate.of(2026, 11, 7));
        assertThat(planService.rolloverAllDue()).isGreaterThanOrEqualTo(1);
        // GET /plans does not itself roll over, so this shows the job's work
        mvc.perform(as(ended, get("/api/v1/plans")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$[0].periodEnd").value("2026-12-06"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("CLOSED"));
        mvc.perform(as(current, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(1)));
        // running it again is a no-op for this user
        planService.rolloverAllDue();
        mvc.perform(as(ended, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void manualRolloverEarlyClosesAndTrimsTheOldPlan() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        today(LocalDate.of(2026, 11, 5));   // salary two days early
        mvc.perform(as(u, post("/api/v1/plans/rollover")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodStart").value("2026-11-05"))
                .andExpect(jsonPath("$.periodEnd").value("2026-12-06"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.discretionaryPaise").value(5_000_000))
                .andExpect(jsonPath("$.limits", hasSize(6)));
        mvc.perform(as(u, get("/api/v1/plans")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].periodEnd").value("2026-11-04"))
                .andExpect(jsonPath("$[1].status").value("CLOSED"));
    }

    @Test
    void manualRolloverLateKeepsTheGapDaysCovered() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        today(LocalDate.of(2026, 11, 10));  // salary three days late, plan already ended
        mvc.perform(as(u, post("/api/v1/plans/rollover")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodStart").value("2026-11-10"))
                .andExpect(jsonPath("$.periodEnd").value("2026-12-06"));
        mvc.perform(as(u, get("/api/v1/plans")))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$[1].periodEnd").value("2026-11-09"))
                .andExpect(jsonPath("$[2].periodEnd").value("2026-11-06"));
    }

    @Test
    void manualRolloverOnSalaryDayAfterAutoRolloverKeepsThePlan() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        today(LocalDate.of(2026, 11, 7));   // auto rollover opens 11-07..12-06, then salary arrives today
        mvc.perform(as(u, get("/api/v1/plans/current"))).andExpect(jsonPath("$.periodStart").value("2026-11-07"));
        mvc.perform(as(u, post("/api/v1/plans/rollover")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$.periodEnd").value("2026-12-06"));
        mvc.perform(as(u, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void manualRolloverWithoutAnyPlanIs409() throws Exception {
        TestUser u = newUser(7);
        mvc.perform(as(u, post("/api/v1/plans/rollover"))).andExpect(status().isConflict());
    }

    @Test
    void rolloverPreviewWithoutAnyPlanIs409() throws Exception {
        TestUser u = newUser(7);
        mvc.perform(as(u, get("/api/v1/plans/rollover/preview")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void rolloverPreviewMatchesWhatRolloverCreatesAndChangesNothing() throws Exception {
        // mid-period (far from salary day), early, late (plan already ended) and on salary day after auto rollover
        assertPreviewMatchesRollover(LocalDate.of(2026, 10, 23), "2026-10-23", "2026-12-06", 45);
        assertPreviewMatchesRollover(LocalDate.of(2026, 11, 5), "2026-11-05", "2026-12-06", 32);
        assertPreviewMatchesRollover(LocalDate.of(2026, 11, 10), "2026-11-10", "2026-12-06", 27);
        assertPreviewMatchesRollover(LocalDate.of(2026, 11, 7), "2026-11-07", "2026-12-06", 30);
    }

    private void assertPreviewMatchesRollover(LocalDate day, String start, String end, int days) throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        today(day);
        mvc.perform(as(u, get("/api/v1/plans/current"))); // settle any due auto rollover before counting plans
        int before = readJson(mvc.perform(as(u, get("/api/v1/plans")))).size();
        for (int i = 0; i < 2; i++) {
            mvc.perform(as(u, get("/api/v1/plans/rollover/preview")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodStart").value(start))
                    .andExpect(jsonPath("$.periodEnd").value(end))
                    .andExpect(jsonPath("$.daysTotal").value(days));
        }
        mvc.perform(as(u, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(before)));
        mvc.perform(as(u, post("/api/v1/plans/rollover")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodStart").value(start))
                .andExpect(jsonPath("$.periodEnd").value(end));
    }

    @Test
    void salaryDayAffectsFuturePeriodsOnly() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        mvc.perform(asJson(u, put("/api/v1/users/me/salary-day"), Map.of("salaryDay", 15)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(u.id()))
                .andExpect(jsonPath("$.email").value(u.user().getEmail()))
                .andExpect(jsonPath("$.name").value("Tester"))
                .andExpect(jsonPath("$.salaryDay").value(15));
        mvc.perform(as(u, get("/api/v1/me"))).andExpect(jsonPath("$.salaryDay").value(15));
        mvc.perform(as(u, get("/api/v1/plans/current"))).andExpect(jsonPath("$.periodEnd").value("2026-11-06"));
        today(LocalDate.of(2026, 11, 8));
        mvc.perform(as(u, get("/api/v1/plans/current")))
                .andExpect(jsonPath("$.periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$.periodEnd").value("2026-11-14"));

        for (Object bad : new Object[] {0, 32}) {
            mvc.perform(asJson(u, put("/api/v1/users/me/salary-day"), Map.of("salaryDay", bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.salaryDay").exists());
        }
    }

    @Test
    void transactionsGetThePlanCoveringTheirDate() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        UUID beforePlanExisted = spend(u, 100, "Food", LocalDate.of(2026, 10, 10), null, null);
        long planId = putReferencePlan(u).get("id").asLong();
        UUID inPeriod = spend(u, 100, "Food", LocalDate.of(2026, 10, 20), null, null);
        UUID earlier = spend(u, 100, "Food", LocalDate.of(2026, 10, 1), null, null);
        // 2026-10-06T19:00Z is 00:30 IST on 10-07: inside the period
        UUID istMidnight = spendAt(u, 100, "Food", Instant.parse("2026-10-06T19:00:00Z"), null, null);

        assertThat(transactions.findById(inPeriod).orElseThrow().getPlanId()).isEqualTo(planId);
        assertThat(transactions.findById(beforePlanExisted).orElseThrow().getPlanId()).isEqualTo(planId);
        assertThat(transactions.findById(istMidnight).orElseThrow().getPlanId()).isEqualTo(planId);
        assertThat(transactions.findById(earlier).orElseThrow().getPlanId()).isNull();

        // moving a spend out of the period clears its plan
        mvc.perform(asJson(u, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/transactions/" + inPeriod), Map.of("spentAt", "2026-09-30T06:00:00Z")))
                .andExpect(status().isOk());
        assertThat(transactions.findById(inPeriod).orElseThrow().getPlanId()).isNull();

        // after rollover, the new period's spends belong to the new plan
        today(LocalDate.of(2026, 11, 8));
        long next = readJson(mvc.perform(as(u, get("/api/v1/plans/current")))).get("id").asLong();
        UUID nov = spend(u, 100, "Food", LocalDate.of(2026, 11, 8), null, null);
        assertThat(transactions.findById(nov).orElseThrow().getPlanId()).isEqualTo(next);
    }

    @Test
    void transactionRawInputIdMustBeTheUsers() throws Exception {
        TestUser u = newUser(7);
        TestUser other = newUser(7);
        long mine = rawInputs.save(new RawInput(u.id(), RawInputType.TEXT, "chai 20", null)).getId();
        long theirs = rawInputs.save(new RawInput(other.id(), RawInputType.TEXT, "chai 20", null)).getId();

        UUID id = UUID.randomUUID();
        mvc.perform(asJson(u, post("/api/v1/transactions"), txBody(u, id, mine))).andExpect(status().isCreated());
        assertThat(transactions.findById(id).orElseThrow().getRawInputId()).isEqualTo(mine);

        mvc.perform(asJson(u, post("/api/v1/transactions"), txBody(u, UUID.randomUUID(), theirs)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rawInputId").exists());
        mvc.perform(asJson(u, post("/api/v1/transactions"), txBody(u, UUID.randomUUID(), 999_999_999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rawInputId").exists());
    }

    @Test
    void patchWithBlankMerchantAndNoteClearsThem() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        UUID id = spend(u, 100, "Food", OCT_22, "Swiggy", null);
        mvc.perform(asJson(u, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/transactions/" + id), Map.of("merchant", "", "note", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.nullValue()));
        assertThat(transactions.findById(id).orElseThrow().getPlanId()).isNotNull();
    }

    private Map<String, Object> txBody(TestUser u, UUID id, long rawInputId) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        body.put("amountPaise", 2000);
        body.put("categoryId", u.cat("Food"));
        body.put("spentAt", Instant.now().toString());
        body.put("source", "TEXT");
        body.put("rawInputId", rawInputId);
        return body;
    }


    // ---------- review fixes ----------

    @Test
    void backdatedSpendIntoAClosedPeriodGetsThatPlan() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        long october = putReferencePlan(u).get("id").asLong();
        today(LocalDate.of(2026, 11, 8));
        long november = readJson(mvc.perform(as(u, get("/api/v1/plans/current")))).get("id").asLong();

        UUID backdated = spend(u, 100, "Food", LocalDate.of(2026, 10, 30), null, null);
        assertThat(transactions.findById(backdated).orElseThrow().getPlanId()).isEqualTo(october);

        UUID moved = spend(u, 100, "Food", LocalDate.of(2026, 11, 8), null, null);
        assertThat(transactions.findById(moved).orElseThrow().getPlanId()).isEqualTo(november);
        mvc.perform(asJson(u, patch("/api/v1/transactions/" + moved), Map.of("spentAt", "2026-10-25T06:00:00Z")))
                .andExpect(status().isOk());
        assertThat(transactions.findById(moved).orElseThrow().getPlanId()).isEqualTo(october);
    }

    @Test
    void rolloverDropsLimitsOfArchivedCategories() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        mvc.perform(asJson(u, patch("/api/v1/categories/" + u.cat("Shopping")), Map.of("archived", true)))
                .andExpect(status().isOk());
        today(LocalDate.of(2026, 11, 8));
        mvc.perform(as(u, get("/api/v1/plans/current")))
                .andExpect(jsonPath("$.periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$.limits", hasSize(5)))
                .andExpect(jsonPath("$.limits[?(@.name == 'Shopping')]", hasSize(0)));
    }

    @Test
    void manualRolloverMovesTodaysChecksToTheNewPlan() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        long october = putReferencePlan(u).get("id").asLong();
        long earlyCheck = readJson(mvc.perform(asJson(u, post("/api/v1/checks"),
                Map.of("amountPaise", 100, "categoryId", u.cat("Food"))))).get("checkId").asLong();
        today(LocalDate.of(2026, 11, 5));   // salary early
        long todayCheck = readJson(mvc.perform(asJson(u, post("/api/v1/checks"),
                Map.of("amountPaise", 100, "categoryId", u.cat("Food"))))).get("checkId").asLong();
        assertThat(checks.findById(todayCheck).orElseThrow().getPlanId()).isEqualTo(october);

        long november = readJson(mvc.perform(as(u, post("/api/v1/plans/rollover")))).get("id").asLong();
        assertThat(checks.findById(todayCheck).orElseThrow().getPlanId()).isEqualTo(november);
        assertThat(checks.findById(earlyCheck).orElseThrow().getPlanId()).isEqualTo(october);
    }

    @Test
    void kindChangeThatWouldMakeDiscretionaryNegativeIs409() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        Map<String, Long> limits = new LinkedHashMap<>();
        limits.put("Rent", 25_000 * RUPEE);
        limits.put("Groceries", 10_000 * RUPEE);
        limits.put("Food", 1_000 * RUPEE);
        // discretionary = 1,00,000 − 25,000 − 70,000 = 5,000
        mvc.perform(asJson(u, put("/api/v1/plans/current"), planBody(u, 100_000 * RUPEE, 70_000 * RUPEE, limits)))
                .andExpect(status().isOk());

        mvc.perform(asJson(u, patch("/api/v1/categories/" + u.cat("Groceries")), Map.of("kind", "FIXED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Groceries")));
        mvc.perform(as(u, get("/api/v1/categories"))).andExpect(
                jsonPath("$[?(@.name == 'Groceries')].kind", org.hamcrest.Matchers.contains("NEED")));
        // fits (5,000 − 1,000 ≥ 0), or not in the plan, or away from FIXED: allowed
        mvc.perform(asJson(u, patch("/api/v1/categories/" + u.cat("Food")), Map.of("kind", "FIXED")))
                .andExpect(status().isOk());
        mvc.perform(asJson(u, patch("/api/v1/categories/" + u.cat("Shopping")), Map.of("kind", "FIXED")))
                .andExpect(status().isOk());
        mvc.perform(asJson(u, patch("/api/v1/categories/" + u.cat("Rent")), Map.of("kind", "NEED")))
                .andExpect(status().isOk());
        mvc.perform(as(u, get("/api/v1/plans/current"))).andExpect(jsonPath("$.discretionaryPaise").value(
                (100_000 - 1_000 - 70_000) * RUPEE));
    }

    @Test
    void kindChangeIsCheckedAgainstTheRolledForwardPlan() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        Map<String, Long> limits = new LinkedHashMap<>();
        limits.put("Rent", 25_000 * RUPEE);
        limits.put("Groceries", 10_000 * RUPEE);
        // discretionary = 1,00,000 − 25,000 − 70,000 = 5,000: Groceries as FIXED would not fit
        mvc.perform(asJson(u, put("/api/v1/plans/current"), planBody(u, 100_000 * RUPEE, 70_000 * RUPEE, limits)))
                .andExpect(status().isOk());
        mvc.perform(asJson(u, patch("/api/v1/categories/" + u.cat("Groceries")), Map.of("archived", true)))
                .andExpect(status().isOk());
        // October has ended: the rolled-forward plan drops the archived category's limit, so the change fits
        today(LocalDate.of(2026, 11, 8));
        mvc.perform(asJson(u, patch("/api/v1/categories/" + u.cat("Groceries")), Map.of("kind", "FIXED")))
                .andExpect(status().isOk());
        mvc.perform(as(u, get("/api/v1/plans")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void dailyJobFindsUsersWhoseDayStartsAheadOfTheServer() throws Exception {
        // UTC+14: at 10:30Z on 11-06 it is already 11-07 there (still 11-06 in IST)
        User kiribati = users.save(new User("it-" + UUID.randomUUID() + "@test.local", "{noop}unused", "K",
                "Pacific/Kiritimati", 7));
        Category food = categories.save(new Category(kiribati.getId(), "Food", CategoryKind.WANT, List.of()));
        TestUser k = new TestUser(kiribati, jwtService.issue(kiribati).token(), Map.of("Food", food.getId()));
        TestUser ist = newUser(7);
        today(OCT_22);
        for (TestUser u : List.of(k, ist)) {
            mvc.perform(asJson(u, put("/api/v1/plans/current"), planBody(u, 100_000 * RUPEE, 0,
                    Map.of("Food", 1_000 * RUPEE)))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodEnd").value("2026-11-06"));
        }
        clock.set(Instant.parse("2026-11-06T10:30:00Z"));
        planService.rolloverAllDue();
        mvc.perform(as(k, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].periodStart").value("2026-11-07"));
        mvc.perform(as(ist, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(1)));
    }
}
