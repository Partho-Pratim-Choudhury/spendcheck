package com.parthopratim.spendcheck;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parthopratim.spendcheck.auth.JwtService;
import com.parthopratim.spendcheck.auth.User;
import com.parthopratim.spendcheck.auth.UserRepository;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.ledger.CategoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared base for plan/check/report integration tests: one Spring context (and Postgres container) with a
 * settable clock. Each test makes its own users, so tests never see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, IntegrationTestSupport.ClockOverride.class, ScriptedAi.class})
public abstract class IntegrationTestSupport {

    protected static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    protected static final long RUPEE = 100;

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockOverride {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected CategoryRepository categories;
    @Autowired protected JwtService jwtService;
    @Autowired protected MutableClock clock;
    @Autowired protected ScriptedAi.ScriptedExtractor extractor;
    @Autowired protected ScriptedAi.ScriptedSpeechToText speechToText;

    protected record TestUser(User user, String token, Map<String, Long> cats) {
        public long id() {
            return user.getId();
        }

        public long cat(String name) {
            Long id = cats.get(name);
            if (id == null) {
                throw new IllegalArgumentException("No category " + name);
            }
            return id;
        }
    }

    @AfterEach
    void resetClock() {
        clock.reset();
        extractor.reset();
        speechToText.reset();
    }

    /** Sets "now" to noon IST on {@code date}. */
    protected void today(LocalDate date) {
        clock.set(date.atTime(LocalTime.NOON).atZone(IST).toInstant());
    }

    protected TestUser newUser(int salaryDay) {
        User u = users.save(new User("it-" + UUID.randomUUID() + "@test.local", "{noop}unused", "Tester",
                IST.getId(), salaryDay));
        Map<String, Long> cats = new HashMap<>();
        Object[][] seed = {
                {"Food", CategoryKind.WANT}, {"Groceries", CategoryKind.NEED}, {"Shopping", CategoryKind.WANT},
                {"Misc", CategoryKind.WANT}, {"Rent", CategoryKind.FIXED}, {"EMI", CategoryKind.FIXED},
                {"SIP", CategoryKind.FIXED}, {"Old", CategoryKind.WANT}};
        for (Object[] s : seed) {
            Category c = new Category(u.getId(), (String) s[0], (CategoryKind) s[1], List.of());
            if (s[0].equals("Old")) {
                c.setArchived(true);
            }
            cats.put((String) s[0], categories.save(c).getId());
        }
        return new TestUser(u, jwtService.issue(u).token(), cats);
    }

    protected MockHttpServletRequestBuilder as(TestUser u, MockHttpServletRequestBuilder b) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token());
    }

    protected MockHttpServletRequestBuilder asJson(TestUser u, MockHttpServletRequestBuilder b, Object body)
            throws Exception {
        return as(u, b).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    /** Income ₹1,20,000; rent 25,000 + EMI 15,000 + SIP 10,000; savings 20,000; Shopping 7,000, Food 8,000, Groceries 10,000. */
    protected JsonNode putReferencePlan(TestUser u) throws Exception {
        Map<String, Long> limits = new LinkedHashMap<>();
        limits.put("Rent", 25_000 * RUPEE);
        limits.put("EMI", 15_000 * RUPEE);
        limits.put("SIP", 10_000 * RUPEE);
        limits.put("Shopping", 7_000 * RUPEE);
        limits.put("Food", 8_000 * RUPEE);
        limits.put("Groceries", 10_000 * RUPEE);
        return readJson(mvc.perform(asJson(u, put("/api/v1/plans/current"),
                        planBody(u, 120_000 * RUPEE, 20_000 * RUPEE, limits)))
                .andExpect(status().isOk()));
    }

    protected Map<String, Object> planBody(TestUser u, long income, long savings, Map<String, Long> limits) {
        List<Map<String, Object>> rows = new ArrayList<>();
        limits.forEach((name, paise) -> rows.add(Map.of("categoryId", u.cat(name), "limitPaise", paise)));
        return Map.of("incomePaise", income, "savingsTargetPaise", savings, "limits", rows);
    }

    /** Creates a confirmed spend at 12:00 IST on {@code date}; returns its id. */
    protected UUID spend(TestUser u, long paise, String category, LocalDate date, String merchant, String needOrWant)
            throws Exception {
        return spendAt(u, paise, category, date.atTime(LocalTime.NOON).atZone(IST).toInstant(), merchant,
                needOrWant);
    }

    protected UUID spendAt(TestUser u, long paise, String category, Instant spentAt, String merchant,
                           String needOrWant) throws Exception {
        return spendVia(u, "MANUAL", paise, category, spentAt, merchant, needOrWant);
    }

    /** Like {@link #spendAt} with a given source (TEXT, VOICE or MANUAL). */
    protected UUID spendVia(TestUser u, String source, long paise, String category, Instant spentAt, String merchant,
                            String needOrWant) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        body.put("amountPaise", paise);
        body.put("categoryId", u.cat(category));
        body.put("spentAt", spentAt.toString());
        body.put("source", source);
        if (merchant != null) {
            body.put("merchant", merchant);
        }
        if (needOrWant != null) {
            body.put("needOrWant", needOrWant);
        }
        mvc.perform(asJson(u, post("/api/v1/transactions"), body)).andExpect(status().isCreated());
        return id;
    }

    protected JsonNode readJson(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
