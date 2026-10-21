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

/** A text/voice input as received. Minimal for now; the NLU pipeline adds the LLM fields. */
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

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public RawInputType getType() { return type; }
    public String getText() { return text; }
    public String getTranscript() { return transcript; }
    public Instant getCreatedAt() { return createdAt; }
}
