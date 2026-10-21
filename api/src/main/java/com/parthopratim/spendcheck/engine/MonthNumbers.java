package com.parthopratim.spendcheck.engine;

/** The period-level numbers of ENGINE.md "Month numbers", plus day counts. All paise. */
public record MonthNumbers(
        long fixedTotalPaise,
        long variableLimitsPaise,
        long discretionaryPaise,
        long unallocatedPaise,
        long dailyBudgetPaise,
        long spentPaise,
        long spentBeforeTodayPaise,
        long spentTodayPaise,
        long leftPaise,
        long todayBudgetPaise,
        long todayLeftPaise,
        long projectedPaise,
        long savingsAtRiskPaise,
        int daysTotal,
        int daysElapsed,
        int daysLeft) {}
