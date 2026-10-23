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
        @DefaultValue RawInputs rawInputs,
        @DefaultValue Signup signup,
        @DefaultValue AiRateLimit aiRateLimit,
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

    /**
     * @param reasoningEffort sent to reasoning models (gpt-5*, o*) only; they reject {@code temperature}, everything
     *                        else gets temperature 0
     */
    public record Llm(@DefaultValue("gpt-5.4-mini") String model,
                      @DefaultValue("0.7") double confidenceThreshold,
                      @DefaultValue("20s") Duration timeout,
                      @DefaultValue("low") String reasoningEffort) {
        public Llm {
            if (confidenceThreshold < 0 || confidenceThreshold > 1) {
                throw new IllegalStateException("spendcheck.llm.confidence-threshold must be between 0 and 1.");
            }
        }
    }

    /** @param retentionDays raw inputs (the user's own words) older than this are deleted daily */
    public record RawInputs(@DefaultValue("90") int retentionDays) {
        public RawInputs {
            if (retentionDays < 1) {
                throw new IllegalStateException("spendcheck.raw-inputs.retention-days must be at least 1.");
            }
        }
    }

    public record Stt(@DefaultValue("gpt-4o-mini-transcribe") String model,
                      @DefaultValue("20s") Duration timeout) {}

    /** Self sign-up gate. A blank {@code inviteCode} (unset {@code SIGNUP_INVITE_CODE}) closes sign-ups entirely. */
    public record Signup(@DefaultValue("") String inviteCode) {
        public boolean isOpen() {
            return inviteCode != null && !inviteCode.isBlank();
        }

        @Override
        public String toString() {
            return "Signup[inviteCode=" + (isOpen() ? "****" : "(unset)") + "]";
        }
    }

    /** Per-user cap on the Groq-calling endpoints (text, voice, pre-check), requests per minute. */
    public record AiRateLimit(@DefaultValue("15") int perMinute) {
        public AiRateLimit {
            if (perMinute < 1) {
                throw new IllegalStateException("spendcheck.ai-rate-limit.per-minute must be at least 1.");
            }
        }
    }
}
