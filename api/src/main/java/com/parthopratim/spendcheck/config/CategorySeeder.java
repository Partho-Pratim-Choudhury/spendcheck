package com.parthopratim.spendcheck.config;

import com.parthopratim.spendcheck.config.SeedRunner.Seed;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The single source of truth for creating a user's default categories (from {@link SeedRunner#DEFAULT_CATEGORIES}). */
@Component
public class CategorySeeder {

    private final CategoryRepository categories;

    public CategorySeeder(CategoryRepository categories) {
        this.categories = categories;
    }

    /** Creates the default category set for {@code userId}. Call once, when the account is created. */
    @Transactional
    public void seedDefaults(Long userId) {
        for (Seed c : SeedRunner.DEFAULT_CATEGORIES) {
            categories.save(new Category(userId, c.name(), c.kind(), c.aliases()));
        }
    }
}
