package com.parthopratim.spendcheck.check;

import com.parthopratim.spendcheck.auth.UserTime;
import com.parthopratim.spendcheck.check.CheckDtos.DecisionRequest;
import com.parthopratim.spendcheck.check.CheckDtos.DecisionResponse;
import com.parthopratim.spendcheck.check.CheckDtos.VerdictResponse;
import com.parthopratim.spendcheck.common.ConflictException;
import com.parthopratim.spendcheck.common.FieldValidationException;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.engine.BudgetEngine;
import com.parthopratim.spendcheck.engine.BudgetInput;
import com.parthopratim.spendcheck.engine.Verdict;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryRepository;
import com.parthopratim.spendcheck.ledger.TransactionRepository;
import com.parthopratim.spendcheck.plan.BudgetInputs;
import com.parthopratim.spendcheck.plan.BudgetPlan;
import com.parthopratim.spendcheck.plan.Goal;
import com.parthopratim.spendcheck.plan.GoalService;
import com.parthopratim.spendcheck.plan.PlanService;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckService {

    private final SpendCheckRepository checks;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final PlanService plans;
    private final GoalService goals;
    private final BudgetInputs budgetInputs;
    private final UserTime userTime;

    public CheckService(SpendCheckRepository checks, CategoryRepository categories,
                        TransactionRepository transactions, PlanService plans, GoalService goals,
                        BudgetInputs budgetInputs, UserTime userTime) {
        this.checks = checks;
        this.categories = categories;
        this.transactions = transactions;
        this.plans = plans;
        this.goals = goals;
        this.budgetInputs = budgetInputs;
        this.userTime = userTime;
    }

    /**
     * Runs the engine for "spend {@code amountPaise} in {@code categoryId} now", records the check and returns the
     * verdict. Shared by {@code POST /checks} and the chat pipeline.
     *
     * @throws FieldValidationException {@code categoryId} unknown, foreign or archived
     * @throws ConflictException        no active plan
     */
    @Transactional
    public VerdictResponse check(long userId, long amountPaise, long categoryId) {
        Category category = categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new FieldValidationException("categoryId", "category does not exist"));
        if (category.isArchived()) {
            throw new FieldValidationException("categoryId", "category is archived");
        }
        BudgetPlan plan = plans.activeUpToDate(userId)
                .orElseThrow(() -> new ConflictException("No active plan; set up a plan first."));
        ZoneId zone = userTime.zone(userId);
        BudgetInput in = BudgetInputs.input(plan, userTime.today(zone), budgetInputs.spending(plan, zone));
        Verdict v = BudgetEngine.verdict(in, category.getId(), category.getKind(), category.getName(), amountPaise,
                goals.primary(userId).map(Goal::toInput));
        SpendCheck saved = checks.saveAndFlush(
                new SpendCheck(userId, plan.getId(), amountPaise, category.getId(), v.level(), userTime.now()));
        return VerdictResponse.from(saved.getId(), v);
    }

    /** Records the decision; a {@code transactionId} (must be the user's) is linked only when PROCEEDED. */
    @Transactional
    public DecisionResponse decide(long userId, long checkId, DecisionRequest req) {
        SpendCheck check = checks.findByIdAndUserId(checkId, userId)
                .orElseThrow(() -> new NotFoundException("Check " + checkId + " not found."));
        if (req.decision() == CheckDecision.UNKNOWN) {
            throw new FieldValidationException("decision", "must be PROCEEDED, SKIPPED or DEFERRED");
        }
        if (req.transactionId() != null && transactions.findByIdAndUserId(req.transactionId(), userId).isEmpty()) {
            throw new FieldValidationException("transactionId", "transaction does not exist");
        }
        check.setDecision(req.decision());
        check.setTransactionId(req.decision() == CheckDecision.PROCEEDED ? req.transactionId() : null);
        checks.flush();
        return new DecisionResponse(check.getId(), check.getDecision());
    }
}
