package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.engine.Period;
import com.parthopratim.spendcheck.ledger.Category;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "budget_plans")
public class BudgetPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "income_paise", nullable = false)
    private long incomePaise;

    @Column(name = "savings_target_paise", nullable = false)
    private long savingsTargetPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PlanLimit> limits = new ArrayList<>();

    protected BudgetPlan() {
    }

    public BudgetPlan(Long userId, Period period, long incomePaise, long savingsTargetPaise) {
        this.userId = userId;
        this.periodStart = period.start();
        this.periodEnd = period.end();
        this.incomePaise = incomePaise;
        this.savingsTargetPaise = savingsTargetPaise;
        this.status = PlanStatus.ACTIVE;
    }

    /**
     * A new ACTIVE plan for {@code period} with the same income, savings target and limits, minus the limits of
     * categories archived since (a plan may not budget an archived category).
     */
    public static BudgetPlan copyOf(BudgetPlan source, Period period) {
        BudgetPlan p = new BudgetPlan(source.userId, period, source.incomePaise, source.savingsTargetPaise);
        for (PlanLimit l : source.limits) {
            if (!l.getCategory().isArchived()) {
                p.addLimit(l.getCategory(), l.getLimitPaise());
            }
        }
        return p;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void addLimit(Category category, long limitPaise) {
        limits.add(new PlanLimit(this, category, limitPaise));
    }

    public Period period() {
        return new Period(periodStart, periodEnd);
    }

    public void close() {
        this.status = PlanStatus.CLOSED;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public long getIncomePaise() { return incomePaise; }
    public long getSavingsTargetPaise() { return savingsTargetPaise; }
    public PlanStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PlanLimit> getLimits() { return limits; }

    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public void setIncomePaise(long incomePaise) { this.incomePaise = incomePaise; }
    public void setSavingsTargetPaise(long savingsTargetPaise) { this.savingsTargetPaise = savingsTargetPaise; }
}
