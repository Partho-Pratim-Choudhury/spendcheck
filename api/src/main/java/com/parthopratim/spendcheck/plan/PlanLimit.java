package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.ledger.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_limits")
public class PlanLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private BudgetPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    @Column(name = "limit_paise", nullable = false)
    private long limitPaise;

    protected PlanLimit() {
    }

    PlanLimit(BudgetPlan plan, Category category, long limitPaise) {
        this.plan = plan;
        this.category = category;
        this.limitPaise = limitPaise;
    }

    public Long getId() { return id; }
    public BudgetPlan getPlan() { return plan; }
    public Category getCategory() { return category; }
    public long getLimitPaise() { return limitPaise; }

    public void setLimitPaise(long limitPaise) { this.limitPaise = limitPaise; }
}
