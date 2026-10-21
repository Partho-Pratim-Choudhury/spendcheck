package com.parthopratim.spendcheck.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("spendcheck")
public record AppProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue SeedUser seedUser,
        @DefaultValue Llm llm,
        @DefaultValue Stt stt,
        @DefaultValue("Asia/Kolkata") ZoneId tz) {

    public record Jwt(String secret, @DefaultValue("7d") Duration ttl) {
        public Jwt {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(
                        "spendcheck.jwt.secret is not set. Define JWT_SECRET (at least 32 bytes) in the environment or .env.");
            }
            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException(
                        "spendcheck.jwt.secret is too short for HS256: need at least 32 bytes. Use e.g. `openssl rand -base64 48`.");
            }
            if (ttl == null || ttl.isNegative() || ttl.isZero()) {
                throw new IllegalStateException("spendcheck.jwt.ttl must be a positive duration.");
            }
        }

        @Override
        public String toString() {
            return "Jwt[secret=****, ttl=" + ttl + "]";
        }
    }

    public record SeedUser(String email, String password, @DefaultValue("Partho") String name,
                           @DefaultValue("7") int salaryDay) {
        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "SeedUser[email=" + email + ", password=****, name=" + name + ", salaryDay=" + salaryDay + "]";
        }
    }

    public record Llm(String model) {}

    public record Stt(String model) {}
}
