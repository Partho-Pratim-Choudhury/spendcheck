package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.auth.User;
import com.parthopratim.spendcheck.auth.UserRepository;
import com.parthopratim.spendcheck.auth.UserTime;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.engine.BudgetEngine;
import com.parthopratim.spendcheck.engine.BudgetInput;
import com.parthopratim.spendcheck.engine.Inr;
import com.parthopratim.spendcheck.engine.MonthNumbers;
import com.parthopratim.spendcheck.engine.Period;
import com.parthopratim.spendcheck.engine.PeriodCalculator;
import com.parthopratim.spendcheck.input.InputDtos.After;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.ledger.SpendAggregate;
import com.parthopratim.spendcheck.ledger.SpendAggregateRepository;
import com.parthopratim.spendcheck.plan.BudgetInputs;
import com.parthopratim.spendcheck.plan.BudgetPlan;
import com.parthopratim.spendcheck.plan.Goal;
import com.parthopratim.spendcheck.plan.GoalDtos.GoalResponse;
import com.parthopratim.spendcheck.plan.GoalService;
import com.parthopratim.spendcheck.plan.PlanService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Engine-backed numbers for the chat: a draft's "after" and the text answers to QUERY inputs. */
@Component
public class InputAnswers {

    static final String NO_PLAN = "You don't have a budget plan yet — set one up first and I can tell you "
            + "what's left.";
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final PlanService plans;
    private final BudgetInputs budgetInputs;
    private final SpendAggregateRepository aggregates;
    private final GoalService goals;
    private final UserRepository users;
    private final UserTime userTime;

    public InputAnswers(PlanService plans, BudgetInputs budgetInputs, SpendAggregateRepository aggregates,
                        GoalService goals, UserRepository users, UserTime userTime) {
        this.plans = plans;
        this.budgetInputs = budgetInputs;
        this.aggregates = aggregates;
        this.goals = goals;
        this.users = users;
        this.userTime = userTime;
    }

    /**
     * The category's spent (including this draft when it falls in the period) and limit. Without a plan the limit
     * is 0 and spent comes from the salary period containing today.
     */
    @Transactional
    public After after(long userId, InputCategory category, long amountPaise, LocalDate date) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
        ZoneId zone = userTime.zone(user);
        LocalDate today = userTime.today(zone);
        Optional<BudgetPlan> plan = plans.activeUpToDate(userId);
        Period period;
        long limit;
        long spent;
        if (plan.isPresent()) {
            BudgetInput in = BudgetInputs.input(plan.get(), today, budgetInputs.spending(plan.get(), zone));
            period = plan.get().period();
            limit = BudgetEngine.limitFor(in, category.id());
            spent = BudgetEngine.spentIn(in, category.id());
        } else {
            period = PeriodCalculator.periodContaining(today, user.getSalaryDay());
            limit = 0;
            spent = aggregates.between(userId, period.start(), period.end(), zone).stream()
                    .filter(a -> a.categoryId() == category.id()).mapToLong(SpendAggregate::totalPaise).sum();
        }
        return new After(spent + (period.contains(date) ? amountPaise : 0), limit);
    }

    /** The answer text for a QUERY; a missing plan is explained, not an error. */
    @Transactional
    public String answer(long userId, QueryType type, InputCategory category) {
        if (type == QueryType.GOAL_STATUS) {
            return goalStatus(userId);
        }
        Optional<BudgetPlan> plan = plans.activeUpToDate(userId);
        if (plan.isEmpty()) {
            return NO_PLAN;
        }
        ZoneId zone = userTime.zone(userId);
        BudgetInput in = BudgetInputs.input(plan.get(), userTime.today(zone), budgetInputs.spending(plan.get(), zone));
        MonthNumbers m = BudgetEngine.month(in);
        return switch (type) {
            case CATEGORY_LEFT -> categoryLeft(in, category);
            case TOTAL_SPENT -> "You've spent " + Inr.format(m.spentPaise()) + " of " + Inr.format(m.discretionaryPaise())
                    + " this period" + (m.leftPaise() >= 0
                    ? " — " + Inr.formatDown(m.leftPaise()) + " left with " + days(m.daysLeft()) + " to go."
                    : " — " + Inr.format(-m.leftPaise()) + " over plan.");
            case SAFE_TODAY -> m.todayLeftPaise() > 0
                    ? "You can safely spend " + Inr.formatDown(m.todayLeftPaise()) + " today"
                    + (m.spentTodayPaise() > 0 ? " (" + Inr.format(m.spentTodayPaise()) + " spent so far)." : ".")
                    : "You've used today's safe spend" + (m.todayLeftPaise() < 0
                    ? " — " + Inr.format(-m.todayLeftPaise()) + " over." : ".")
                    + " Anything more comes out of the rest of the period.";
            case GOAL_STATUS -> throw new IllegalStateException("handled above");
        };
    }

    private static String categoryLeft(BudgetInput in, InputCategory category) {
        long limit = BudgetEngine.limitFor(in, category.id());
        long spent = BudgetEngine.spentIn(in, category.id());
        String name = category.name();
        if (limit == 0) {
            return name + " has no limit in this plan" + (spent > 0
                    ? "; you've spent " + Inr.format(spent) + " there so far." : ".");
        }
        if (category.kind() == CategoryKind.FIXED) {
            return name + ": " + Inr.format(spent) + " paid of " + Inr.format(limit) + " planned.";
        }
        long left = limit - spent;
        return left >= 0
                ? name + ": " + Inr.formatDown(left) + " left of " + Inr.format(limit) + " (" + Inr.format(spent)
                + " spent)."
                : name + " is " + Inr.format(-left) + " over its " + Inr.format(limit) + " limit.";
    }

    private String goalStatus(long userId) {
        Optional<Goal> goal = goals.primary(userId);
        if (goal.isEmpty()) {
            return "You don't have a savings goal yet — add one in Goals.";
        }
        GoalResponse g = GoalResponse.from(goal.get(), userTime.today(userId));
        StringBuilder sb = new StringBuilder(g.name()).append(": ").append(Inr.format(g.savedPaise())).append(" of ")
                .append(Inr.format(g.targetPaise())).append(" saved (").append(g.progressPct()).append("%).");
        if (g.savedPaise() >= g.targetPaise()) {
            sb.append(" Done!");
        } else if (g.onTrackDate() != null) {
            sb.append(" On track for ").append(MONTH_YEAR.format(g.onTrackDate()));
            if (g.targetDate() != null) {
                sb.append(g.onTrackDate().isAfter(g.targetDate())
                        ? ", after your " + MONTH_YEAR.format(g.targetDate()) + " target." : ", in time for your target.");
            } else {
                sb.append('.');
            }
        } else if (g.monthlyContributionPaise() > 0) {
            // contributing, but the date is beyond the engine's horizon (onTrackDate stays null in the JSON)
            sb.append(" At ").append(Inr.format(g.monthlyContributionPaise())).append("/month it would take over ")
                    .append(BudgetEngine.ON_TRACK_HORIZON_MONTHS / 12)
                    .append(" years — raise the contribution to get there sooner.");
        } else {
            sb.append(" Set a monthly contribution to see when you'll get there.");
        }
        return sb.toString();
    }

    private static String days(int n) {
        return n == 1 ? "1 day" : n + " days";
    }
}
