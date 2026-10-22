package com.parthopratim.spendcheck.engine;

import static com.parthopratim.spendcheck.ledger.CategoryKind.FIXED;
import static com.parthopratim.spendcheck.ledger.CategoryKind.NEED;
import static com.parthopratim.spendcheck.ledger.CategoryKind.WANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.parthopratim.spendcheck.engine.CategoryStatus.Status;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BudgetEngineTest {

    private static final long RUPEE = 100;

    // ids for the reference scenario
    private static final long RENT = 1, EMI = 2, SIP = 3, SHOPPING = 4, FOOD = 5, GROCERIES = 6, MISC = 7;

    private static final Period OCT = new Period(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 11, 6));
    private static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22); // day 16 of 31, 16 days left

    private static final GoalInput TRIP = new GoalInput(1, "Goa trip", 10_000 * RUPEE, LocalDate.of(2027, 3, 1));

    /** Income 1,20,000; rent 25,000 + EMI 15,000 + SIP 10,000; savings 20,000 -> discretionary 50,000. */
    private static List<LimitInput> referenceLimits() {
        return List.of(
                new LimitInput(RENT, FIXED, 25_000 * RUPEE),
                new LimitInput(EMI, FIXED, 15_000 * RUPEE),
                new LimitInput(SIP, FIXED, 10_000 * RUPEE),
                new LimitInput(SHOPPING, WANT, 7_000 * RUPEE),
                new LimitInput(FOOD, WANT, 8_000 * RUPEE),
                new LimitInput(GROCERIES, NEED, 10_000 * RUPEE));
    }

    private static BudgetInput reference(LocalDate today, List<SpendRow> spends) {
        return new BudgetInput(120_000 * RUPEE, 20_000 * RUPEE, referenceLimits(), OCT, today, spends);
    }

    private static SpendRow spend(LocalDate date, long categoryId, CategoryKind kind, long paise) {
        return new SpendRow(date, categoryId, kind, paise);
    }

    // ---------- reference scenario ----------

    @Test
    void referenceScenarioShoppingCheckIsRedWithGoalDelay() {
        BudgetInput in = reference(OCT_22, List.of(
                spend(LocalDate.of(2026, 10, 8), RENT, FIXED, 25_000 * RUPEE),
                spend(LocalDate.of(2026, 10, 12), SHOPPING, WANT, 5_800 * RUPEE)));

        MonthNumbers m = BudgetEngine.month(in);
        assertThat(m.discretionaryPaise()).isEqualTo(50_000 * RUPEE);
        assertThat(m.fixedTotalPaise()).isEqualTo(50_000 * RUPEE);

        Verdict v = BudgetEngine.verdict(in, SHOPPING, WANT, "Shopping", 3_500 * RUPEE, Optional.of(TRIP));
        assertThat(v.level()).isEqualTo(VerdictLevel.RED);
        assertThat(v.categoryLimitPaise()).isEqualTo(7_000 * RUPEE);
        assertThat(v.categorySpentPaise()).isEqualTo(5_800 * RUPEE);
        assertThat(v.categoryLeftPaise()).isEqualTo(1_200 * RUPEE);
        assertThat(v.overflowPaise()).isEqualTo(2_300 * RUPEE);
        assertThat(v.savingsHitPaise()).isEqualTo(2_300 * RUPEE);
        assertThat(v.goalName()).isEqualTo("Goa trip");
        assertThat(v.goalDelayDays()).isEqualTo(7L);
        // 3,500 also exceeds today's safe spend of 2,762.50
        assertThat(v.reasons()).containsExactly(BudgetEngine.REASON_OVER_CATEGORY, BudgetEngine.REASON_OVER_TODAY,
                BudgetEngine.REASON_OVER_HALF);
        assertThat(v.message()).isEqualTo(
                "Over plan. ₹2,300 more than Shopping has left — it comes out of savings. Goa trip delayed ~7 days.");
        // (50,000 − 5,800) / 16 = 2,762.50 ; after: (44,200 − 3,500) / 16 = 2,543.75
        assertThat(v.todayBudgetPaise()).isEqualTo(276_250);
        assertThat(v.safePerDayBeforePaise()).isEqualTo(276_250);
        assertThat(v.safePerDayAfterPaise()).isEqualTo(254_375);
        assertThat(v.todaySpentPaise()).isZero();
    }

    // ---------- month numbers ----------

    @Test
    void monthNumbers() {
        BudgetInput in = reference(OCT_22, List.of(
                spend(LocalDate.of(2026, 10, 8), RENT, FIXED, 25_000 * RUPEE),      // FIXED: ignored
                spend(LocalDate.of(2026, 10, 10), SHOPPING, WANT, 5_800 * RUPEE),
                spend(LocalDate.of(2026, 10, 15), FOOD, WANT, 12_200 * RUPEE),
                spend(OCT_22, GROCERIES, NEED, 500 * RUPEE),
                spend(OCT_22, RENT, FIXED, 100 * RUPEE)));                           // FIXED today: ignored
        MonthNumbers m = BudgetEngine.month(in);
        assertThat(m.fixedTotalPaise()).isEqualTo(50_000 * RUPEE);
        assertThat(m.variableLimitsPaise()).isEqualTo(25_000 * RUPEE);
        assertThat(m.discretionaryPaise()).isEqualTo(50_000 * RUPEE);
        assertThat(m.unallocatedPaise()).isEqualTo(25_000 * RUPEE);
        assertThat(m.dailyBudgetPaise()).isEqualTo(5_000_000 / 31);           // 161290 (floor)
        assertThat(m.spentPaise()).isEqualTo(18_500 * RUPEE);
        assertThat(m.spentBeforeTodayPaise()).isEqualTo(18_000 * RUPEE);
        assertThat(m.spentTodayPaise()).isEqualTo(500 * RUPEE);
        assertThat(m.leftPaise()).isEqualTo(31_500 * RUPEE);
        assertThat(m.todayBudgetPaise()).isEqualTo(2_000 * RUPEE);            // 32,000 / 16
        assertThat(m.todayLeftPaise()).isEqualTo(1_500 * RUPEE);
        assertThat(m.projectedPaise()).isEqualTo(1_850_000L * 31 / 16);        // 35,843.75
        assertThat(m.savingsAtRiskPaise()).isZero();
        assertThat(m.daysTotal()).isEqualTo(31);
        assertThat(m.daysElapsed()).isEqualTo(16);
        assertThat(m.daysLeft()).isEqualTo(16);
    }

    @ParameterizedTest(name = "before={0} today={1} on {2}: budget {3}, todayLeft {4}, left {5}, projected {6}, risk {7}")
    @CsvSource({
            // spentBefore, spentToday, today,     todayBudget, todayLeft, left,     projected, atRisk
            "0,            0,          2026-10-07, 161290,      161290,    5000000,  0,         0",       // day 1
            "0,            300000,     2026-10-07, 161290,      -138710,   4700000,  9300000,   4300000", // day 1 blow-out
            "4000000,      0,          2026-10-22, 62500,       62500,     1000000,  7750000,   2750000",
            "5000000,      0,          2026-10-22, 0,           0,         0,        9687500,   4687500", // exactly all gone
            "6000000,      10000,      2026-10-22, 0,           -10000,    -1010000, 11644375,  6644375", // negative left
            "4900000,      0,          2026-11-06, 100000,      100000,    100000,   4900000,   0",       // last day: daysLeft 1
            "4900000,      100001,     2026-11-06, 100000,      -1,        -1,       5000001,   1",       // 1 paise over
    })
    void monthBoundaries(long before, long todaySpent, LocalDate today, long todayBudget, long todayLeft, long left,
                         long projected, long atRisk) {
        List<SpendRow> spends = new ArrayList<>();
        if (before > 0) {
            spends.add(spend(OCT.start(), FOOD, WANT, before));
        }
        if (todaySpent > 0) {
            spends.add(spend(today, SHOPPING, WANT, todaySpent));
        }
        MonthNumbers m = BudgetEngine.month(reference(today, spends));
        assertThat(m.todayBudgetPaise()).isEqualTo(todayBudget);
        assertThat(m.todayLeftPaise()).isEqualTo(todayLeft);
        assertThat(m.leftPaise()).isEqualTo(left);
        assertThat(m.projectedPaise()).isEqualTo(projected);
        assertThat(m.savingsAtRiskPaise()).isEqualTo(atRisk);
    }

    @Test
    void todayAfterPeriodEndUsesDaysLeftOfOne() {
        MonthNumbers m = BudgetEngine.month(reference(LocalDate.of(2026, 11, 20),
                List.of(spend(OCT.start(), FOOD, WANT, 4_000_000))));
        assertThat(m.daysLeft()).isEqualTo(1);
        assertThat(m.daysElapsed()).isEqualTo(31);
        assertThat(m.todayBudgetPaise()).isEqualTo(1_000_000);
        assertThat(m.projectedPaise()).isEqualTo(4_000_000);
    }

    @Test
    void negativeDiscretionaryFloorsTowardsMinusInfinity() {
        BudgetInput in = new BudgetInput(1000, 0, List.of(new LimitInput(RENT, FIXED, 1031)), OCT, OCT.start(),
                List.of());
        MonthNumbers m = BudgetEngine.month(in);
        assertThat(m.discretionaryPaise()).isEqualTo(-31);
        assertThat(m.dailyBudgetPaise()).isEqualTo(-1);
        assertThat(m.todayBudgetPaise()).isZero();
    }

    // ---------- category status ----------

    @ParameterizedTest(name = "spent {0} of {1} -> {2}% {3}")
    @CsvSource({
            "0,     0,     0,   OK",      // no limit, nothing spent
            "1,     0,     100, OVER",    // no limit, anything spent
            "0,     1000,  0,   OK",
            "749,   1000,  74,  OK",
            "7499,  10000, 74,  OK",      // 74.99 floors to 74
            "750,   1000,  75,  WARN",    // pct boundary
            "999,   1000,  99,  WARN",
            "1000,  1000,  100, WARN",    // exactly at limit is not over
            "1001,  1000,  100, OVER",    // 1 paise over
            "2500,  1000,  250, OVER",
    })
    void pctAndStatus(long spent, long limit, long pct, Status status) {
        assertThat(BudgetEngine.pct(spent, limit)).isEqualTo(pct);
        assertThat(BudgetEngine.status(spent, limit)).isEqualTo(status);
        CategoryStatus c = BudgetEngine.categoryStatus(9, WANT, limit, spent);
        assertThat(c.leftPaise()).isEqualTo(limit - spent);
    }

    @Test
    void categoryStatusesCoverVariableCategoriesOnlyAndSortByStatusThenPct() {
        BudgetInput in = reference(OCT_22, List.of(
                spend(OCT_22, RENT, FIXED, 25_000 * RUPEE),
                spend(OCT_22, SHOPPING, WANT, 5_800 * RUPEE),     // 82% WARN
                spend(OCT_22, FOOD, WANT, 7_000 * RUPEE),         // 87% WARN
                spend(OCT_22, GROCERIES, NEED, 1_000 * RUPEE),    // 10% OK
                spend(OCT_22, MISC, WANT, 100)));                 // no limit row -> limit 0 -> OVER
        List<CategoryStatus> out = BudgetEngine.categoryStatuses(in);
        assertThat(out).extracting(CategoryStatus::categoryId).containsExactly(MISC, FOOD, SHOPPING, GROCERIES);
        assertThat(out.get(0)).isEqualTo(new CategoryStatus(MISC, WANT, 0, 100, -100, 100, Status.OVER));
        assertThat(out.get(1).pct()).isEqualTo(87);
        assertThat(out.get(3).status()).isEqualTo(Status.OK);
    }

    @Test
    void categoryWithLimitButNoSpendIsListedAsOk() {
        List<CategoryStatus> out = BudgetEngine.categoryStatuses(reference(OCT_22, List.of()));
        assertThat(out).extracting(CategoryStatus::categoryId).containsExactly(SHOPPING, FOOD, GROCERIES);
        assertThat(out).allMatch(c -> c.status() == Status.OK && c.pct() == 0);
    }

    // ---------- verdict boundaries ----------

    /**
     * 30-day period, today is day 21 (10 days left), discretionary ₹1,000 (100000 paise). Category FOOD (WANT)
     * limit/spent set per case, spent earlier in the period; optional spend today in GROCERIES.
     */
    private static final Period P30 = new Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30));
    private static final LocalDate DAY21 = LocalDate.of(2026, 10, 21);

    private static BudgetInput small(Long foodLimit, long foodSpent, long spentToday) {
        List<LimitInput> limits = new ArrayList<>();
        limits.add(new LimitInput(GROCERIES, NEED, 1_000_000));
        if (foodLimit != null) {
            limits.add(new LimitInput(FOOD, WANT, foodLimit));
        }
        List<SpendRow> spends = new ArrayList<>();
        if (foodSpent > 0) {
            spends.add(spend(LocalDate.of(2026, 10, 5), FOOD, WANT, foodSpent));
        }
        if (spentToday > 0) {
            spends.add(spend(DAY21, GROCERIES, NEED, spentToday));
        }
        return new BudgetInput(100_000, 0, limits, P30, DAY21, spends);
    }

    @ParameterizedTest(name = "[{index}] limit {0} spent {1} today {2} amount {3} -> {4}")
    @CsvSource(nullValues = "null", delimiter = '|', value = {
            // limit | spent | today | amount | level | overflow | reasons (;-separated)                 | message
            "10000 | 6000  | 0     | 2000   | GREEN | 0        |                                          | Fits. Food left after: ₹20.",
            "10000 | 6000  | 0     | 2001   | AMBER | 0        | half                                     | Careful. Uses more than half of what's left in category. Food left after: ₹19.",
            "10000 | 6000  | 0     | 4000   | AMBER | 0        | half                                     | Careful. Uses more than half of what's left in category. Food left after: ₹0.",
            "10000 | 6000  | 0     | 4001   | RED   | 1        | category;half                            | Over plan. ₹1 more than Food has left — it comes out of savings.",
            "10000 | 10000 | 0     | 1      | RED   | 1        | category;half                            | Over plan. ₹1 more than Food has left — it comes out of savings.",
            "1000  | 1500  | 0     | 100    | RED   | 100      | category;half                            | Over plan. ₹1 more than Food has left — it comes out of savings.",
            "null  | 0     | 0     | 100    | RED   | 100      | category;half                            | Over plan. ₹1 more than Food has left — it comes out of savings.",
            // today budget = (100000 − 6000) / 10 = 9400
            "100000| 6000  | 0     | 9400   | GREEN | 0        |                                          | Fits. Food left after: ₹846.",
            "100000| 6000  | 0     | 9401   | AMBER | 0        | today                                    | Careful. Over today's safe spend. Food left after: ₹845.",
            // today budget = (100000 − 6000) / 10 = 9400 with 9000 already spent today
            "100000| 6000  | 9000  | 400    | GREEN | 0        |                                          | Fits. Food left after: ₹936.",
            "100000| 6000  | 9000  | 401    | AMBER | 0        | today                                    | Careful. Over today's safe spend. Food left after: ₹935.",
            "50000 | 30000 | 9000  | 10001  | AMBER | 0        | today;half                               | Careful. Over today's safe spend. Food left after: ₹99.",
            "50000 | 30000 | 9000  | 20001  | RED   | 1        | category;today;half                      | Over plan. ₹1 more than Food has left — it comes out of savings.",
    })
    void verdictBoundaries(Long limit, long spent, long today, long amount, VerdictLevel level, long overflow,
                           String reasons, String message) {
        Verdict v = BudgetEngine.verdict(small(limit, spent, today), FOOD, WANT, "Food", amount, Optional.empty());
        assertThat(v.level()).isEqualTo(level);
        assertThat(v.overflowPaise()).isEqualTo(overflow);
        assertThat(v.savingsHitPaise()).isEqualTo(overflow);
        assertThat(v.categoryLimitPaise()).isEqualTo(limit == null ? 0 : limit);
        assertThat(v.categoryLeftPaise()).isEqualTo((limit == null ? 0 : limit) - spent);
        assertThat(v.reasons()).containsExactlyElementsOf(expandReasons(reasons));
        assertThat(v.message()).isEqualTo(message);
        assertThat(v.goalName()).isNull();
        assertThat(v.goalDelayDays()).isNull();
    }

    private static List<String> expandReasons(String codes) {
        List<String> out = new ArrayList<>();
        if (codes == null || codes.isBlank()) {
            return out;
        }
        for (String c : codes.split(";")) {
            out.add(switch (c.trim()) {
                case "category" -> BudgetEngine.REASON_OVER_CATEGORY;
                case "today" -> BudgetEngine.REASON_OVER_TODAY;
                case "half" -> BudgetEngine.REASON_OVER_HALF;
                default -> throw new IllegalArgumentException(c);
            });
        }
        return out;
    }

    @Test
    void safePerDayBeforeAndAfter() {
        // left = 100000 − 6000 − 9000 = 85000; + spentToday 9000 = 94000 over 10 days
        Verdict v = BudgetEngine.verdict(small(100_000L, 6000, 9000), FOOD, WANT, "Food", 4005, Optional.empty());
        assertThat(v.todayBudgetPaise()).isEqualTo(9400);
        assertThat(v.todaySpentPaise()).isEqualTo(9000);
        assertThat(v.safePerDayBeforePaise()).isEqualTo(9400);
        assertThat(v.safePerDayAfterPaise()).isEqualTo((94000 - 4005) / 10);   // 8999 (floor)
    }

    @Test
    void safePerDayAfterIsClampedAtZero() {
        Verdict v = BudgetEngine.verdict(small(10_000_000L, 90_000, 0), FOOD, WANT, "Food", 50_000, Optional.empty());
        assertThat(v.safePerDayBeforePaise()).isEqualTo(1000);
        assertThat(v.safePerDayAfterPaise()).isZero();
    }

    @Test
    void fixedCategoryCheckDoesNotTouchDiscretionaryNumbers() {
        BudgetInput in = reference(OCT_22, List.of());
        Verdict v = BudgetEngine.verdict(in, RENT, FIXED, "Rent", 10_000 * RUPEE, Optional.of(TRIP));
        assertThat(v.level()).isEqualTo(VerdictLevel.GREEN);  // 10,000 of 25,000 left, and no today-budget test
        assertThat(v.safePerDayAfterPaise()).isEqualTo(v.safePerDayBeforePaise());
        assertThat(v.message()).isEqualTo("Fits. Rent left after: ₹15,000.");
    }

    // ---------- goal delay & primary goal ----------

    @ParameterizedTest(name = "overflow {0}, contribution {1} -> {2}")
    @CsvSource(nullValues = "null", value = {
            "230000,  1000000, 7",       // reference: ceil(2300 × 30 / 10000) = ceil(6.9)
            "1000000, 1000000, 30",
            "1,       30,      1",
            "1,       1000000, 1",       // tiny overflow still rounds up to a day
            "333,     1000,    10",      // 9.99 -> 10
            "0,       1000000, null",    // no overflow
            "230000,  0,       null",    // zero-contribution goal
    })
    void goalDelayDays(long overflow, long contribution, Long expected) {
        assertThat(BudgetEngine.goalDelayDays(overflow, contribution)).isEqualTo(expected);
    }

    @Test
    void redWithZeroContributionPrimaryGoalHasNoDelay() {
        GoalInput idle = new GoalInput(3, "Emergency fund", 0, LocalDate.of(2026, 12, 1));
        Verdict v = BudgetEngine.verdict(small(1000L, 1000, 0), FOOD, WANT, "Food", 500, Optional.of(idle));
        assertThat(v.level()).isEqualTo(VerdictLevel.RED);
        assertThat(v.goalName()).isNull();
        assertThat(v.goalDelayDays()).isNull();
        assertThat(v.message()).isEqualTo("Over plan. ₹5 more than Food has left — it comes out of savings.");
    }

    @Test
    void nonRedVerdictHasNoGoalDelay() {
        Verdict v = BudgetEngine.verdict(small(10_000L, 0, 0), FOOD, WANT, "Food", 100, Optional.of(TRIP));
        assertThat(v.level()).isEqualTo(VerdictLevel.GREEN);
        assertThat(v.goalDelayDays()).isNull();
        assertThat(v.goalName()).isNull();
    }

    @Test
    void primaryGoalIsEarliestTargetDateNullsLastThenLowestId() {
        GoalInput a = new GoalInput(5, "A", 1, LocalDate.of(2027, 1, 1));
        GoalInput b = new GoalInput(4, "B", 1, LocalDate.of(2026, 12, 1));
        GoalInput c = new GoalInput(1, "C", 1, null);
        GoalInput d = new GoalInput(2, "D", 1, LocalDate.of(2026, 12, 1));
        GoalInput e = new GoalInput(3, "E", 1, null);
        assertThat(primary(a, b, c, d, e)).contains(d);   // same date: lower id
        assertThat(primary(a, c, e)).contains(a);         // dated beats undated
        assertThat(primary(e, c)).contains(c);            // all undated: lowest id
        assertThat(primary()).isEmpty();
    }

    private static Optional<GoalInput> primary(GoalInput... goals) {
        return java.util.Arrays.stream(goals).min(BudgetEngine.primaryGoalOrder());
    }

    @ParameterizedTest(name = "saved {1} of {0} at {2}/month -> {3}% done {4}")
    @CsvSource(nullValues = "null", value = {
            "100000, 0,      10000, 0,   2027-08-22",   // 10 months
            "100000, 25000,  10000, 25,  2027-06-22",   // 7.5 -> 8 months
            "100000, 99999,  10000, 99,  2026-11-22",
            "100000, 100000, 0,     100, 2026-10-22",   // reached: today
            "100000, 150000, 0,     100, 2026-10-22",   // capped at 100
            "100000, 0,      0,     0,   null",         // no contribution: no projection
            "100000, 0,      83,    0,   null",         // 1,205 months: beyond the 100-year horizon
            "100000, 0,      84,    0,   2126-01-22",   // 1,191 months: still projected
            "9223372036854775807, 0, 1, 0, null",       // would overflow LocalDate: no projection, no exception
    })
    void goalProgressAndOnTrackDate(long target, long saved, long contribution, int pct, LocalDate onTrack) {
        assertThat(BudgetEngine.progressPct(saved, target)).isEqualTo(pct);
        assertThat(BudgetEngine.onTrackDate(target, saved, contribution, OCT_22)).isEqualTo(onTrack);
    }

    // ---------- burn ----------

    @Nested
    class Burn {

        @Test
        void cumulativeVariableSpendEveryDayThroughToday() {
            BudgetInput in = reference(LocalDate.of(2026, 10, 10), List.of(
                    spend(LocalDate.of(2026, 10, 7), FOOD, WANT, 100),
                    spend(LocalDate.of(2026, 10, 7), RENT, FIXED, 999_999),
                    spend(LocalDate.of(2026, 10, 9), SHOPPING, WANT, 250),
                    spend(LocalDate.of(2026, 10, 9), GROCERIES, NEED, 50)));
            assertThat(BudgetEngine.burn(in)).containsExactly(
                    new BurnPoint(LocalDate.of(2026, 10, 7), 100),
                    new BurnPoint(LocalDate.of(2026, 10, 8), 100),
                    new BurnPoint(LocalDate.of(2026, 10, 9), 400),
                    new BurnPoint(LocalDate.of(2026, 10, 10), 400));
        }

        @Test
        void stopsAtPeriodEndWhenTodayIsLater() {
            assertThat(BudgetEngine.burn(reference(LocalDate.of(2026, 12, 1), List.of()))).hasSize(31);
        }
    }
}
