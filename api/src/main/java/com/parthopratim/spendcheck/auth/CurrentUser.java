package com.parthopratim.spendcheck.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves the authenticated user's id from the JWT {@code sub} claim. */
@Component
public class CurrentUser {

    public long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw new IllegalStateException("No authenticated user in security context");
    }
}
