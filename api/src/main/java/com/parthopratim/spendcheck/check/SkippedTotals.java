package com.parthopratim.spendcheck.check;

/** SKIPPED spend checks: how many, and the spending they avoided ("pauses saved"). */
public record SkippedTotals(long count, long totalPaise) {}
