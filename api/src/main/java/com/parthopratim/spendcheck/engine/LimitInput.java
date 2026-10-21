package com.parthopratim.spendcheck.engine;

import com.parthopratim.spendcheck.ledger.CategoryKind;

/** One plan limit row. */
public record LimitInput(long categoryId, CategoryKind kind, long limitPaise) {}
