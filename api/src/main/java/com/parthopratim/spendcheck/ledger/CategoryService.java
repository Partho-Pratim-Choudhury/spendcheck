package com.parthopratim.spendcheck.ledger;

import com.parthopratim.spendcheck.common.ConflictException;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.ledger.CategoryDtos.CategoryResponse;
import com.parthopratim.spendcheck.ledger.CategoryDtos.CreateCategoryRequest;
import com.parthopratim.spendcheck.ledger.CategoryDtos.UpdateCategoryRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
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
        Category c = categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category " + id + " not found."));
        if (req.name() != null) {
            String name = req.name().trim();
            if (categories.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
                throw new ConflictException("A category named '" + name + "' already exists.");
            }
            c.setName(name);
        }
        if (req.kind() != null) {
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
