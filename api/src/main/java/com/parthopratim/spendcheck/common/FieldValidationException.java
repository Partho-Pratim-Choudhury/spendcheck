package com.parthopratim.spendcheck.common;

import java.util.Map;

/** A business-rule validation failure on specific request fields; rendered like bean-validation errors. */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public FieldValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public FieldValidationException(Map<String, String> errors) {
        super("Validation failed: " + errors.keySet());
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
