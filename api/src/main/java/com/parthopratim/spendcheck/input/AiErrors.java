package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.common.BadGatewayException;
import com.parthopratim.spendcheck.common.ServiceUnavailableException;
import java.util.Locale;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

/**
 * Maps failures of the LLM / speech-to-text providers onto the API's 502/503 contract. Spring AI's default error
 * handler reports an HTTP error as an {@code AiException} whose message is {@code "<status> - <body>"}.
 */
public final class AiErrors {

    public static final String NOT_CONFIGURED = "AI features not configured. Set OPENAI_API_KEY and restart the API.";
    public static final String NO_CREDITS = "The OpenAI account has no credits. Add credits at "
            + "platform.openai.com/settings/billing, then try again.";
    public static final String KEY_REJECTED = "AI features not configured: the OpenAI API key was rejected.";
    public static final String BUSY = "The AI service is busy right now. Please try again in a moment.";
    public static final String EXTRACT_FAILED = "Couldn't understand that right now — the AI service failed. "
            + "Please try again.";
    public static final String TRANSCRIBE_FAILED = "Couldn't transcribe the voice note. Please try again or type it.";

    private AiErrors() {
    }

    /** True when the key is missing or still the placeholder the app boots with. */
    public static boolean configured(String apiKey) {
        return apiKey != null && !apiKey.isBlank() && !apiKey.trim().equalsIgnoreCase("not-set");
    }

    /**
     * @param failureDetail the 502 detail for anything that is not a configuration/billing/rate-limit problem
     * @return the exception to throw: {@link ServiceUnavailableException} or {@link BadGatewayException}
     */
    public static RuntimeException translate(RuntimeException e, String failureDetail) {
        if (e instanceof ServiceUnavailableException || e instanceof BadGatewayException) {
            return e;
        }
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof NonTransientAiException || t instanceof TransientAiException) {
                String msg = t.getMessage() == null ? "" : t.getMessage();
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("insufficient_quota") || lower.contains("credit_balance_exhausted")
                        || lower.contains("billing_hard_limit_reached")) {
                    return new ServiceUnavailableException(NO_CREDITS);
                }
                if (msg.startsWith("401")) {
                    return new ServiceUnavailableException(KEY_REJECTED);
                }
                if (msg.startsWith("429")) {
                    return new BadGatewayException(BUSY, e);
                }
                break;
            }
        }
        return new BadGatewayException(failureDetail, e);
    }
}
