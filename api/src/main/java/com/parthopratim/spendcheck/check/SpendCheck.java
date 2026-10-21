package com.parthopratim.spendcheck.check;

import com.parthopratim.spendcheck.engine.VerdictLevel;
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
import java.util.UUID;

/** A pre-spend check and what the user then did about it. */
@Entity
@Table(name = "spend_checks")
public class SpendCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "plan_id", updatable = false)
    private Long planId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private VerdictLevel verdict;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckDecision decision;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SpendCheck() {
    }

    public SpendCheck(Long userId, Long planId, long amountPaise, Long categoryId, VerdictLevel verdict,
                      Instant createdAt) {
        this.userId = userId;
        this.planId = planId;
        this.amountPaise = amountPaise;
        this.categoryId = categoryId;
        this.verdict = verdict;
        this.decision = CheckDecision.UNKNOWN;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPlanId() { return planId; }
    public long getAmountPaise() { return amountPaise; }
    public Long getCategoryId() { return categoryId; }
    public VerdictLevel getVerdict() { return verdict; }
    public CheckDecision getDecision() { return decision; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDecision(CheckDecision decision) { this.decision = decision; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
}
