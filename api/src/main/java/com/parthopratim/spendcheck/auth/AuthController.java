package com.parthopratim.spendcheck.auth;

import com.parthopratim.spendcheck.common.NotFoundException;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    /** Compared against when the email is unknown, so response timing doesn't reveal which emails exist. */
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          CurrentUser currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        // Too long can never match a stored hash, and the encoder would throw on it.
        boolean ok = Passwords.fitsBcrypt(req.password()) && passwordEncoder.matches(req.password(), hash);
        if (user == null || !ok) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return users.findById(currentUser.id())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }
}
