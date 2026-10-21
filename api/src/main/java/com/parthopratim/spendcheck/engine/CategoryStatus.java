package com.parthopratim.spendcheck.engine;

import com.parthopratim.spendcheck.ledger.CategoryKind;

public record CategoryStatus(long categoryId, CategoryKind kind, long limitPaise, long spentPaise, long leftPaise,
                             long pct, Status status) {

    /** Declared in dashboard sort order. */
    public enum Status {
        OVER, WARN, OK
    }
}
