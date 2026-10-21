package com.parthopratim.spendcheck.ledger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class TransactionDtos {

    /** 10,00,00,000 paise = Rs 10,00,000. */
    public static final long MAX_AMOUNT_PAISE = 100_000_000L;

    private TransactionDtos() {
    }

    public record TransactionResponse(
            UUID id, long amountPaise, Long categoryId, String categoryName, String merchant, String note,
            Instant spentAt, NeedOrWant needOrWant, TransactionSource source, TransactionStatus status,
            Instant createdAt, Instant updatedAt) {

        static TransactionResponse from(Transaction t) {
            return new TransactionResponse(t.getId(), t.getAmountPaise(), t.getCategory().getId(),
                    t.getCategory().getName(), t.getMerchant(), t.getNote(), t.getSpentAt(), t.getNeedOrWant(),
                    t.getSource(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record CreateTransactionRequest(
            @NotNull UUID id,
            @NotNull @Positive @Max(MAX_AMOUNT_PAISE) Long amountPaise,
            @NotNull Long categoryId,
            @Size(max = 200) String merchant,
            @Size(max = 1000) String note,
            @NotNull Instant spentAt,
            NeedOrWant needOrWant,
            @NotNull TransactionSource source) {}

    /** All fields optional; null means "leave unchanged". */
    public record UpdateTransactionRequest(
            @Positive @Max(MAX_AMOUNT_PAISE) Long amountPaise,
            Long categoryId,
            @Size(max = 200) String merchant,
            @Size(max = 1000) String note,
            Instant spentAt,
            NeedOrWant needOrWant) {}
}
