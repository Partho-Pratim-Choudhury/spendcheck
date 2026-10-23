package com.parthopratim.spendcheck;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.parthopratim.spendcheck.config.SeedRunner;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Self sign-up (invite-gated) end to end: the shared test context has sign-ups open with code {@code test-invite}. */
class RegistrationIntegrationTest extends IntegrationTestSupport {

    private static final String INVITE = "test-invite";

    @Test
    void successIssuesWorkingTokenAndSeedsDefaultCategories() throws Exception {
        String email = email("new");
        JsonNode r = readJson(register(body(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(notNullValue()))
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.name").value("New User"))
                .andExpect(jsonPath("$.user.salaryDay").value(5)));
        String token = r.get("token").asText();

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // same default category count as the env user gets at startup
        mvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(SeedRunner.DEFAULT_CATEGORIES.size())));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        String email = email("dup");
        register(body(email)).andExpect(status().isCreated());
        register(body(email))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("An account with that email already exists."));
    }

    @Test
    void wrongInviteCodeIs403() throws Exception {
        Map<String, Object> b = body(email("wc"));
        b.put("inviteCode", "not-the-code");
        register(b)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Invalid invite code."));
    }

    @Test
    void validationErrorsAre400WithFieldMap() throws Exception {
        badField("email", "not-an-email", "email");
        badField("password", "short", "password");            // 5 chars < 8
        badField("salaryDay", 0, "salaryDay");
        badField("salaryDay", 32, "salaryDay");
        badField("name", "   ", "name");
        badField("inviteCode", "", "inviteCode");
    }

    @Test
    void registeredUsersCannotSeeEachOthersData() throws Exception {
        today(LocalDate.of(2026, 10, 22));
        String tokenA = tokenFor(email("iso-a"));
        String tokenB = tokenFor(email("iso-b"));

        UUID txA = createSpend(tokenA, categoryId(tokenA, "Food"));
        UUID txB = createSpend(tokenB, categoryId(tokenB, "Food"));

        // each sees only their own
        mvc.perform(get("/api/v1/transactions?from=2026-10-22&to=2026-10-22")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(txA.toString()));
        mvc.perform(get("/api/v1/transactions?from=2026-10-22&to=2026-10-22")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(txB.toString()));

        // neither can touch the other's transaction by id
        mvc.perform(delete("/api/v1/transactions/" + txB).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/transactions/" + txA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private ResultActions register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private void badField(String field, Object value, String expectedErrorKey) throws Exception {
        Map<String, Object> b = body(email("val"));
        b.put(field, value);
        register(b)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors['" + expectedErrorKey + "']").exists());
    }

    private String tokenFor(String email) throws Exception {
        return readJson(register(body(email)).andExpect(status().isCreated())).get("token").asText();
    }

    private long categoryId(String token, String name) throws Exception {
        JsonNode list = readJson(mvc.perform(get("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
        for (JsonNode c : list) {
            if (c.get("name").asText().equals(name)) {
                return c.get("id").asLong();
            }
        }
        throw new IllegalStateException("No category " + name);
    }

    private UUID createSpend(String token, long categoryId) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> tx = new HashMap<>();
        tx.put("id", id.toString());
        tx.put("amountPaise", 10_000);
        tx.put("categoryId", categoryId);
        tx.put("spentAt", clock.instant().toString());
        tx.put("source", "MANUAL");
        mvc.perform(post("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tx)))
                .andExpect(status().isCreated());
        return id;
    }

    private static Map<String, Object> body(String email) {
        Map<String, Object> m = new HashMap<>();
        m.put("email", email);
        m.put("password", "password123");
        m.put("name", "New User");
        m.put("salaryDay", 5);
        m.put("inviteCode", INVITE);
        return m;
    }

    private static String email(String tag) {
        return tag + "-" + UUID.randomUUID() + "@test.local";
    }
}
