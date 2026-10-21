package com.parthopratim.spendcheck.check;

import com.parthopratim.spendcheck.auth.CurrentUser;
import com.parthopratim.spendcheck.check.CheckDtos.CreateCheckRequest;
import com.parthopratim.spendcheck.check.CheckDtos.DecisionRequest;
import com.parthopratim.spendcheck.check.CheckDtos.DecisionResponse;
import com.parthopratim.spendcheck.check.CheckDtos.VerdictResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checks")
public class CheckController {

    private final CheckService service;
    private final CurrentUser currentUser;

    public CheckController(CheckService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VerdictResponse check(@Valid @RequestBody CreateCheckRequest req) {
        return service.check(currentUser.id(), req.amountPaise(), req.categoryId());
    }

    @PostMapping("/{id}/decision")
    public DecisionResponse decide(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        return service.decide(currentUser.id(), id, req);
    }
}
