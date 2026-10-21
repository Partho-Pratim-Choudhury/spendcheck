package com.parthopratim.spendcheck.auth;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, UserResponse user) {

    @Override
    public String toString() {
        return "LoginResponse[token=****, expiresAt=" + expiresAt + ", user=" + user + "]";
    }
}
