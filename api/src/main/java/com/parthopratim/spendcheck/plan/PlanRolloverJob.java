package com.parthopratim.spendcheck.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily at 00:05 in the configured tz: close ended plans and open the next period. */
@Component
public class PlanRolloverJob {

    private static final Logger log = LoggerFactory.getLogger(PlanRolloverJob.class);

    private final PlanService plans;

    public PlanRolloverJob(PlanService plans) {
        this.plans = plans;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "${spendcheck.tz:Asia/Kolkata}")
    public void run() {
        int rolled = plans.rolloverAllDue();
        if (rolled > 0) {
            log.info("Daily rollover: {} plan(s) rolled forward.", rolled);
        }
    }
}
