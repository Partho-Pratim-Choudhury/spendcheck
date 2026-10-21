package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.engine.BudgetInput;
import com.parthopratim.spendcheck.engine.LimitInput;
import com.parthopratim.spendcheck.engine.SpendRow;
import com.parthopratim.spendcheck.ledger.SpendAggregate;
import com.parthopratim.spendcheck.ledger.SpendAggregateRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds engine inputs from a plan and its period's SQL-aggregated spending. */
@Component
public class BudgetInputs {

    private final SpendAggregateRepository aggregates;

    public BudgetInputs(SpendAggregateRepository aggregates) {
        this.aggregates = aggregates;
    }

    /** Confirmed spending dated within the plan's period. */
    public List<SpendAggregate> spending(BudgetPlan plan, ZoneId zone) {
        return aggregates.between(plan.getUserId(), plan.getPeriodStart(), plan.getPeriodEnd(), zone);
    }

    public static BudgetInput input(BudgetPlan plan, LocalDate today, List<SpendAggregate> spending) {
        return new BudgetInput(plan.getIncomePaise(), plan.getSavingsTargetPaise(), limits(plan), plan.period(),
                today, spending.stream().map(BudgetInputs::row).toList());
    }

    /** The plan alone, with no spending (for the Plan response's derived numbers). */
    public static BudgetInput input(BudgetPlan plan) {
        return input(plan, plan.getPeriodStart(), List.of());
    }

    public static List<LimitInput> limits(BudgetPlan plan) {
        return plan.getLimits().stream()
                .map(l -> new LimitInput(l.getCategory().getId(), l.getCategory().getKind(), l.getLimitPaise()))
                .toList();
    }

    private static SpendRow row(SpendAggregate a) {
        return new SpendRow(a.day(), a.categoryId(), a.kind(), a.totalPaise());
    }
}
