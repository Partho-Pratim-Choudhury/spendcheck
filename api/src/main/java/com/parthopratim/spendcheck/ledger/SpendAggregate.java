package com.parthopratim.spendcheck.ledger;

import java.time.LocalDate;

/**
 * Confirmed transactions summed per (day in user tz, category, needOrWant tag, lower-cased merchant).
 *
 * @param merchantKey lower-cased merchant, or null
 */
public record SpendAggregate(LocalDate day, long categoryId, CategoryKind kind, NeedOrWant needOrWant,
                             String merchantKey, long count, long totalPaise) {

    public boolean variable() {
        return kind != CategoryKind.FIXED;
    }

    /** The transaction's own tag, else the category kind when that is NEED/WANT, else null (untagged). */
    public NeedOrWant effectiveNeedOrWant() {
        if (needOrWant != null) {
            return needOrWant;
        }
        return switch (kind) {
            case NEED -> NeedOrWant.NEED;
            case WANT -> NeedOrWant.WANT;
            case FIXED -> null;
        };
    }
}
