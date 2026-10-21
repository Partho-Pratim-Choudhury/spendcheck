package com.parthopratim.spendcheck.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The business clock ("what day is it for the user") and the scheduler that runs the daily rollover. */
@Configuration
@EnableScheduling
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
