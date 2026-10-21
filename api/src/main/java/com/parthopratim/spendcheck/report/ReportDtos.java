package com.parthopratim.spendcheck.report;

import com.parthopratim.spendcheck.engine.CategoryStatus;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.plan.GoalDtos.GoalResponse;
import com.parthopratim.spendcheck.plan.PlanStatus;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    // ---------- dashboard ----------

    public record DashboardResponse(
            DashboardPlan plan, Today today, Month month, List<CategoryLine> categories, List<BurnDay> burn,
            long pausesSavedPaise, long skippedCount, GoalResponse primaryGoal) {}

    public record DashboardPlan(Long id, LocalDate periodStart, LocalDate periodEnd, int daysTotal, int daysElapsed,
                                int daysLeft) {}

    public record Today(LocalDate date, long budgetPaise, long spentPaise, long leftPaise) {}

    public record Month(long discretionaryPaise, long spentPaise, long leftPaise, long projectedPaise,
                        long savingsAtRiskPaise) {}

    public record CategoryLine(long categoryId, String name, CategoryKind kind, long limitPaise, long spentPaise,
                               long leftPaise, long pct, CategoryStatus.Status status) {}

    public record BurnDay(LocalDate date, long cumulativePaise) {}

    // ---------- monthly report ----------

    public record MonthlyReport(
            ReportPlan plan, Totals totals, List<CategorySpend> byCategory,
            long needsPaise, long wantsPaise, long untaggedPaise, List<TopWant> topWants,
            long pausesSavedPaise, long skippedCount, List<Long> wantsByWeekday, List<TrendPoint> trend) {}

    public record ReportPlan(Long id, LocalDate periodStart, LocalDate periodEnd, PlanStatus status) {}

    public record Totals(long incomePaise, long fixedPaise, long discretionaryPaise, long spentPaise, long savedPaise,
                         long savingsTargetPaise, long underPlanPaise) {}

    public record CategorySpend(long categoryId, String name, CategoryKind kind, long limitPaise, long spentPaise) {}

    public record TopWant(String label, long count, long totalPaise) {}

    public record TrendPoint(Long planId, LocalDate periodStart, long spentPaise, long discretionaryPaise) {}

    // ---------- daily summary ----------

    public record DailySummary(LocalDate date, long spentPaise, long count, long budgetPaise, long overPaise,
                               SummaryMonth month, long pausesSavedPaise, String text) {}

    public record SummaryMonth(long spentPaise, long discretionaryPaise, long projectedPaise,
                               long savingsAtRiskPaise) {}
}
