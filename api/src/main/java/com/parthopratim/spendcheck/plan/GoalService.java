package com.parthopratim.spendcheck.plan;

import com.parthopratim.spendcheck.auth.UserTime;
import com.parthopratim.spendcheck.common.FieldValidationException;
import com.parthopratim.spendcheck.common.NotFoundException;
import com.parthopratim.spendcheck.engine.BudgetEngine;
import com.parthopratim.spendcheck.plan.GoalDtos.CreateGoalRequest;
import com.parthopratim.spendcheck.plan.GoalDtos.GoalResponse;
import com.parthopratim.spendcheck.plan.GoalDtos.UpdateGoalRequest;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private static final Comparator<Goal> PRIMARY_FIRST =
            Comparator.comparing(Goal::toInput, BudgetEngine.primaryGoalOrder());

    private final GoalRepository goals;
    private final UserTime userTime;

    public GoalService(GoalRepository goals, UserTime userTime) {
        this.goals = goals;
        this.userTime = userTime;
    }

    /** Primary goal first (earliest target date, nulls last, then lowest id). */
    @Transactional(readOnly = true)
    public List<GoalResponse> list(long userId) {
        LocalDate today = today(userId);
        return goals.findByUserId(userId).stream().sorted(PRIMARY_FIRST)
                .map(g -> GoalResponse.from(g, today)).toList();
    }

    @Transactional
    public GoalResponse create(long userId, CreateGoalRequest req) {
        Goal g = goals.saveAndFlush(new Goal(userId, req.name().trim(), req.targetPaise(),
                req.savedPaise() == null ? 0 : req.savedPaise(), req.monthlyContributionPaise(), req.targetDate()));
        return GoalResponse.from(g, today(userId));
    }

    @Transactional
    public GoalResponse update(long userId, long id, UpdateGoalRequest req) {
        Goal g = owned(userId, id);
        if (req.name() != null) {
            g.setName(req.name().trim());
        }
        if (req.targetPaise() != null) {
            g.setTargetPaise(req.targetPaise());
        }
        if (req.savedPaise() != null) {
            g.setSavedPaise(req.savedPaise());
        }
        if (req.monthlyContributionPaise() != null) {
            g.setMonthlyContributionPaise(req.monthlyContributionPaise());
        }
        if (Boolean.TRUE.equals(req.clearTargetDate())) {
            if (req.targetDate() != null) {
                throw new FieldValidationException("clearTargetDate", "must not be combined with targetDate");
            }
            g.setTargetDate(null);
        } else if (req.targetDate() != null) {
            g.setTargetDate(req.targetDate());
        }
        goals.flush();
        return GoalResponse.from(g, today(userId));
    }

    @Transactional
    public void delete(long userId, long id) {
        goals.delete(owned(userId, id));
    }

    /** The user's primary goal, if any. */
    @Transactional(readOnly = true)
    public Optional<Goal> primary(long userId) {
        return goals.findByUserId(userId).stream().min(PRIMARY_FIRST);
    }

    private Goal owned(long userId, long id) {
        return goals.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Goal " + id + " not found."));
    }

    private LocalDate today(long userId) {
        return userTime.today(userId);
    }
}
