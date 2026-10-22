package com.parthopratim.spendcheck.common;

/** 409: the operation needs an ACTIVE budget plan and the user has none. */
public class NoActivePlanException extends ConflictException {
    public NoActivePlanException(String message) {
        super(message);
    }
}
