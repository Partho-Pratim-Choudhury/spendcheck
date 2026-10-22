package com.parthopratim.spendcheck.auth;

import com.parthopratim.spendcheck.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** "Now" and "today" from the user's point of view (their tz, falling back to the configured one). */
@Component
public class UserTime {

    private static final Logger log = LoggerFactory.getLogger(UserTime.class);

    private final Clock clock;
    private final UserRepository users;
    private final AppProperties props;

    public UserTime(Clock clock, UserRepository users, AppProperties props) {
        this.clock = clock;
        this.users = users;
        this.props = props;
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone(long userId) {
        return users.findById(userId).map(this::zone).orElse(props.tz());
    }

    public ZoneId zone(User user) {
        try {
            return ZoneId.of(user.getTz());
        } catch (RuntimeException e) {
            log.warn("User {} has invalid tz '{}'; falling back to {}", user.getId(), user.getTz(), props.tz());
            return props.tz();
        }
    }

    public LocalDate today(long userId) {
        return today(zone(userId));
    }

    public LocalDate today(User user) {
        return LocalDate.ofInstant(clock.instant(), zone(user));
    }

    public LocalDate today(ZoneId zone) {
        return LocalDate.ofInstant(clock.instant(), zone);
    }
}
