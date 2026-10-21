package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.ledger.CategoryKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class PlanDtos {

    /** 1,00,00,00,00,000 paise = Rs 1,000 crore; keeps every sum far from overflow. */
    public static final long MAX_PLAN_AMOUNT_PAISE = 1_000_000_000_000L;

    private PlanDtos() {
    }

    public record LimitResponse(Long categoryId, String categoryName, CategoryKind kind, long limitPaise) {}

    public record PlanResponse(
            Long id, LocalDate periodStart, LocalDate periodEnd, PlanStatus status,
            long incomePaise, long savingsTargetPaise, List<LimitResponse> limits,
            long fixedTotalPaise, long variableLimitsPaise, long discretionaryPaise, long unallocatedPaise,
            long dailyBudgetPaise) {}

    public record PlanSummary(Long id, LocalDate periodStart, LocalDate periodEnd, PlanStatus status) {
        static PlanSummary from(BudgetPlan p) {
            return new PlanSummary(p.getId(), p.getPeriodStart(), p.getPeriodEnd(), p.getStatus());
        }
    }

    public record PutPlanRequest(
            @NotNull @Positive @Max(MAX_PLAN_AMOUNT_PAISE) Long incomePaise,
            @NotNull @PositiveOrZero @Max(MAX_PLAN_AMOUNT_PAISE) Long savingsTargetPaise,
            @NotNull @Size(max = 200) List<@NotNull @Valid LimitRequest> limits) {}

    public record LimitRequest(
            @NotNull Long categoryId,
            @NotNull @PositiveOrZero @Max(MAX_PLAN_AMOUNT_PAISE) Long limitPaise) {}

    public record SalaryDayRequest(@NotNull @Min(1) @Max(31) Integer salaryDay) {}
}
