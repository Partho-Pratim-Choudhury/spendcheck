package com.parthopratim.spendcheck.engine;

import com.parthopratim.spendcheck.engine.CategoryStatus.Status;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The budget rules of ENGINE.md. Pure functions over immutable inputs; all amounts are paise and every division
 * floors. Only NEED and WANT categories are variable: FIXED spends never touch discretionary numbers.
 */
public final class BudgetEngine {

    public static final String REASON_OVER_CATEGORY = "over category limit";
    public static final String REASON_OVER_TODAY = "over today's safe spend";
    public static final String REASON_OVER_HALF = "uses more than half of what's left in category";

    /** 100 years: beyond this an on-track date is meaningless (and far enough out would overflow LocalDate). */
    public static final long ON_TRACK_HORIZON_MONTHS = 1200;

    private static final Comparator<GoalInput> PRIMARY_GOAL_ORDER = Comparator
            .comparing(GoalInput::targetDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(GoalInput::id);

    private BudgetEngine() {
    }

    // ---------- month numbers ----------

    public static MonthNumbers month(BudgetInput in) {
        long fixedTotal = 0;
        long variableLimits = 0;
        for (LimitInput l : in.limits()) {
            if (l.kind() == CategoryKind.FIXED) {
                fixedTotal += l.limitPaise();
            } else {
                variableLimits += l.limitPaise();
            }
        }
        long discretionary = in.incomePaise() - fixedTotal - in.savingsTargetPaise();

        LocalDate today = in.today();
        long spent = 0;
        long spentBeforeToday = 0;
        long spentToday = 0;
        for (SpendRow s : in.spends()) {
            if (!s.variable()) {
                continue;
            }
            spent += s.amountPaise();
            if (s.date().isBefore(today)) {
                spentBeforeToday += s.amountPaise();
            } else if (s.date().isEqual(today)) {
                spentToday += s.amountPaise();
            }
        }

        Period p = in.period();
        int daysTotal = PeriodCalculator.daysTotal(p);
        int daysElapsed = PeriodCalculator.daysElapsed(p, today);
        int daysLeft = PeriodCalculator.daysLeft(p, today);

        long left = discretionary - spent;
        long todayBudget = Math.max(0, Math.floorDiv(discretionary - spentBeforeToday, daysLeft));
        long projected = Math.floorDiv(spent * daysTotal, daysElapsed);
        return new MonthNumbers(
                fixedTotal,
                variableLimits,
                discretionary,
                discretionary - variableLimits,
                Math.floorDiv(discretionary, daysTotal),
                spent,
                spentBeforeToday,
                spentToday,
                left,
                todayBudget,
                todayBudget - spentToday,
                projected,
                Math.max(0, projected - discretionary),
                daysTotal,
                daysElapsed,
                daysLeft);
    }

    // ---------- categories ----------

    /** {@code spent × 100 / limit}; a zero limit is 100 when anything was spent, else 0. */
    public static long pct(long spentPaise, long limitPaise) {
        if (limitPaise <= 0) {
            return spentPaise > 0 ? 100 : 0;
        }
        return Math.floorDiv(spentPaise * 100, limitPaise);
    }

    public static Status status(long spentPaise, long limitPaise) {
        if (spentPaise > limitPaise) {
            return Status.OVER;
        }
        return pct(spentPaise, limitPaise) >= 75 ? Status.WARN : Status.OK;
    }

    public static CategoryStatus categoryStatus(long categoryId, CategoryKind kind, long limitPaise,
                                                long spentPaise) {
        return new CategoryStatus(categoryId, kind, limitPaise, spentPaise, limitPaise - spentPaise,
                pct(spentPaise, limitPaise), status(spentPaise, limitPaise));
    }

    /**
     * Every NEED/WANT category that has a limit row or spends in the period (no limit row = limit 0), sorted by
     * status (OVER, WARN, OK), then pct descending, then category id.
     */
    public static List<CategoryStatus> categoryStatuses(BudgetInput in) {
        Map<Long, CategoryKind> kinds = new LinkedHashMap<>();
        Map<Long, Long> limits = new LinkedHashMap<>();
        Map<Long, Long> spent = new LinkedHashMap<>();
        for (LimitInput l : in.limits()) {
            if (l.kind() != CategoryKind.FIXED) {
                kinds.putIfAbsent(l.categoryId(), l.kind());
                limits.merge(l.categoryId(), l.limitPaise(), Long::sum);
            }
        }
        for (SpendRow s : in.spends()) {
            if (s.variable()) {
                kinds.putIfAbsent(s.categoryId(), s.kind());
                spent.merge(s.categoryId(), s.amountPaise(), Long::sum);
            }
        }
        List<CategoryStatus> out = new ArrayList<>();
        kinds.forEach((id, kind) -> out.add(
                categoryStatus(id, kind, limits.getOrDefault(id, 0L), spent.getOrDefault(id, 0L))));
        out.sort(Comparator.comparing(CategoryStatus::status)
                .thenComparing(Comparator.comparingLong(CategoryStatus::pct).reversed())
                .thenComparingLong(CategoryStatus::categoryId));
        return out;
    }

    public static long limitFor(BudgetInput in, long categoryId) {
        return in.limits().stream().filter(l -> l.categoryId() == categoryId).mapToLong(LimitInput::limitPaise).sum();
    }

    public static long spentIn(BudgetInput in, long categoryId) {
        return in.spends().stream().filter(s -> s.categoryId() == categoryId).mapToLong(SpendRow::amountPaise).sum();
    }

    /** Cumulative variable spend for every day from period start through {@code min(today, end)}. */
    public static List<BurnPoint> burn(BudgetInput in) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (SpendRow s : in.spends()) {
            if (s.variable()) {
                byDay.merge(s.date(), s.amountPaise(), Long::sum);
            }
        }
        Period p = in.period();
        LocalDate last = in.today().isBefore(p.end()) ? in.today() : p.end();
        List<BurnPoint> out = new ArrayList<>();
        long cumulative = 0;
        for (LocalDate d = p.start(); !d.isAfter(last); d = d.plusDays(1)) {
            cumulative += byDay.getOrDefault(d, 0L);
            out.add(new BurnPoint(d, cumulative));
        }
        return out;
    }

    // ---------- verdict ----------

    /**
     * Verdict for spending {@code amountPaise} in a category. A FIXED category is checked against its own limit
     * only: it never counts against today's budget or the safe-per-day numbers.
     */
    public static Verdict verdict(BudgetInput in, long categoryId, CategoryKind kind, String categoryName,
                                  long amountPaise, Optional<GoalInput> primaryGoal) {
        MonthNumbers m = month(in);
        long limit = limitFor(in, categoryId);
        long catSpent = spentIn(in, categoryId);
        long catLeft = limit - catSpent;
        long overflow = Math.max(0, amountPaise - Math.max(0, catLeft));
        boolean variable = kind != CategoryKind.FIXED;
        long discretionaryHit = variable ? amountPaise : 0;

        long base = m.leftPaise() + m.spentTodayPaise();
        long safeBefore = Math.max(0, Math.floorDiv(base, m.daysLeft()));
        long safeAfter = Math.max(0, Math.floorDiv(base - discretionaryHit, m.daysLeft()));

        boolean overToday = variable && m.spentTodayPaise() + amountPaise > m.todayBudgetPaise();
        boolean overHalf = amountPaise * 2 > catLeft;
        List<String> reasons = new ArrayList<>();
        if (overflow > 0) {
            reasons.add(REASON_OVER_CATEGORY);
        }
        if (overToday) {
            reasons.add(REASON_OVER_TODAY);
        }
        if (overHalf) {
            reasons.add(REASON_OVER_HALF);
        }
        VerdictLevel level = overflow > 0 ? VerdictLevel.RED
                : (overToday || overHalf) ? VerdictLevel.AMBER
                : VerdictLevel.GREEN;

        String goalName = null;
        Long goalDelayDays = null;
        if (primaryGoal.isPresent()) {
            Long delay = goalDelayDays(overflow, primaryGoal.get().monthlyContributionPaise());
            if (delay != null) {
                goalName = primaryGoal.get().name();
                goalDelayDays = delay;
            }
        }

        String message = switch (level) {
            case GREEN -> "Fits. " + categoryName + " left after: " + Inr.formatDown(catLeft - amountPaise) + ".";
            case AMBER -> "Careful. " + capitalise(reasons.get(0)) + ". " + categoryName + " left after: "
                    + Inr.formatDown(Math.max(0, catLeft - amountPaise)) + ".";
            case RED -> "Over plan. " + Inr.formatUp(overflow) + " more than " + categoryName
                    + " has left — it comes out of savings."
                    + (goalDelayDays != null ? " " + goalName + " delayed ~" + goalDelayDays + " days." : "");
        };

        return new Verdict(level, amountPaise, categoryId, categoryName, limit, catSpent, catLeft, overflow,
                overflow, goalName, goalDelayDays, m.todayBudgetPaise(), m.spentTodayPaise(), safeBefore, safeAfter,
                reasons, message);
    }

    /** {@code ceil(overflow × 30 / monthlyContribution)} when both are positive, else null. */
    public static Long goalDelayDays(long overflowPaise, long monthlyContributionPaise) {
        if (overflowPaise <= 0 || monthlyContributionPaise <= 0) {
            return null;
        }
        return Math.ceilDiv(overflowPaise * 30, monthlyContributionPaise);
    }

    // ---------- goals ----------

    /** The primary goal comes first: earliest target date (nulls last), then lowest id. */
    public static Comparator<GoalInput> primaryGoalOrder() {
        return PRIMARY_GOAL_ORDER;
    }

    /** {@code saved × 100 / target}, floored and capped at 100. */
    public static int progressPct(long savedPaise, long targetPaise) {
        if (targetPaise <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.floorDiv(Math.max(0, savedPaise) * 100, targetPaise));
    }

    /**
     * Projected completion at the current monthly contribution: today when already reached, null when nothing is
     * being contributed or the date would be more than {@value #ON_TRACK_HORIZON_MONTHS} months away, else today
     * plus {@code ceil(remaining / contribution)} months.
     */
    public static LocalDate onTrackDate(long targetPaise, long savedPaise, long monthlyContributionPaise,
                                        LocalDate today) {
        long remaining = targetPaise - savedPaise;
        if (remaining <= 0) {
            return today;
        }
        if (monthlyContributionPaise <= 0) {
            return null;
        }
        long months = Math.ceilDiv(remaining, monthlyContributionPaise);
        return months > ON_TRACK_HORIZON_MONTHS ? null : today.plusMonths(months);
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
