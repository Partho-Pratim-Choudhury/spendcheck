package com.parthopratim.spendcheck.engine;

import java.time.LocalDate;
import java.util.Objects;

/** An inclusive date range {@code [start, end]} covered by one budget plan. */
public record Period(LocalDate start, LocalDate end) {

    public Period {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Period end " + end + " is before start " + start);
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
