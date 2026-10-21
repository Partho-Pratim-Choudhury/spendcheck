package com.parthopratim.spendcheck;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A second user can neither see nor change the first user's plan, goals or checks. */
class ScopingIntegrationTest extends IntegrationTestSupport {

    @Test
    void anotherUsersPlanGoalAndCheckAreInvisible() throws Exception {
        TestUser a = newUser(7);
        TestUser b = newUser(7);
        today(LocalDate.of(2026, 10, 22));
        long planId = putReferencePlan(a).get("id").asLong();
        long goalId = readJson(mvc.perform(asJson(a, post("/api/v1/goals"), Map.of("name", "A's goal",
                "targetPaise", 1000, "monthlyContributionPaise", 100)))).get("id").asLong();
        long checkId = readJson(mvc.perform(asJson(a, post("/api/v1/checks"),
                Map.of("amountPaise", 100, "categoryId", a.cat("Food"))))).get("checkId").asLong();

        mvc.perform(as(b, get("/api/v1/plans/current"))).andExpect(status().isNotFound());
        mvc.perform(as(b, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(b, get("/api/v1/dashboard"))).andExpect(status().isNotFound());
        mvc.perform(as(b, get("/api/v1/reports/monthly?planId=" + planId))).andExpect(status().isNotFound());
        mvc.perform(as(b, get("/api/v1/goals"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(asJson(b, patch("/api/v1/goals/" + goalId), Map.of("name", "mine now")))
                .andExpect(status().isNotFound());
        mvc.perform(as(b, delete("/api/v1/goals/" + goalId))).andExpect(status().isNotFound());
        mvc.perform(asJson(b, post("/api/v1/checks/" + checkId + "/decision"), Map.of("decision", "SKIPPED")))
                .andExpect(status().isNotFound());
        // nor use A's categories
        mvc.perform(asJson(b, put("/api/v1/plans/current"), Map.of("incomePaise", 1000, "savingsTargetPaise", 0,
                        "limits", List.of(Map.of("categoryId", a.cat("Food"), "limitPaise", 10)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['limits[0].categoryId']").exists());

        // B's own plan doesn't disturb A's
        putReferencePlan(b);
        mvc.perform(as(a, get("/api/v1/plans"))).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(planId));
        mvc.perform(as(a, get("/api/v1/goals"))).andExpect(jsonPath("$[0].name").value("A's goal"));
    }
}
