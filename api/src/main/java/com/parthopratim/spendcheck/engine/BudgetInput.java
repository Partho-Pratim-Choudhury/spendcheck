package com.parthopratim.spendcheck.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Everything the engine needs about one plan: amounts, limits, the period, "today" (user tz) and the period's
 * confirmed spends.
 */
public record BudgetInput(long incomePaise, long savingsTargetPaise, List<LimitInput> limits, Period period,
                          LocalDate today, List<SpendRow> spends) {

    public BudgetInput {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(today, "today");
        limits = List.copyOf(limits);
        spends = List.copyOf(spends);
    }
}
