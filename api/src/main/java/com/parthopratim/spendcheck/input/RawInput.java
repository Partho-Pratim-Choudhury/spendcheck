package com.parthopratim.spendcheck.input;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A text/voice input as received, with what the LLM made of it (never shown to other users, never logged). */
@Entity
@Table(name = "raw_inputs")
public class RawInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private RawInputType type;

    private String text;

    private String transcript;

    /** The raw {@link Extraction} as JSON; null when the LLM was not reached. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_output", columnDefinition = "jsonb")
    private String llmOutput;

    private String model;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RawInput() {
    }

    public RawInput(Long userId, RawInputType type, String text, String transcript) {
        this.userId = userId;
        this.type = type;
        this.text = text;
        this.transcript = transcript;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void setTranscript(String transcript) { this.transcript = transcript; }

    public void recordLlm(String llmOutput, String model, int latencyMs) {
        this.llmOutput = llmOutput;
        this.model = model;
        this.latencyMs = Math.max(0, latencyMs);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public RawInputType getType() { return type; }
    public String getText() { return text; }
    public String getTranscript() { return transcript; }
    public String getLlmOutput() { return llmOutput; }
    public String getModel() { return model; }
    public Integer getLatencyMs() { return latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
}
