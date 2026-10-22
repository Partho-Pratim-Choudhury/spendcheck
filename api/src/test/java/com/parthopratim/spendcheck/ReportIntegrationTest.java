package com.parthopratim.spendcheck;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);   // Thursday; day 16 of 31

    /**
     * Reference plan (discretionary ₹50,000) with: rent 25,000 (FIXED, untagged), Shopping 5,800 Mon 10-12 at
     * Myntra, Food 1,200 Thu 10-15 + 800 Thu 10-22 at Swiggy, Groceries 1,000 today at DMart, a cancelled Food
     * spend, and two spends either side of 00:00 IST on the period start.
     */
    private TestUser seeded() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        spend(u, 25_000 * RUPEE, "Rent", LocalDate.of(2026, 10, 8), null, null);
        spend(u, 5_800 * RUPEE, "Shopping", LocalDate.of(2026, 10, 12), "Myntra", "WANT");
        spend(u, 1_200 * RUPEE, "Food", LocalDate.of(2026, 10, 15), "Swiggy", null);
        spend(u, 800 * RUPEE, "Food", OCT_22, "swiggy", null);
        spend(u, 1_000 * RUPEE, "Groceries", OCT_22, "DMart", "NEED");
        UUID cancelled = spend(u, 999 * RUPEE, "Food", LocalDate.of(2026, 10, 20), null, null);
        mvc.perform(as(u, delete("/api/v1/transactions/" + cancelled))).andExpect(status().isNoContent());
        // 23:30 IST on 10-06 (previous period) and 00:30 IST on 10-07 (this one, ₹0 — kept tiny on purpose)
        spendAt(u, 50 * RUPEE, "Misc", Instant.parse("2026-10-06T18:00:00Z"), null, null);
        return u;
    }

    @Test
    void dashboardNumbers() throws Exception {
        TestUser u = seeded();
        mvc.perform(as(u, get("/api/v1/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.periodStart").value("2026-10-07"))
                .andExpect(jsonPath("$.plan.periodEnd").value("2026-11-06"))
                .andExpect(jsonPath("$.plan.daysTotal").value(31))
                .andExpect(jsonPath("$.plan.daysElapsed").value(16))
                .andExpect(jsonPath("$.plan.daysLeft").value(16))
                // variable spent 8,800; before today 7,000; today 1,800
                .andExpect(jsonPath("$.today.date").value("2026-10-22"))
                .andExpect(jsonPath("$.today.budgetPaise").value(268_750))      // 43,000 / 16
                .andExpect(jsonPath("$.today.spentPaise").value(180_000))
                .andExpect(jsonPath("$.today.leftPaise").value(88_750))
                .andExpect(jsonPath("$.month.discretionaryPaise").value(5_000_000))
                .andExpect(jsonPath("$.month.spentPaise").value(880_000))
                .andExpect(jsonPath("$.month.leftPaise").value(4_120_000))
                .andExpect(jsonPath("$.month.projectedPaise").value(1_705_000)) // 8,800 × 31 / 16
                .andExpect(jsonPath("$.month.savingsAtRiskPaise").value(0))
                .andExpect(jsonPath("$.categories[*].name", contains("Shopping", "Food", "Groceries")))
                .andExpect(jsonPath("$.categories[0].kind").value("WANT"))
                .andExpect(jsonPath("$.categories[0].limitPaise").value(700_000))
                .andExpect(jsonPath("$.categories[0].spentPaise").value(580_000))
                .andExpect(jsonPath("$.categories[0].leftPaise").value(120_000))
                .andExpect(jsonPath("$.categories[0].pct").value(82))
                .andExpect(jsonPath("$.categories[0].status").value("WARN"))
                .andExpect(jsonPath("$.categories[1].pct").value(25))
                .andExpect(jsonPath("$.categories[1].status").value("OK"))
                .andExpect(jsonPath("$.burn", hasSize(16)))
                .andExpect(jsonPath("$.burn[0].date").value("2026-10-07"))
                .andExpect(jsonPath("$.burn[0].cumulativePaise").value(0))
                .andExpect(jsonPath("$.burn[5].date").value("2026-10-12"))
                .andExpect(jsonPath("$.burn[5].cumulativePaise").value(580_000))
                .andExpect(jsonPath("$.burn[15].cumulativePaise").value(880_000))
                .andExpect(jsonPath("$.pausesSavedPaise").value(0))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.primaryGoal").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void spendJustAfterIstMidnightCountsInThePeriod() throws Exception {
        TestUser u = seeded();
        spendAt(u, 100 * RUPEE, "Misc", Instant.parse("2026-10-06T19:00:00Z"), null, null);
        mvc.perform(as(u, get("/api/v1/dashboard")))
                .andExpect(jsonPath("$.month.spentPaise").value(890_000))
                .andExpect(jsonPath("$.burn[0].cumulativePaise").value(10_000))
                // Misc has no limit row: limit 0, so it is OVER and sorts first
                .andExpect(jsonPath("$.categories[0].name").value("Misc"))
                .andExpect(jsonPath("$.categories[0].status").value("OVER"))
                .andExpect(jsonPath("$.categories[0].pct").value(100));
    }

    @Test
    void monthlyReport() throws Exception {
        TestUser u = seeded();
        long planId = readJson(mvc.perform(as(u, get("/api/v1/plans/current")))).get("id").asLong();
        mvc.perform(as(u, get("/api/v1/reports/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.id").value(planId))
                .andExpect(jsonPath("$.plan.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totals.incomePaise").value(12_000_000))
                .andExpect(jsonPath("$.totals.fixedPaise").value(5_000_000))
                .andExpect(jsonPath("$.totals.discretionaryPaise").value(5_000_000))
                .andExpect(jsonPath("$.totals.spentPaise").value(880_000))
                .andExpect(jsonPath("$.totals.savedPaise").value(2_000_000 + 4_120_000))
                .andExpect(jsonPath("$.totals.savingsTargetPaise").value(2_000_000))
                .andExpect(jsonPath("$.totals.underPlanPaise").value(4_120_000))
                .andExpect(jsonPath("$.byCategory[*].name", contains("Shopping", "Food", "Groceries")))
                .andExpect(jsonPath("$.byCategory[1].spentPaise").value(200_000))
                .andExpect(jsonPath("$.byCategory[1].limitPaise").value(800_000))
                .andExpect(jsonPath("$.needsPaise").value(100_000))
                .andExpect(jsonPath("$.wantsPaise").value(780_000))
                .andExpect(jsonPath("$.untaggedPaise").value(0))   // rent is FIXED: not in needs/wants/untagged
                .andExpect(jsonPath("$.topWants", hasSize(2)))
                .andExpect(jsonPath("$.topWants[0].label").value("Myntra"))
                .andExpect(jsonPath("$.topWants[0].count").value(1))
                .andExpect(jsonPath("$.topWants[0].totalPaise").value(580_000))
                .andExpect(jsonPath("$.topWants[1].label").value("swiggy"))   // "Swiggy" 1, "swiggy" 1: latest wins
                .andExpect(jsonPath("$.topWants[1].count").value(2))
                .andExpect(jsonPath("$.topWants[1].totalPaise").value(200_000))
                .andExpect(jsonPath("$.wantsByWeekday", contains(580_000, 0, 0, 200_000, 0, 0, 0)))
                .andExpect(jsonPath("$.pausesSavedPaise").value(0))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.trend", hasSize(1)))
                .andExpect(jsonPath("$.trend[0].planId").value(planId))
                .andExpect(jsonPath("$.trend[0].periodStart").value("2026-10-07"))
                .andExpect(jsonPath("$.trend[0].spentPaise").value(880_000))
                .andExpect(jsonPath("$.trend[0].discretionaryPaise").value(5_000_000));

        // next period: default is the latest plan, trend covers both, the old one is still addressable
        today(LocalDate.of(2026, 11, 8));
        spend(u, 300 * RUPEE, "Food", LocalDate.of(2026, 11, 8), null, null);
        mvc.perform(as(u, get("/api/v1/reports/monthly")))
                .andExpect(jsonPath("$.plan.periodStart").value("2026-11-07"))
                .andExpect(jsonPath("$.totals.spentPaise").value(30_000))
                .andExpect(jsonPath("$.trend[*].periodStart", contains("2026-10-07", "2026-11-07")))
                .andExpect(jsonPath("$.trend[*].spentPaise", contains(880_000, 30_000)));
        mvc.perform(as(u, get("/api/v1/reports/monthly?planId=" + planId)))
                .andExpect(jsonPath("$.plan.status").value("CLOSED"))
                .andExpect(jsonPath("$.totals.spentPaise").value(880_000))
                .andExpect(jsonPath("$.trend", hasSize(1)));
        mvc.perform(as(u, get("/api/v1/reports/monthly?planId=999999999"))).andExpect(status().isNotFound());
    }

    @Test
    void topWantLabelIsTheMostUsedSpellingAndFixedSpendsStayOutOfNeedsAndWants() throws Exception {
        TestUser u = newUser(7);
        today(OCT_22);
        putReferencePlan(u);
        spend(u, 100 * RUPEE, "Food", LocalDate.of(2026, 10, 10), "Swiggy", null);
        spend(u, 100 * RUPEE, "Food", LocalDate.of(2026, 10, 11), "SWIGGY", null);
        spend(u, 100 * RUPEE, "Food", LocalDate.of(2026, 10, 12), "Swiggy", null);
        spend(u, 100 * RUPEE, "Food", LocalDate.of(2026, 10, 20), "swiggy", null);
        spend(u, 200 * RUPEE, "Shopping", LocalDate.of(2026, 10, 13), "h&m", null);
        spend(u, 200 * RUPEE, "Shopping", LocalDate.of(2026, 10, 14), "H&M", null);
        spend(u, 1_000 * RUPEE, "EMI", LocalDate.of(2026, 10, 9), "Bank", "WANT");   // FIXED, even if tagged
        mvc.perform(as(u, get("/api/v1/reports/monthly")))
                .andExpect(jsonPath("$.totals.spentPaise").value(80_000))
                .andExpect(jsonPath("$.needsPaise").value(0))
                .andExpect(jsonPath("$.wantsPaise").value(80_000))
                .andExpect(jsonPath("$.untaggedPaise").value(0))
                .andExpect(jsonPath("$.topWants", hasSize(2)))
                .andExpect(jsonPath("$.topWants[0].label").value("Swiggy"))   // 2 of 4 spellings
                .andExpect(jsonPath("$.topWants[0].count").value(4))
                .andExpect(jsonPath("$.topWants[0].totalPaise").value(40_000))
                .andExpect(jsonPath("$.topWants[1].label").value("H&M"))      // 1:1, the later one
                .andExpect(jsonPath("$.topWants[1].count").value(2))
                .andExpect(jsonPath("$.wantsByWeekday", contains(10_000, 30_000, 20_000, 0, 0, 10_000, 10_000)));
    }

    @Test
    void dailySummaryToday() throws Exception {
        TestUser u = seeded();
        mvc.perform(as(u, get("/api/v1/summary/daily")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-22"))
                .andExpect(jsonPath("$.spentPaise").value(180_000))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.budgetPaise").value(268_750))
                .andExpect(jsonPath("$.overPaise").value(0))
                .andExpect(jsonPath("$.month.spentPaise").value(880_000))
                .andExpect(jsonPath("$.month.discretionaryPaise").value(5_000_000))
                .andExpect(jsonPath("$.month.projectedPaise").value(1_705_000))
                .andExpect(jsonPath("$.month.savingsAtRiskPaise").value(0))
                .andExpect(jsonPath("$.pausesSavedPaise").value(0))
                .andExpect(jsonPath("$.text").value("Today ₹1,800 across 2 spends — ₹888 under today's safe "
                        + "spend. Month: ₹8,800 of ₹50,000, projected ₹17,050."));
    }

    @Test
    void dailySummaryForAPastDateIgnoresLaterSpending() throws Exception {
        TestUser u = seeded();
        // as of 10-15: before = 5,800, that day 1,200, 23 days left -> (50,000 − 5,800) / 23 = 1,921.73
        mvc.perform(as(u, get("/api/v1/summary/daily?date=2026-10-15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-15"))
                .andExpect(jsonPath("$.spentPaise").value(120_000))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.budgetPaise").value(192_173))
                .andExpect(jsonPath("$.overPaise").value(0))
                .andExpect(jsonPath("$.month.spentPaise").value(700_000))
                .andExpect(jsonPath("$.month.projectedPaise").value(700_000L * 31 / 9))
                .andExpect(jsonPath("$.text").value("On 15 Oct 2026 ₹1,200 across 1 spend — ₹722 under the "
                        + "day's safe spend. Month: ₹7,000 of ₹50,000, projected ₹24,111."));
        mvc.perform(as(u, get("/api/v1/summary/daily?date=2026-10-12")))
                .andExpect(jsonPath("$.overPaise").value(580_000 - 5_000_000 / 26));
        mvc.perform(as(u, get("/api/v1/summary/daily?date=2026-09-01"))).andExpect(status().isNotFound());
    }
}
