package com.parthopratim.spendcheck.common;

/** An upstream call (LLM, speech-to-text) failed (rendered as 502). */
public class BadGatewayException extends RuntimeException {
    public BadGatewayException(String message) {
        super(message);
    }

    public BadGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
