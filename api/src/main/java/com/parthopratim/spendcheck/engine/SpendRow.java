package com.parthopratim.spendcheck.engine;

import com.parthopratim.spendcheck.ledger.CategoryKind;
import java.time.LocalDate;

/** Confirmed spending in one category on one day (user tz); may be pre-aggregated. */
public record SpendRow(LocalDate date, long categoryId, CategoryKind kind, long amountPaise) {

    public boolean variable() {
        return kind != CategoryKind.FIXED;
    }
}
