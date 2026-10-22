package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import java.util.List;

/** The slice of a category the NLU pipeline needs; decoupled from JPA so the eval can build it in memory. */
public record InputCategory(long id, String name, CategoryKind kind, List<String> aliases, boolean archived) {

    public InputCategory {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public static InputCategory from(Category c) {
        return new InputCategory(c.getId(), c.getName(), c.getKind(), c.getAliases(), c.isArchived());
    }
}
