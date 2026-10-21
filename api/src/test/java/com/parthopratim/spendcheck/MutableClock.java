package com.parthopratim.spendcheck;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A settable clock so integration tests can pick "today". */
public class MutableClock extends Clock {

    private volatile Instant instant = Instant.now();

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void reset() {
        this.instant = Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
