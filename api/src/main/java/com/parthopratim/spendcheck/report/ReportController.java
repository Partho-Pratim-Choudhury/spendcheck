package com.parthopratim.spendcheck.report;

import com.parthopratim.spendcheck.auth.CurrentUser;
import com.parthopratim.spendcheck.report.ReportDtos.DailySummary;
import com.parthopratim.spendcheck.report.ReportDtos.DashboardResponse;
import com.parthopratim.spendcheck.report.ReportDtos.MonthlyReport;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService service;
    private final CurrentUser currentUser;

    public ReportController(ReportService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return service.dashboard(currentUser.id());
    }

    @GetMapping("/reports/monthly")
    public MonthlyReport monthly(@RequestParam(required = false) Long planId) {
        return service.monthly(currentUser.id(), planId);
    }

    @GetMapping("/summary/daily")
    public DailySummary daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(currentUser.id(), date);
    }
}
