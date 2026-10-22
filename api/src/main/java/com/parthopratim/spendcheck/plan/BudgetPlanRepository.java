package com.parthopratim.spendcheck.plan;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {

    @EntityGraph(attributePaths = {"limits", "limits.category"})
    Optional<BudgetPlan> findByUserIdAndStatus(Long userId, PlanStatus status);

    @EntityGraph(attributePaths = {"limits", "limits.category"})
    Optional<BudgetPlan> findByIdAndUserId(Long id, Long userId);

    // no fetch graph: a collection fetch with a row limit would be paginated in memory
    Optional<BudgetPlan> findFirstByUserIdOrderByPeriodStartDescIdDesc(Long userId);

    List<BudgetPlan> findByUserIdOrderByPeriodStartDescIdDesc(Long userId);

    // no fetch graph: a collection fetch with a row limit would be paginated in memory
    List<BudgetPlan> findTop6ByUserIdAndPeriodStartLessThanEqualOrderByPeriodStartDescIdDesc(Long userId,
                                                                                         LocalDate start);

    /** Plans whose period contains {@code date}, ACTIVE first, then newest. */
    @EntityGraph(attributePaths = {"limits", "limits.category"})
    @Query("""
            select p from BudgetPlan p
            where p.userId = :userId and p.periodStart <= :date and p.periodEnd >= :date
            order by case when p.status = com.parthopratim.spendcheck.plan.PlanStatus.ACTIVE then 0 else 1 end,
                     p.periodStart desc, p.id desc""")
    List<BudgetPlan> findCovering(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** Ids of the plans (any status) whose period contains {@code date}, ACTIVE first, then newest. */
    @Query("""
            select p.id from BudgetPlan p
            where p.userId = :userId and p.periodStart <= :date and p.periodEnd >= :date
            order by case when p.status = com.parthopratim.spendcheck.plan.PlanStatus.ACTIVE then 0 else 1 end,
                     p.periodStart desc, p.id desc""")
    List<Long> findIdsCovering(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** The plans with their limits and limit categories in one query (no row limit, so no in-memory paging). */
    @EntityGraph(attributePaths = {"limits", "limits.category"})
    List<BudgetPlan> findWithLimitsByIdIn(Collection<Long> ids);

    /** Period end of the user's ACTIVE plan — a cheap staleness probe that loads no entity. */
    @Query("select p.periodEnd from BudgetPlan p where p.userId = :userId and p.status = :status")
    Optional<LocalDate> findPeriodEndByUserIdAndStatus(@Param("userId") Long userId,
                                                       @Param("status") PlanStatus status);

    /** Users whose ACTIVE plan ended before {@code date}. */
    @Query("""
            select distinct p.userId from BudgetPlan p
            where p.status = com.parthopratim.spendcheck.plan.PlanStatus.ACTIVE and p.periodEnd < :date""")
    List<Long> findUserIdsWithActivePlanEndingBefore(@Param("date") LocalDate date);
}
