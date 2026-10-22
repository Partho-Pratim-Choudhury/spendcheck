package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.auth.User;
import com.parthopratim.spendcheck.auth.UserRepository;
import com.parthopratim.spendcheck.auth.UserResponse;
import com.parthopratim.spendcheck.auth.UserTime;
import com.parthopratim.spendcheck.check.SpendCheckRepository;
import com.parthopratim.spendcheck.common.ConflictException;
import com.parthopratim.spendcheck.common.FieldValidationException;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.engine.BudgetEngine;
import com.parthopratim.spendcheck.engine.MonthNumbers;
import com.parthopratim.spendcheck.engine.Period;
import com.parthopratim.spendcheck.engine.PeriodCalculator;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.ledger.CategoryRepository;
import com.parthopratim.spendcheck.ledger.TransactionRepository;
import com.parthopratim.spendcheck.plan.PlanDtos.LimitRequest;
import com.parthopratim.spendcheck.plan.PlanDtos.LimitResponse;
import com.parthopratim.spendcheck.plan.PlanDtos.PlanResponse;
import com.parthopratim.spendcheck.plan.PlanDtos.PlanSummary;
import com.parthopratim.spendcheck.plan.PlanDtos.PutPlanRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Budget plans: one ACTIVE plan per user (partial unique index). Every change to which plan is active (create,
 * rollover) first takes a row lock on the user, so concurrent requests and the daily job serialise per user
 * instead of racing on the index; a violation that still slips through surfaces as 409.
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final BudgetPlanRepository plans;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final SpendCheckRepository checks;
    private final UserTime userTime;
    private final TransactionTemplate tx;

    public PlanService(BudgetPlanRepository plans, CategoryRepository categories, UserRepository users,
                       TransactionRepository transactions, SpendCheckRepository checks, UserTime userTime,
                       PlatformTransactionManager txManager) {
        this.plans = plans;
        this.categories = categories;
        this.users = users;
        this.transactions = transactions;
        this.checks = checks;
        this.userTime = userTime;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional
    public PlanResponse current(long userId) {
        return activeUpToDate(userId).map(PlanService::toResponse)
                .orElseThrow(() -> new NotFoundException("No active plan."));
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list(long userId) {
        return plans.findByUserIdOrderByPeriodStartDescIdDesc(userId).stream().map(PlanSummary::from).toList();
    }

    /**
     * The user's ACTIVE plan, first rolled forward if its period ended before today. Call inside a read-write
     * transaction; the returned entity is managed.
     */
    public Optional<BudgetPlan> activeUpToDate(long userId) {
        rollForwardIfDue(userId);
        return plans.findByUserIdAndStatus(userId, PlanStatus.ACTIVE);
    }

    /**
     * Rolls the user's ACTIVE plan forward if its period ended before today, loading nothing when it has not (one
     * cheap probe). Call inside a read-write transaction.
     */
    public void rollForwardIfDue(long userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
        LocalDate today = userTime.today(user);
        Optional<LocalDate> end = plans.findPeriodEndByUserIdAndStatus(userId, PlanStatus.ACTIVE);
        if (end.isPresent() && end.get().isBefore(today)) {
            rollForwardLocked(lock(userId), today);
        }
    }

    /**
     * The daily job's work: rolls forward every user whose ACTIVE plan has ended, each in its own transaction.
     * Only users whose plan ended before the latest "today" on Earth (UTC+14) are candidates, so nobody due is
     * missed whatever their tz; each is re-checked in their own tz under the lock.
     *
     * @return how many users were rolled forward
     */
    public int rolloverAllDue() {
        int rolled = 0;
        LocalDate latestToday = LocalDate.ofInstant(userTime.now(), ZoneOffset.MAX);
        for (Long userId : plans.findUserIdsWithActivePlanEndingBefore(latestToday)) {
            try {
                Boolean done = tx.execute(s -> {
                    User user = lock(userId);
                    return rollForwardLocked(user, userTime.today(user));
                });
                if (Boolean.TRUE.equals(done)) {
                    rolled++;
                }
            } catch (RuntimeException e) {
                log.error("Daily rollover failed for user {}", userId, e);
            }
        }
        return rolled;
    }

    @Transactional
    public PlanResponse put(long userId, PutPlanRequest req) {
        List<Category> limitCategories = validLimitCategories(userId, req.limits());
        long fixedTotal = 0;
        for (int i = 0; i < limitCategories.size(); i++) {
            if (limitCategories.get(i).getKind() == CategoryKind.FIXED) {
                fixedTotal += req.limits().get(i).limitPaise();
            }
        }
        if (req.incomePaise() - fixedTotal - req.savingsTargetPaise() < 0) {
            throw new FieldValidationException("savingsTargetPaise",
                    "income minus fixed limits and savings target must not be negative");
        }

        User user = lock(userId);
        LocalDate today = userTime.today(user);
        rollForwardLocked(user, today);
        BudgetPlan plan = plans.findByUserIdAndStatus(userId, PlanStatus.ACTIVE).orElse(null);
        boolean created = plan == null;
        if (created) {
            plan = new BudgetPlan(userId, PeriodCalculator.periodContaining(today, user.getSalaryDay()),
                    req.incomePaise(), req.savingsTargetPaise());
        } else {
            plan.setIncomePaise(req.incomePaise());
            plan.setSavingsTargetPaise(req.savingsTargetPaise());
        }
        mergeLimits(plan, limitCategories, req.limits());
        plan = plans.saveAndFlush(plan);
        if (created) {
            assignTransactions(user, plan);
        }
        return toResponse(plan);
    }

    /**
     * "Salary came today": closes the active plan and starts a new one at today, copying its amounts (or those of
     * the latest plan when none is active). The closed plan's end is cut back to yesterday so periods don't
     * overlap. If the active plan only started today or yesterday, it is kept and just given the new end, since
     * a plan must span at least two days and cutting it would leave days covered by no plan.
     */
    @Transactional
    public PlanResponse rollover(long userId) {
        User user = lock(userId);
        LocalDate today = userTime.today(user);
        rollForwardLocked(user, today); // so the days since the old period ended stay covered
        BudgetPlan active = plans.findByUserIdAndStatus(userId, PlanStatus.ACTIVE).orElse(null);
        BudgetPlan source = active != null ? active
                : plans.findFirstByUserIdOrderByPeriodStartDescIdDesc(userId)
                        .orElseThrow(() -> new ConflictException("No plan to roll over; set up a plan first."));
        Period period = PeriodCalculator.rolloverPeriod(today, user.getSalaryDay());

        BudgetPlan result;
        if (active != null && !active.getPeriodStart().isBefore(today.minusDays(1))) {
            active.setPeriodEnd(period.end());
            result = active;
        } else {
            if (active != null) {
                if (!active.getPeriodEnd().isBefore(today)) {
                    active.setPeriodEnd(today.minusDays(1));
                }
                active.close();
                plans.flush(); // the old row must stop being ACTIVE before the new one is inserted
            }
            result = plans.save(BudgetPlan.copyOf(source, period));
        }
        plans.flush();
        assignTransactions(user, result);
        return toResponse(result);
    }

    @Transactional
    public UserResponse setSalaryDay(long userId, int salaryDay) {
        User user = lock(userId);
        user.setSalaryDay(salaryDay);
        users.flush();
        return UserResponse.from(user);
    }

    // ---------- internals ----------

    private User lock(long userId) {
        return users.lockById(userId).orElseThrow(() -> new NotFoundException("User not found."));
    }

    /** Caller holds the user lock. Returns whether anything was rolled. */
    private boolean rollForwardLocked(User user, LocalDate today) {
        BudgetPlan active = plans.findByUserIdAndStatus(user.getId(), PlanStatus.ACTIVE).orElse(null);
        boolean rolled = false;
        while (active != null && active.getPeriodEnd().isBefore(today)) {
            Period next = PeriodCalculator.nextPeriodAfter(active.getPeriodEnd(), user.getSalaryDay());
            active.close();
            plans.flush(); // the old row must stop being ACTIVE before the new one is inserted
            BudgetPlan successor = plans.saveAndFlush(BudgetPlan.copyOf(active, next));
            assignTransactions(user, successor);
            log.info("Rolled plan {} of user {} over to plan {} ({}..{})", active.getId(), user.getId(),
                    successor.getId(), next.start(), next.end());
            active = successor;
            rolled = true;
        }
        return rolled;
    }

    /** Points the transactions dated, and the spend checks made, within the plan's period at it. */
    private void assignTransactions(User user, BudgetPlan plan) {
        ZoneId zone = userTime.zone(user);
        Instant from = plan.getPeriodStart().atStartOfDay(zone).toInstant();
        Instant to = plan.getPeriodEnd().plusDays(1).atStartOfDay(zone).toInstant();
        transactions.assignPlan(user.getId(), plan.getId(), from, to);
        checks.assignPlan(user.getId(), plan.getId(), from, to);
    }

    /** The category of each limit, in request order; 400 with {@code limits[i].categoryId} errors otherwise. */
    private List<Category> validLimitCategories(long userId, List<LimitRequest> limits) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Category> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < limits.size(); i++) {
            Long categoryId = limits.get(i).categoryId();
            String field = "limits[" + i + "].categoryId";
            Category c = categories.findByIdAndUserId(categoryId, userId).orElse(null);
            if (c == null) {
                errors.put(field, "category does not exist");
            } else if (c.isArchived()) {
                errors.put(field, "category is archived");
            } else if (!seen.add(categoryId)) {
                errors.put(field, "duplicate category");
            }
            out.add(c);
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return out;
    }

    /** Updates rows in place (no delete + re-insert, which would trip UNIQUE(plan_id, category_id)). */
    private static void mergeLimits(BudgetPlan plan, List<Category> cats, List<LimitRequest> requested) {
        Map<Long, PlanLimit> existing = new HashMap<>();
        plan.getLimits().forEach(l -> existing.put(l.getCategory().getId(), l));
        Set<Long> keep = new HashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            Category c = cats.get(i);
            long limit = requested.get(i).limitPaise();
            keep.add(c.getId());
            PlanLimit row = existing.get(c.getId());
            if (row != null) {
                row.setLimitPaise(limit);
            } else {
                plan.addLimit(c, limit);
            }
        }
        plan.getLimits().removeIf(l -> !keep.contains(l.getCategory().getId()));
    }

    static PlanResponse toResponse(BudgetPlan p) {
        MonthNumbers m = BudgetEngine.month(BudgetInputs.input(p));
        List<LimitResponse> limits = p.getLimits().stream()
                .sorted(Comparator.comparing(l -> l.getCategory().getId()))
                .map(l -> new LimitResponse(l.getCategory().getId(), l.getCategory().getName(),
                        l.getCategory().getKind(), l.getLimitPaise()))
                .toList();
        return new PlanResponse(p.getId(), p.getPeriodStart(), p.getPeriodEnd(), p.getStatus(), p.getIncomePaise(),
                p.getSavingsTargetPaise(), limits, m.fixedTotalPaise(), m.variableLimitsPaise(),
                m.discretionaryPaise(), m.unallocatedPaise(), m.dailyBudgetPaise());
    }
}
