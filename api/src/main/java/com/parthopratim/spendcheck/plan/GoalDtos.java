package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.engine.BudgetEngine;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class GoalDtos {

    private static final long MAX = PlanDtos.MAX_PLAN_AMOUNT_PAISE;

    private GoalDtos() {
    }

    public record GoalResponse(
            Long id, String name, long targetPaise, long savedPaise, long monthlyContributionPaise,
            LocalDate targetDate, int progressPct, LocalDate onTrackDate) {

        public static GoalResponse from(Goal g, LocalDate today) {
            return new GoalResponse(g.getId(), g.getName(), g.getTargetPaise(), g.getSavedPaise(),
                    g.getMonthlyContributionPaise(), g.getTargetDate(),
                    BudgetEngine.progressPct(g.getSavedPaise(), g.getTargetPaise()),
                    BudgetEngine.onTrackDate(g.getTargetPaise(), g.getSavedPaise(), g.getMonthlyContributionPaise(),
                            today));
        }
    }

    public record CreateGoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Positive @Max(MAX) Long targetPaise,
            @PositiveOrZero @Max(MAX) Long savedPaise,
            @NotNull @PositiveOrZero @Max(MAX) Long monthlyContributionPaise,
            LocalDate targetDate) {}

    /** All fields optional; null means "leave unchanged". */
    public record UpdateGoalRequest(
            @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
            @Positive @Max(MAX) Long targetPaise,
            @PositiveOrZero @Max(MAX) Long savedPaise,
            @PositiveOrZero @Max(MAX) Long monthlyContributionPaise,
            LocalDate targetDate) {}
}
