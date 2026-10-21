package com.parthopratim.spendcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parthopratim.spendcheck.auth.User;
import com.parthopratim.spendcheck.auth.UserRepository;
import com.parthopratim.spendcheck.ledger.TransactionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ApiIntegrationTest {

    private static final String SEED_EMAIL = "partho@test.local";
    private static final String SEED_PASSWORD = "test-password";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired TransactionRepository transactions;
    @Autowired PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = loginAs(SEED_EMAIL, SEED_PASSWORD);
    }

    // ---------- auth ----------

    @Test
    void loginReturnsTokenExpiryAndUser() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", SEED_EMAIL, "password", SEED_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(notNullValue()))
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.email").value(SEED_EMAIL))
                .andExpect(jsonPath("$.user.name").value("Partho"))
                .andExpect(jsonPath("$.user.salaryDay").value(7));
    }

    @Test
    void loginWithWrongPasswordIs401Problem() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", SEED_EMAIL, "password", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "nobody@test.local", "password", "x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithPasswordBeyondBcryptLimitIs401NotError() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", SEED_EMAIL, "password", "x".repeat(80)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestsAre401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        mvc.perform(authed(get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(SEED_EMAIL))
                .andExpect(jsonPath("$.salaryDay").value(7));
    }

    // ---------- categories ----------

    @Test
    void categoriesListReturnsSeeded() throws Exception {
        mvc.perform(authed(get("/api/v1/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Food")))
                .andExpect(jsonPath("$[*].name", hasItem("SIP")))
                .andExpect(jsonPath("$[?(@.name == 'Food')].kind").value("WANT"))
                .andExpect(jsonPath("$[?(@.name == 'Rent')].kind").value("FIXED"))
                .andExpect(jsonPath("$[?(@.name == 'Food')].aliases[*]",
                        containsInAnyOrder("swiggy", "zomato", "dinner", "lunch", "cafe", "chai")))
                .andExpect(jsonPath("$[?(@.name == 'Food')].archived").value(false));
    }

    @Test
    void categoryCreateDuplicatePatchAndArchive() throws Exception {
        String name = "Pets" + UUID.randomUUID().toString().substring(0, 6);
        JsonNode created = readJson(mvc.perform(authed(post("/api/v1/categories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "kind", "NEED", "aliases", new String[] {" Vet ", "vet"}))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aliases", containsInAnyOrder("vet"))));
        long id = created.get("id").asLong();

        mvc.perform(authed(post("/api/v1/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name.toUpperCase(), "kind", "WANT"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mvc.perform(authed(patch("/api/v1/categories/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("kind", "WANT", "archived", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("WANT"))
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.name").value(name));

        mvc.perform(authed(get("/api/v1/categories")))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        mvc.perform(authed(get("/api/v1/categories?includeArchived=true")))
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));

        // archived category can't take new transactions
        mvc.perform(createTx(UUID.randomUUID(), 100, id, Instant.now()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").exists());
    }

    // ---------- transactions ----------

    @Test
    void createIsIdempotentOnClientId() throws Exception {
        long food = categoryId("Food");
        UUID id = UUID.randomUUID();
        mvc.perform(createTx(id, 45000, food, Instant.now().minusSeconds(60)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.amountPaise").value(45000))
                .andExpect(jsonPath("$.categoryId").value(food))
                .andExpect(jsonPath("$.categoryName").value("Food"))
                .andExpect(jsonPath("$.merchant").value("Swiggy"))
                .andExpect(jsonPath("$.needOrWant").value("WANT"))
                .andExpect(jsonPath("$.source").value("TEXT"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());

        long before = transactions.count();
        mvc.perform(createTx(id, 99999, food, Instant.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.amountPaise").value(45000));
        assertThat(transactions.count()).isEqualTo(before);
    }

    @Test
    void sameIdOwnedByAnotherUserIs409AndForeignResourcesAre404() throws Exception {
        String otherEmail = "other-" + UUID.randomUUID() + "@test.local";
        User other = users.save(new User(otherEmail, passwordEncoder.encode("pw-other"), "Other",
                "Asia/Kolkata", 1));
        // give the other user a category of their own
        String otherToken = loginAs(otherEmail, "pw-other");
        JsonNode cat = readJson(mvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Food", "kind", "WANT"))))
                .andExpect(status().isCreated()));

        UUID id = UUID.randomUUID();
        mvc.perform(createTx(id, 1000, categoryId("Food"), Instant.now())).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody(id, 1000, cat.get("id").asLong(), Instant.now())))
                .andExpect(status().isConflict());

        // the other user can't see, edit or delete it
        mvc.perform(patch("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":5}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/categories/" + categoryId("Food"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                .andExpect(status().isNotFound());
        // nor use my category
        mvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody(UUID.randomUUID(), 1000, categoryId("Food"), Instant.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").exists());
        assertThat(other.getId()).isNotNull();
    }

    @Test
    void patchUpdatesFields() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(createTx(id, 45000, categoryId("Food"), Instant.now().minusSeconds(3600)))
                .andExpect(status().isCreated());
        long travel = categoryId("Travel");
        mvc.perform(authed(patch("/api/v1/transactions/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("amountPaise", 30000, "categoryId", travel, "merchant", "Uber",
                                "needOrWant", "NEED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaise").value(30000))
                .andExpect(jsonPath("$.categoryId").value(travel))
                .andExpect(jsonPath("$.categoryName").value("Travel"))
                .andExpect(jsonPath("$.merchant").value("Uber"))
                .andExpect(jsonPath("$.needOrWant").value("NEED"))
                .andExpect(jsonPath("$.note").value("test"));

        mvc.perform(authed(patch("/api/v1/transactions/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHidesFromList() throws Exception {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2025-03-10T06:00:00Z");
        mvc.perform(createTx(id, 500, categoryId("Fun"), at)).andExpect(status().isCreated());
        mvc.perform(authed(get("/api/v1/transactions?from=2025-03-10&to=2025-03-10")))
                .andExpect(jsonPath("$[*].id", hasItem(id.toString())));

        mvc.perform(authed(delete("/api/v1/transactions/" + id))).andExpect(status().isNoContent());
        mvc.perform(authed(delete("/api/v1/transactions/" + id))).andExpect(status().isNoContent());

        mvc.perform(authed(get("/api/v1/transactions?from=2025-03-10&to=2025-03-10")))
                .andExpect(jsonPath("$[*].id", not(hasItem(id.toString()))));
        assertThat(transactions.findById(id)).get()
                .extracting(t -> t.getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    void listFiltersByDateInUserTimezoneAndCategory() throws Exception {
        long food = categoryId("Food");
        long groceries = categoryId("Groceries");
        // IST = UTC+05:30. 2025-01-14T19:00Z = 2025-01-15 00:30 IST (inside the 15th).
        UUID a = UUID.randomUUID(); // Jan 15 IST, food
        UUID b = UUID.randomUUID(); // Jan 16 IST late evening, groceries
        UUID c = UUID.randomUUID(); // Jan 14 IST 23:59, outside
        UUID d = UUID.randomUUID(); // Jan 17 IST 00:00, outside
        mvc.perform(createTx(a, 100, food, Instant.parse("2025-01-14T19:00:00Z"))).andExpect(status().isCreated());
        mvc.perform(createTx(b, 200, groceries, Instant.parse("2025-01-16T18:00:00Z"))).andExpect(status().isCreated());
        mvc.perform(createTx(c, 300, food, Instant.parse("2025-01-14T18:29:00Z"))).andExpect(status().isCreated());
        mvc.perform(createTx(d, 400, food, Instant.parse("2025-01-16T18:30:00Z"))).andExpect(status().isCreated());

        mvc.perform(authed(get("/api/v1/transactions?from=2025-01-15&to=2025-01-16")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(b.toString())) // newest first
                .andExpect(jsonPath("$[1].id").value(a.toString()));

        mvc.perform(authed(get("/api/v1/transactions?from=2025-01-15&to=2025-01-16&categoryId=" + food)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(a.toString()));

        mvc.perform(authed(get("/api/v1/transactions?from=2025-01-14&to=2025-01-17&needOrWant=WANT")))
                .andExpect(jsonPath("$", hasSize(4)));

        mvc.perform(authed(get("/api/v1/transactions?from=2025-01-17&to=2025-01-15")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.from").exists());
    }

    @Test
    void validationErrorsUseProblemDetailWithFieldMap() throws Exception {
        mvc.perform(authed(post("/api/v1/transactions")).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("id", UUID.randomUUID().toString(), "amountPaise", 0,
                                "spentAt", Instant.now().toString(), "source", "MANUAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.errors.amountPaise").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists());

        mvc.perform(createTx(UUID.randomUUID(), 100_000_001L, categoryId("Food"), Instant.now()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountPaise").exists());

        mvc.perform(createTx(UUID.randomUUID(), 100, categoryId("Food"), Instant.now().plusSeconds(2 * 86400)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.spentAt").exists());

        mvc.perform(createTx(UUID.randomUUID(), 100, 999_999L, Instant.now()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categoryId").exists());

        mvc.perform(authed(post("/api/v1/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"kind\":\"WANT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ---------- helpers ----------

    private String loginAs(String email, String password) throws Exception {
        return readJson(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()))
                .get("token").asText();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder b) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder createTx(UUID id, long amount, long categoryId, Instant spentAt)
            throws Exception {
        return authed(post("/api/v1/transactions")).contentType(MediaType.APPLICATION_JSON)
                .content(txBody(id, amount, categoryId, spentAt));
    }

    private String txBody(UUID id, long amount, long categoryId, Instant spentAt) throws Exception {
        return body(Map.of("id", id.toString(), "amountPaise", amount, "categoryId", categoryId,
                "merchant", "Swiggy", "note", "test", "spentAt", spentAt.toString(),
                "needOrWant", "WANT", "source", "TEXT"));
    }

    private long categoryId(String name) throws Exception {
        JsonNode list = readJson(mvc.perform(authed(get("/api/v1/categories?includeArchived=true"))));
        for (JsonNode c : list) {
            if (c.get("name").asText().equals(name)) {
                return c.get("id").asLong();
            }
        }
        throw new IllegalStateException("No category " + name);
    }

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    private JsonNode readJson(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
