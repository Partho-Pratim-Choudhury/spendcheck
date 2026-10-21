package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.auth.CurrentUser;
import com.parthopratim.spendcheck.auth.UserResponse;
import com.parthopratim.spendcheck.plan.PlanDtos.SalaryDayRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Changing the salary day affects future periods only; the active plan keeps its dates. */
@RestController
public class SalaryDayController {

    private final PlanService service;
    private final CurrentUser currentUser;

    public SalaryDayController(PlanService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PutMapping("/api/v1/users/me/salary-day")
    public UserResponse setSalaryDay(@Valid @RequestBody SalaryDayRequest req) {
        return service.setSalaryDay(currentUser.id(), req.salaryDay());
    }
}
