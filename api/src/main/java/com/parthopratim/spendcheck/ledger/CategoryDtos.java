package com.parthopratim.spendcheck.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryResponse(Long id, String name, CategoryKind kind, List<String> aliases, boolean archived) {
        static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getKind(), List.copyOf(c.getAliases()),
                    c.isArchived());
        }
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 50) String name,
            @NotNull CategoryKind kind,
            @Size(max = 50) List<@NotBlank @Size(max = 50) String> aliases) {}

    /** All fields optional; null means "leave unchanged". */
    public record UpdateCategoryRequest(
            @Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
            CategoryKind kind,
            @Size(max = 50) List<@NotBlank @Size(max = 50) String> aliases,
            Boolean archived) {}
}
