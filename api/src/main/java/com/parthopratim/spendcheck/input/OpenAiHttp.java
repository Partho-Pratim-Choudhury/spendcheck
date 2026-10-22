package com.parthopratim.spendcheck.input;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * HTTP plumbing shared by the OpenAI clients: bounded timeouts and at most one retry, only for a failure that came
 * back fast (a 5xx or a failed connect within {@link #RETRY_BUDGET}). A read timeout is never retried, so one call
 * costs at most about one read timeout plus a fast failure, not two full timeouts.
 */
final class OpenAiHttp {

    static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** A retry is only started if the first attempt failed within this long of the call starting. */
    static final Duration RETRY_BUDGET = Duration.ofSeconds(2);
    private static final long BACKOFF_MILLIS = 500;

    private OpenAiHttp() {
    }

    static RestClient.Builder restClient(Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    /** Spring AI's default retries 10 times with long backoff; a chat turn can't wait that long. */
    static RetryTemplate retry() {
        return retry(RETRY_BUDGET);
    }

    static RetryTemplate retry(Duration budget) {
        // 5xx (Spring AI's TransientAiException) or a connect failure anywhere in the cause chain; a read timeout
        // (HttpTimeoutException, the superclass of HttpConnectTimeoutException) is not listed, so not retried.
        SimpleRetryPolicy once = new SimpleRetryPolicy(2, Map.of(
                TransientAiException.class, true,
                ConnectException.class, true,
                HttpConnectTimeoutException.class, true), true);
        CompositeRetryPolicy policy = new CompositeRetryPolicy();
        policy.setPolicies(new RetryPolicy[] {once, new TimeoutRetryPolicy(budget.toMillis())});
        FixedBackOffPolicy backOff = new FixedBackOffPolicy();
        backOff.setBackOffPeriod(BACKOFF_MILLIS);
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(policy);
        template.setBackOffPolicy(backOff);
        return template;
    }

    static String baseUrl(String configured) {
        return configured == null || configured.isBlank() ? DEFAULT_BASE_URL : configured;
    }
}
