package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily at 03:15 in the configured tz: deletes raw inputs (the user's typed text and transcripts) older than
 * {@code spendcheck.raw-inputs.retention-days}. Transactions keep existing; their link to the input is nulled.
 */
@Component
public class RawInputPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RawInputPurgeJob.class);

    private final RawInputRepository rawInputs;
    private final Clock clock;
    private final AppProperties props;

    public RawInputPurgeJob(RawInputRepository rawInputs, Clock clock, AppProperties props) {
        this.rawInputs = rawInputs;
        this.clock = clock;
        this.props = props;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "${spendcheck.tz:Asia/Kolkata}")
    public void run() {
        purge();
    }

    /** @return how many raw inputs were deleted */
    public int purge() {
        int deleted = rawInputs.deleteCreatedBefore(
                clock.instant().minus(Duration.ofDays(props.rawInputs().retentionDays())));
        if (deleted > 0) {
            log.info("Purged {} raw input(s) older than {} days.", deleted, props.rawInputs().retentionDays());
        }
        return deleted;
    }
}
