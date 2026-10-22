package com.parthopratim.spendcheck.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;

/** What the WARN logs may contain: never the provider's body or the row's values. */
class GlobalExceptionHandlerTest {

    @Test
    void upstreamSummaryHasStatusAndCodeOnly() {
        String body = "429 - {\"error\":{\"message\":\"You said: swiggy 450 for my secret\","
                + "\"type\":\"insufficient_quota\",\"code\":\"insufficient_quota\"}}";
        assertThat(GlobalExceptionHandler.upstreamSummary(new NonTransientAiException(body)))
                .isEqualTo("NonTransientAiException status=429 code=insufficient_quota");
        assertThat(GlobalExceptionHandler.upstreamSummary(new RuntimeException("wrap",
                new TransientAiException("503 - <html>upstream says: swiggy 450</html>"))))
                .isEqualTo("TransientAiException status=503");
        assertThat(GlobalExceptionHandler.upstreamSummary(new ResourceAccessException("I/O error on POST "
                + "request for https://api.openai.com", new java.net.http.HttpTimeoutException("timed out"))))
                .isEqualTo("ResourceAccessException caused by HttpTimeoutException");
        assertThat(GlobalExceptionHandler.upstreamSummary(null)).isEqualTo("unknown");
    }

    @Test
    void constraintNameOnly() {
        SQLException sql = new SQLException("ERROR: duplicate key value violates unique constraint "
                + "\"uq_x\"  Detail: Key (email)=(someone@example.com) already exists.");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute",
                new ConstraintViolationException("could not execute", sql, "uq_x"));
        assertThat(GlobalExceptionHandler.constraintName(ex)).isEqualTo("uq_x");
        assertThat(GlobalExceptionHandler.constraintName(new DataIntegrityViolationException("x", sql)))
                .isEqualTo("unknown");
    }
}
