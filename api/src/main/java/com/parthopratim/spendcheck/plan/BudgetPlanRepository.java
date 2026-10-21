package com.parthopratim.spendcheck.plan;

import java.time.LocalDate;
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

    /** Id of the ACTIVE plan if its period contains {@code date}. */
    @Query("""
            select p.id from BudgetPlan p
            where p.userId = :userId and p.status = com.parthopratim.spendcheck.plan.PlanStatus.ACTIVE
              and p.periodStart <= :date and p.periodEnd >= :date""")
    Optional<Long> findActiveIdCovering(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** Period end of the user's ACTIVE plan — a cheap staleness probe that loads no entity. */
    @Query("select p.periodEnd from BudgetPlan p where p.userId = :userId and p.status = :status")
    Optional<LocalDate> findPeriodEndByUserIdAndStatus(@Param("userId") Long userId,
                                                       @Param("status") PlanStatus status);

    @Query("select distinct p.userId from BudgetPlan p where p.status = :status")
    List<Long> findUserIdsWithStatus(@Param("status") PlanStatus status);
}
