package com.parthopratim.spendcheck.config;

import com.parthopratim.spendcheck.auth.Passwords;
import com.parthopratim.spendcheck.auth.User;
import com.parthopratim.spendcheck.auth.UserRepository;
import com.parthopratim.spendcheck.ledger.Category;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.ledger.CategoryRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotently creates the single app user and their default categories on startup. */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private record Seed(String name, CategoryKind kind, List<String> aliases) {}

    private static final List<Seed> DEFAULT_CATEGORIES = List.of(
            new Seed("Food", CategoryKind.WANT, List.of("swiggy", "zomato", "dinner", "lunch", "cafe", "chai")),
            new Seed("Groceries", CategoryKind.NEED, List.of("dmart", "blinkit", "zepto", "bigbasket", "grocery")),
            new Seed("Shopping", CategoryKind.WANT, List.of("myntra", "amazon", "flipkart", "clothes", "shoes")),
            new Seed("Travel", CategoryKind.NEED, List.of("uber", "ola", "rapido", "metro", "auto", "petrol")),
            new Seed("Bills", CategoryKind.NEED, List.of("electricity", "wifi", "recharge", "gas")),
            new Seed("Fun", CategoryKind.WANT, List.of("movie", "pvr", "netflix", "outing")),
            new Seed("Health", CategoryKind.NEED, List.of("medicine", "doctor", "pharmacy", "gym")),
            new Seed("Misc", CategoryKind.WANT, List.of()),
            new Seed("Rent", CategoryKind.FIXED, List.of("rent")),
            new Seed("EMI", CategoryKind.FIXED, List.of("emi")),
            new Seed("SIP", CategoryKind.FIXED, List.of("sip", "mutual fund")));

    private final AppProperties props;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final PasswordEncoder passwordEncoder;

    public SeedRunner(AppProperties props, UserRepository users, CategoryRepository categories,
                      PasswordEncoder passwordEncoder) {
        this.props = props;
        this.users = users;
        this.categories = categories;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.SeedUser seed = props.seedUser();
        if (!seed.isConfigured()) {
            log.warn("APP_USER_EMAIL / APP_USER_PASSWORD not set; skipping seed user creation.");
            return;
        }
        if (!Passwords.fitsBcrypt(seed.password())) {
            throw new IllegalStateException(
                    "APP_USER_PASSWORD must be at most " + Passwords.BCRYPT_MAX_BYTES + " bytes (BCrypt limit).");
        }
        String email = seed.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            log.info("Seed user already exists; nothing to seed.");
            return;
        }
        User user = users.save(new User(email, passwordEncoder.encode(seed.password()), seed.name(),
                props.tz().getId(), seed.salaryDay()));
        for (Seed c : DEFAULT_CATEGORIES) {
            categories.save(new Category(user.getId(), c.name(), c.kind(), c.aliases()));
        }
        log.info("Seeded user id={} with {} categories.", user.getId(), DEFAULT_CATEGORIES.size());
    }
}
