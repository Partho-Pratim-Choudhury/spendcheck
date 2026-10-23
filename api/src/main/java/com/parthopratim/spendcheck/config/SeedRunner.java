package com.parthopratim.spendcheck.config;

import com.parthopratim.spendcheck.auth.Passwords;
import com.parthopratim.spendcheck.auth.User;
import com.parthopratim.spendcheck.auth.UserRepository;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    /**
     * A default category; also the category set the NLU eval runs against. {@code brands} are merchant names (a
     * misheard one may be snapped onto them), {@code words} generic aliases; both are seeded as aliases.
     */
    public record Seed(String name, CategoryKind kind, List<String> brands, List<String> words) {

        /** The aliases seeded for the category: brands, then words. */
        public List<String> aliases() {
            return Stream.concat(brands.stream(), words.stream()).toList();
        }
    }

    public static final List<Seed> DEFAULT_CATEGORIES = List.of(
            new Seed("Food", CategoryKind.WANT, List.of("swiggy", "zomato"), List.of("dinner", "lunch", "cafe", "chai")),
            new Seed("Groceries", CategoryKind.NEED, List.of("dmart", "blinkit", "zepto", "bigbasket"),
                    List.of("grocery")),
            new Seed("Shopping", CategoryKind.WANT, List.of("myntra", "amazon", "flipkart"),
                    List.of("clothes", "shoes")),
            new Seed("Travel", CategoryKind.NEED, List.of("uber", "ola", "rapido"),
                    List.of("metro", "auto", "petrol")),
            new Seed("Bills", CategoryKind.NEED, List.of(), List.of("electricity", "wifi", "recharge", "gas")),
            new Seed("Fun", CategoryKind.WANT, List.of("pvr", "netflix"), List.of("movie", "outing")),
            new Seed("Health", CategoryKind.NEED, List.of(), List.of("medicine", "doctor", "pharmacy", "gym")),
            new Seed("Misc", CategoryKind.WANT, List.of(), List.of()),
            new Seed("Rent", CategoryKind.FIXED, List.of(), List.of("rent")),
            new Seed("EMI", CategoryKind.FIXED, List.of(), List.of("emi")),
            new Seed("SIP", CategoryKind.FIXED, List.of(), List.of("sip", "mutual fund")));

    /** Every seeded brand alias. */
    public static final Set<String> BRANDS = DEFAULT_CATEGORIES.stream().flatMap(c -> c.brands().stream())
            .collect(Collectors.toUnmodifiableSet());

    private final AppProperties props;
    private final UserRepository users;
    private final CategorySeeder categorySeeder;
    private final PasswordEncoder passwordEncoder;

    public SeedRunner(AppProperties props, UserRepository users, CategorySeeder categorySeeder,
                      PasswordEncoder passwordEncoder) {
        this.props = props;
        this.users = users;
        this.categorySeeder = categorySeeder;
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
        categorySeeder.seedDefaults(user.getId());
        log.info("Seeded user id={} with {} categories.", user.getId(), DEFAULT_CATEGORIES.size());
    }
}
