package com.parthopratim.spendcheck.common;

/** A dependency is not set up or not usable right now (rendered as 503). */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
