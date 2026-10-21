package com.parthopratim.spendcheck.check;

import com.parthopratim.spendcheck.engine.Verdict;
import com.parthopratim.spendcheck.engine.VerdictLevel;
import com.parthopratim.spendcheck.ledger.TransactionDtos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public final class CheckDtos {

    private CheckDtos() {
    }

    public record CreateCheckRequest(
            @NotNull @Positive @Max(TransactionDtos.MAX_AMOUNT_PAISE) Long amountPaise,
            @NotNull Long categoryId) {}

    public record VerdictResponse(
            Long checkId, VerdictLevel level, long amountPaise, long categoryId, String categoryName,
            long categoryLimitPaise, long categorySpentPaise, long categoryLeftPaise,
            long overflowPaise, long savingsHitPaise, String goalName, Long goalDelayDays,
            long todayBudgetPaise, long todaySpentPaise, long safePerDayBeforePaise, long safePerDayAfterPaise,
            List<String> reasons, String message) {

        public static VerdictResponse from(Long checkId, Verdict v) {
            return new VerdictResponse(checkId, v.level(), v.amountPaise(), v.categoryId(), v.categoryName(),
                    v.categoryLimitPaise(), v.categorySpentPaise(), v.categoryLeftPaise(), v.overflowPaise(),
                    v.savingsHitPaise(), v.goalName(), v.goalDelayDays(), v.todayBudgetPaise(), v.todaySpentPaise(),
                    v.safePerDayBeforePaise(), v.safePerDayAfterPaise(), v.reasons(), v.message());
        }
    }

    public record DecisionRequest(@NotNull CheckDecision decision, UUID transactionId) {}

    public record DecisionResponse(Long id, CheckDecision decision) {}
}
