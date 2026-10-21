package com.parthopratim.spendcheck.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "transactions")
public class Transaction implements Persistable<UUID> {

    /** Client-generated; makes create idempotent. */
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    private String merchant;

    private String note;

    @Column(name = "spent_at", nullable = false)
    private Instant spentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "need_or_want")
    private NeedOrWant needOrWant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "raw_input_id")
    private Long rawInputId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Id is client-assigned, so tell Spring Data a new instance needs persist(), not merge(). */
    @Transient
    private boolean isNew = true;

    protected Transaction() {
    }

    public Transaction(UUID id, Long userId, long amountPaise, Category category, String merchant, String note,
                       Instant spentAt, NeedOrWant needOrWant, TransactionSource source, TransactionStatus status) {
        this.id = id;
        this.userId = userId;
        this.amountPaise = amountPaise;
        this.category = category;
        this.merchant = merchant;
        this.note = note;
        this.spentAt = spentAt;
        this.needOrWant = needOrWant;
        this.source = source;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPlanId() { return planId; }
    public long getAmountPaise() { return amountPaise; }
    public Category getCategory() { return category; }
    public String getMerchant() { return merchant; }
    public String getNote() { return note; }
    public Instant getSpentAt() { return spentAt; }
    public NeedOrWant getNeedOrWant() { return needOrWant; }
    public TransactionSource getSource() { return source; }
    public TransactionStatus getStatus() { return status; }
    public Long getRawInputId() { return rawInputId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public void setCategory(Category category) { this.category = category; }
    public void setMerchant(String merchant) { this.merchant = merchant; }
    public void setNote(String note) { this.note = note; }
    public void setSpentAt(Instant spentAt) { this.spentAt = spentAt; }
    public void setNeedOrWant(NeedOrWant needOrWant) { this.needOrWant = needOrWant; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public void setRawInputId(Long rawInputId) { this.rawInputId = rawInputId; }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
