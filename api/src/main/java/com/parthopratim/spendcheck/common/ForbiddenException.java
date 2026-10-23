package com.parthopratim.spendcheck.common;

/** A business-rule refusal rendered as a 403 ProblemDetail (e.g. sign-ups closed or a bad invite code). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
