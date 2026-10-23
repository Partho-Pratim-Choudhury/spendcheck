package com.parthopratim.spendcheck;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** With no invite code configured server-side, self sign-up is closed and every attempt is a 403. */
@TestPropertySource(properties = "spendcheck.signup.invite-code=")
class SignupsClosedIntegrationTest extends IntegrationTestSupport {

    @Test
    void registerIs403WhenNoInviteCodeConfigured() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "closed-" + UUID.randomUUID() + "@test.local");
        body.put("password", "password123");
        body.put("name", "New User");
        body.put("salaryDay", 5);
        body.put("inviteCode", "anything");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Sign-ups are closed."));
    }
}
