package com.parthopratim.spendcheck;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parthopratim.spendcheck.input.Extraction;
import com.parthopratim.spendcheck.input.InputIntent;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** The per-user AI rate limit (set to 2/min here) gates the Groq-calling endpoints but nothing else. */
@TestPropertySource(properties = "spendcheck.ai-rate-limit.per-minute=2")
class RateLimitIntegrationTest extends IntegrationTestSupport {

    @Test
    void thirdAiCallInAMinuteIs429WithRetryAfter() throws Exception {
        extractor.respond(other());
        TestUser u = newUser(7);

        text(u).andExpect(status().isOk());
        text(u).andExpect(status().isOk());
        text(u)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Too many requests — give it a moment."));
    }

    @Test
    void theLimitIsPerUser() throws Exception {
        extractor.respond(other());
        TestUser u = newUser(7);
        TestUser v = newUser(7);

        text(u).andExpect(status().isOk());
        text(u).andExpect(status().isOk());
        text(u).andExpect(status().isTooManyRequests());

        // a different user has their own budget
        text(v).andExpect(status().isOk());
        text(v).andExpect(status().isOk());
    }

    @Test
    void readEndpointsAreNeverRateLimited() throws Exception {
        extractor.respond(other());
        TestUser u = newUser(7);

        text(u).andExpect(status().isOk());
        text(u).andExpect(status().isOk());
        text(u).andExpect(status().isTooManyRequests());   // u is now over the AI limit

        for (int i = 0; i < 5; i++) {
            mvc.perform(as(u, get("/api/v1/categories"))).andExpect(status().isOk());
            mvc.perform(as(u, get("/api/v1/transactions"))).andExpect(status().isOk());
        }
    }

    private ResultActions text(TestUser u) throws Exception {
        return mvc.perform(asJson(u, post("/api/v1/inputs/text"), Map.of("text", "hi")));
    }

    private static Extraction other() {
        return new Extraction(InputIntent.OTHER, null, null, null, null, null, null, null, null, 0.95);
    }
}
