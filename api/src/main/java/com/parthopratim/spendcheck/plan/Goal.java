package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.engine.GoalInput;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_paise", nullable = false)
    private long targetPaise;

    @Column(name = "saved_paise", nullable = false)
    private long savedPaise;

    @Column(name = "monthly_contribution_paise", nullable = false)
    private long monthlyContributionPaise;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Goal() {
    }

    public Goal(Long userId, String name, long targetPaise, long savedPaise, long monthlyContributionPaise,
                LocalDate targetDate) {
        this.userId = userId;
        this.name = name;
        this.targetPaise = targetPaise;
        this.savedPaise = savedPaise;
        this.monthlyContributionPaise = monthlyContributionPaise;
        this.targetDate = targetDate;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public GoalInput toInput() {
        return new GoalInput(id, name, monthlyContributionPaise, targetDate);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public long getTargetPaise() { return targetPaise; }
    public long getSavedPaise() { return savedPaise; }
    public long getMonthlyContributionPaise() { return monthlyContributionPaise; }
    public LocalDate getTargetDate() { return targetDate; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setTargetPaise(long targetPaise) { this.targetPaise = targetPaise; }
    public void setSavedPaise(long savedPaise) { this.savedPaise = savedPaise; }
    public void setMonthlyContributionPaise(long v) { this.monthlyContributionPaise = v; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
}
