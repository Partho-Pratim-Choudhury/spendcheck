package com.parthopratim.spendcheck.common;

/** Signals that a per-user rate limit was exceeded; carries the seconds until a slot frees for {@code Retry-After}. */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
