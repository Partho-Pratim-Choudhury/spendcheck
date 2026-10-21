package com.parthopratim.spendcheck.engine;

import java.util.List;

/** Engine answer to "spend X in category c" (category numbers are before the spend). */
public record Verdict(
        VerdictLevel level,
        long amountPaise,
        long categoryId,
        String categoryName,
        long categoryLimitPaise,
        long categorySpentPaise,
        long categoryLeftPaise,
        long overflowPaise,
        long savingsHitPaise,
        String goalName,
        Long goalDelayDays,
        long todayBudgetPaise,
        long todaySpentPaise,
        long safePerDayBeforePaise,
        long safePerDayAfterPaise,
        List<String> reasons,
        String message) {

    public Verdict {
        reasons = List.copyOf(reasons);
    }
}
