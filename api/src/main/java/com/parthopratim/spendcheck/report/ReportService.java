package com.parthopratim.spendcheck.report;

import com.parthopratim.spendcheck.auth.UserTime;
import com.parthopratim.spendcheck.check.SkippedTotals;
import com.parthopratim.spendcheck.check.SpendCheckRepository;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.engine.BudgetEngine;
import com.parthopratim.spendcheck.engine.BudgetInput;
import com.parthopratim.spendcheck.engine.CategoryStatus;
import com.parthopratim.spendcheck.engine.MonthNumbers;
import com.parthopratim.spendcheck.engine.SummaryText;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryRepository;
import com.parthopratim.spendcheck.ledger.NeedOrWant;
import com.parthopratim.spendcheck.ledger.SpendAggregate;
import com.parthopratim.spendcheck.ledger.SpendAggregateRepository;
import com.parthopratim.spendcheck.plan.BudgetInputs;
import com.parthopratim.spendcheck.plan.BudgetPlan;
import com.parthopratim.spendcheck.plan.BudgetPlanRepository;
import com.parthopratim.spendcheck.plan.GoalDtos.GoalResponse;
import com.parthopratim.spendcheck.plan.GoalService;
import com.parthopratim.spendcheck.plan.PlanService;
import com.parthopratim.spendcheck.report.ReportDtos.BurnDay;
import com.parthopratim.spendcheck.report.ReportDtos.CategoryLine;
import com.parthopratim.spendcheck.report.ReportDtos.CategorySpend;
import com.parthopratim.spendcheck.report.ReportDtos.DailySummary;
import com.parthopratim.spendcheck.report.ReportDtos.DashboardPlan;
import com.parthopratim.spendcheck.report.ReportDtos.DashboardResponse;
import com.parthopratim.spendcheck.report.ReportDtos.Month;
import com.parthopratim.spendcheck.report.ReportDtos.MonthlyReport;
import com.parthopratim.spendcheck.report.ReportDtos.ReportPlan;
import com.parthopratim.spendcheck.report.ReportDtos.SummaryMonth;
import com.parthopratim.spendcheck.report.ReportDtos.Today;
import com.parthopratim.spendcheck.report.ReportDtos.TopWant;
import com.parthopratim.spendcheck.report.ReportDtos.Totals;
import com.parthopratim.spendcheck.report.ReportDtos.TrendPoint;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard, monthly report and daily summary. Spending is aggregated in SQL by day/category and selected by the
 * plan's date range in the user's tz (not by transactions.plan_id), then run through the engine.
 */
@Service
public class ReportService {

    private static final int TREND_PLANS = 6;
    private static final int TOP_WANTS = 5;

    private final PlanService planService;
    private final BudgetPlanRepository plans;
    private final GoalService goals;
    private final CategoryRepository categories;
    private final SpendCheckRepository checks;
    private final SpendAggregateRepository aggregates;
    private final BudgetInputs budgetInputs;
    private final UserTime userTime;

    public ReportService(PlanService planService, BudgetPlanRepository plans, GoalService goals,
                         CategoryRepository categories, SpendCheckRepository checks,
                         SpendAggregateRepository aggregates, BudgetInputs budgetInputs, UserTime userTime) {
        this.planService = planService;
        this.plans = plans;
        this.goals = goals;
        this.categories = categories;
        this.checks = checks;
        this.aggregates = aggregates;
        this.budgetInputs = budgetInputs;
        this.userTime = userTime;
    }

    // ---------- dashboard ----------

    @Transactional
    public DashboardResponse dashboard(long userId) {
        BudgetPlan plan = planService.activeUpToDate(userId)
                .orElseThrow(() -> new NotFoundException("No active plan."));
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(zone);
        BudgetInput in = BudgetInputs.input(plan, today, budgetInputs.spending(plan, zone));
        MonthNumbers m = BudgetEngine.month(in);
        Map<Long, Category> cats = categoriesById(userId);

        List<CategoryLine> lines = BudgetEngine.categoryStatuses(in).stream()
                .map(c -> new CategoryLine(c.categoryId(), name(cats, c.categoryId()), c.kind(), c.limitPaise(),
                        c.spentPaise(), c.leftPaise(), c.pct(), c.status()))
                .toList();
        List<BurnDay> burn = BudgetEngine.burn(in).stream()
                .map(b -> new BurnDay(b.date(), b.cumulativePaise())).toList();
        SkippedTotals skipped = checks.skippedInPlan(userId, plan.getId());
        GoalResponse primary = goals.primary(userId).map(g -> GoalResponse.from(g, today)).orElse(null);

        return new DashboardResponse(
                new DashboardPlan(plan.getId(), plan.getPeriodStart(), plan.getPeriodEnd(), m.daysTotal(),
                        m.daysElapsed(), m.daysLeft()),
                new Today(today, m.todayBudgetPaise(), m.spentTodayPaise(), m.todayLeftPaise()),
                new Month(m.discretionaryPaise(), m.spentPaise(), m.leftPaise(), m.projectedPaise(),
                        m.savingsAtRiskPaise()),
                lines, burn, skipped.totalPaise(), skipped.count(), primary);
    }

    // ---------- monthly report ----------

    @Transactional
    public MonthlyReport monthly(long userId, Long planId) {
        planService.activeUpToDate(userId);
        BudgetPlan plan = (planId != null
                ? plans.findByIdAndUserId(planId, userId)
                : plans.findFirstByUserIdOrderByPeriodStartDescIdDesc(userId))
                .orElseThrow(() -> new NotFoundException(planId != null ? "Plan " + planId + " not found."
                        : "No plan."));
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(zone);
        List<SpendAggregate> rows = budgetInputs.spending(plan, zone);
        BudgetInput in = BudgetInputs.input(plan, today, rows);
        MonthNumbers m = BudgetEngine.month(in);
        Map<Long, Category> cats = categoriesById(userId);

        Totals totals = new Totals(plan.getIncomePaise(), m.fixedTotalPaise(), m.discretionaryPaise(), m.spentPaise(),
                plan.getSavingsTargetPaise() + m.leftPaise(), plan.getSavingsTargetPaise(), m.leftPaise());

        List<CategorySpend> byCategory = BudgetEngine.categoryStatuses(in).stream()
                .sorted(Comparator.comparingLong(CategoryStatus::spentPaise).reversed()
                        .thenComparingLong(CategoryStatus::categoryId))
                .map(c -> new CategorySpend(c.categoryId(), name(cats, c.categoryId()), c.kind(), c.limitPaise(),
                        c.spentPaise()))
                .toList();

        long needs = 0;
        long wants = 0;
        long untagged = 0;
        long[] wantsByWeekday = new long[7];
        Map<String, long[]> wantGroups = new LinkedHashMap<>();
        for (SpendAggregate a : rows) {
            NeedOrWant tag = a.effectiveNeedOrWant();
            if (tag == NeedOrWant.NEED) {
                needs += a.totalPaise();
            } else if (tag == NeedOrWant.WANT) {
                wants += a.totalPaise();
                wantsByWeekday[a.day().getDayOfWeek().getValue() - 1] += a.totalPaise();
                String label = a.merchantKey() != null ? a.merchantKey() : name(cats, a.categoryId());
                long[] g = wantGroups.computeIfAbsent(label, k -> new long[2]);
                g[0] += a.count();
                g[1] += a.totalPaise();
            } else {
                untagged += a.totalPaise();
            }
        }
        List<TopWant> topWants = wantGroups.entrySet().stream()
                .map(e -> new TopWant(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(TopWant::totalPaise).reversed()
                        .thenComparing(Comparator.comparingLong(TopWant::count).reversed())
                        .thenComparing(TopWant::label))
                .limit(TOP_WANTS)
                .toList();

        SkippedTotals skipped = checks.skippedInPlan(userId, plan.getId());
        List<Long> weekday = new ArrayList<>(7);
        for (long w : wantsByWeekday) {
            weekday.add(w);
        }
        return new MonthlyReport(
                new ReportPlan(plan.getId(), plan.getPeriodStart(), plan.getPeriodEnd(), plan.getStatus()),
                totals, byCategory, needs, wants, untagged, topWants, skipped.totalPaise(), skipped.count(), weekday,
                trend(userId, plan, zone));
    }

    /** Up to the last six plans up to and including {@code plan}, oldest first; one aggregate query for all. */
    private List<TrendPoint> trend(long userId, BudgetPlan plan, ZoneId zone) {
        List<BudgetPlan> recent = new ArrayList<>(plans
                .findTop6ByUserIdAndPeriodStartLessThanEqualOrderByPeriodStartDescIdDesc(userId,
                        plan.getPeriodStart()));
        if (recent.stream().noneMatch(p -> p.getId().equals(plan.getId()))) {
            recent.add(0, plan);
        }
        List<BudgetPlan> window = recent.stream().limit(TREND_PLANS)
                .sorted(Comparator.comparing(BudgetPlan::getPeriodStart).thenComparing(BudgetPlan::getId))
                .toList();
        LocalDate from = window.stream().map(BudgetPlan::getPeriodStart).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = window.stream().map(BudgetPlan::getPeriodEnd).max(Comparator.naturalOrder()).orElseThrow();
        List<SpendAggregate> rows = aggregates.between(userId, from, to, zone);
        return window.stream().map(p -> {
            List<SpendAggregate> mine = rows.stream().filter(a -> p.period().contains(a.day())).toList();
            MonthNumbers m = BudgetEngine.month(BudgetInputs.input(p, p.getPeriodEnd(), mine));
            return new TrendPoint(p.getId(), p.getPeriodStart(), m.spentPaise(), m.discretionaryPaise());
        }).toList();
    }

    // ---------- daily summary ----------

    /** Numbers as of the end of {@code date} (default today): spending after that day is ignored. */
    @Transactional
    public DailySummary daily(long userId, LocalDate date) {
        planService.activeUpToDate(userId);
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(zone);
        LocalDate day = date != null ? date : today;
        BudgetPlan plan = plans.findCovering(userId, day).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("No plan covers " + day + "."));
        List<SpendAggregate> rows = budgetInputs.spending(plan, zone).stream()
                .filter(a -> !a.day().isAfter(day)).toList();
        MonthNumbers m = BudgetEngine.month(BudgetInputs.input(plan, day, rows));
        long count = rows.stream().filter(a -> a.variable() && a.day().isEqual(day))
                .mapToLong(SpendAggregate::count).sum();
        long over = Math.max(0, m.spentTodayPaise() - m.todayBudgetPaise());
        SkippedTotals skipped = checks.skippedBetween(userId, day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant());
        String text = SummaryText.daily(day, today, m.spentTodayPaise(), count, m.todayBudgetPaise(), m.spentPaise(),
                m.discretionaryPaise(), m.projectedPaise(), skipped.totalPaise());
        return new ReportDtos.DailySummary(day, m.spentTodayPaise(), count, m.todayBudgetPaise(), over,
                new SummaryMonth(m.spentPaise(), m.discretionaryPaise(), m.projectedPaise(), m.savingsAtRiskPaise()),
                skipped.totalPaise(), text);
    }

    private Map<Long, Category> categoriesById(long userId) {
        return categories.findByUserIdOrderByIdAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private static String name(Map<Long, Category> cats, long id) {
        Category c = cats.get(id);
        return c != null ? c.getName() : "Unknown";
    }
}
