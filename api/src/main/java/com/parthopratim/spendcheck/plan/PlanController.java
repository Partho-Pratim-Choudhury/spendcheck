package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.auth.CurrentUser;
import com.parthopratim.spendcheck.plan.PlanDtos.PlanResponse;
import com.parthopratim.spendcheck.plan.PlanDtos.PlanSummary;
import com.parthopratim.spendcheck.plan.PlanDtos.PutPlanRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService service;
    private final CurrentUser currentUser;

    public PlanController(PlanService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/current")
    public PlanResponse current() {
        return service.current(currentUser.id());
    }

    @PutMapping("/current")
    public PlanResponse put(@Valid @RequestBody PutPlanRequest req) {
        return service.put(currentUser.id(), req);
    }

    @PostMapping("/rollover")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse rollover() {
        return service.rollover(currentUser.id());
    }

    @GetMapping
    public List<PlanSummary> list() {
        return service.list(currentUser.id());
    }
}
