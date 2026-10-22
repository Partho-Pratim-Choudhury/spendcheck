package com.parthopratim.spendcheck.ledger;

import com.parthopratim.spendcheck.common.ConflictException;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.engine.BudgetEngine;
import com.parthopratim.spendcheck.engine.BudgetInput;
import com.parthopratim.spendcheck.engine.LimitInput;
import com.parthopratim.spendcheck.ledger.CategoryDtos.CategoryResponse;
import com.parthopratim.spendcheck.ledger.CategoryDtos.CreateCategoryRequest;
import com.parthopratim.spendcheck.ledger.CategoryDtos.UpdateCategoryRequest;
import com.parthopratim.spendcheck.plan.BudgetInputs;
import com.parthopratim.spendcheck.plan.BudgetPlan;
import com.parthopratim.spendcheck.plan.PlanService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final PlanService plans;

    public CategoryService(CategoryRepository categories, PlanService plans) {
        this.categories = categories;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(long userId, boolean includeArchived) {
        List<Category> rows = includeArchived
                ? categories.findByUserIdOrderByIdAsc(userId)
                : categories.findByUserIdAndArchivedFalseOrderByIdAsc(userId);
        return rows.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(long userId, CreateCategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("A category named '" + name + "' already exists.");
        }
        Category saved = categories.saveAndFlush(new Category(userId, name, req.kind(), normalize(req.aliases())));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(long userId, long id, UpdateCategoryRequest req) {
        // a kind change is checked against the ACTIVE plan: take the user lock first, as plan writes do, so a
        // concurrent PUT /plans/current or rollover can't slip in between the check and the change
        BudgetPlan active = req.kind() != null ? plans.lockActiveUpToDate(userId).orElse(null) : null;
        Category c = categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category " + id + " not found."));
        if (req.name() != null) {
            String name = req.name().trim();
            if (categories.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
                throw new ConflictException("A category named '" + name + "' already exists.");
            }
            c.setName(name);
        }
        if (req.kind() != null && req.kind() != c.getKind()) {
            checkKindChangeKeepsPlanValid(active, c, req.kind());
            c.setKind(req.kind());
        }
        if (req.aliases() != null) {
            c.setAliases(normalize(req.aliases()));
        }
        if (req.archived() != null) {
            c.setArchived(req.archived());
        }
        categories.flush();
        return CategoryResponse.from(c);
    }

    /**
     * A kind change reclassifies the category's limit in the ACTIVE plan (to or from FIXED), which moves the plan's
     * discretionary amount; refuse it when that would go negative, as {@code PUT /plans/current} would. The caller
     * holds the user lock and passes the rolled-forward ACTIVE plan (or null).
     */
    private static void checkKindChangeKeepsPlanValid(BudgetPlan plan, Category c, CategoryKind newKind) {
        if (plan == null || plan.getLimits().stream().noneMatch(l -> l.getCategory().getId().equals(c.getId()))) {
            return;
        }
        List<LimitInput> limits = BudgetInputs.limits(plan).stream()
                .map(l -> l.categoryId() == c.getId() ? new LimitInput(l.categoryId(), newKind, l.limitPaise()) : l)
                .toList();
        long discretionary = BudgetEngine.month(new BudgetInput(
                plan.getIncomePaise(), plan.getSavingsTargetPaise(), limits, plan.period(), plan.getPeriodStart(),
                List.of())).discretionaryPaise();
        if (discretionary < 0) {
            throw new ConflictException("Making '" + c.getName() + "' " + newKind + " would leave the current plan's "
                    + "income minus fixed limits and savings target negative. Lower its limit or the savings target "
                    + "in the plan first.");
        }
    }

    /** Aliases are matched case-insensitively later, so store them trimmed, lower-case and de-duplicated. */
    private static List<String> normalize(List<String> aliases) {
        if (aliases == null) {
            return List.of();
        }
        return aliases.stream()
                .map(a -> a.trim().toLowerCase(Locale.ROOT))
                .filter(a -> !a.isEmpty())
                .distinct()
                .toList();
    }
}
