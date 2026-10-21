package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.auth.CurrentUser;
import com.parthopratim.spendcheck.plan.GoalDtos.CreateGoalRequest;
import com.parthopratim.spendcheck.plan.GoalDtos.GoalResponse;
import com.parthopratim.spendcheck.plan.GoalDtos.UpdateGoalRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService service;
    private final CurrentUser currentUser;

    public GoalController(GoalService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<GoalResponse> list() {
        return service.list(currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody CreateGoalRequest req) {
        return service.create(currentUser.id(), req);
    }

    @PatchMapping("/{id}")
    public GoalResponse update(@PathVariable long id, @Valid @RequestBody UpdateGoalRequest req) {
        return service.update(currentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(currentUser.id(), id);
    }
}
