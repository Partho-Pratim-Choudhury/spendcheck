package com.parthopratim.spendcheck.input;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/** HTTP plumbing shared by the OpenAI clients: bounded timeouts and at most one retry on 5xx. */
final class OpenAiHttp {

    static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

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
        return RetryTemplate.builder()
                .maxAttempts(2)
                .retryOn(TransientAiException.class)
                .fixedBackoff(500)
                .build();
    }

    static String baseUrl(String configured) {
        return configured == null || configured.isBlank() ? DEFAULT_BASE_URL : configured;
    }
}
