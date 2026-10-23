package com.parthopratim.spendcheck.auth;

import com.parthopratim.spendcheck.common.ConflictException;
import com.parthopratim.spendcheck.common.FieldValidationException;
import com.parthopratim.spendcheck.common.ForbiddenException;
import com.parthopratim.spendcheck.config.AppProperties;
import com.parthopratim.spendcheck.config.CategorySeeder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Invite-gated self sign-up: validates the gate, creates the user with default categories, and signs them in. */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CategorySeeder categorySeeder;
    private final AppProperties props;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                               CategorySeeder categorySeeder, AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.categorySeeder = categorySeeder;
        this.props = props;
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        AppProperties.Signup signup = props.signup();
        if (!signup.isOpen()) {
            throw new ForbiddenException("Sign-ups are closed.");
        }
        if (!constantTimeEquals(req.inviteCode(), signup.inviteCode())) {
            throw new ForbiddenException("Invalid invite code.");
        }
        if (!Passwords.fitsBcrypt(req.password())) {
            throw new FieldValidationException("password",
                    "must be at most " + Passwords.BCRYPT_MAX_BYTES + " bytes");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists.");
        }
        User user = users.save(new User(email, passwordEncoder.encode(req.password()), req.name().trim(),
                props.tz().getId(), req.salaryDay()));
        categorySeeder.seedDefaults(user.getId());
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), UserResponse.from(user));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
