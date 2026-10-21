package com.parthopratim.spendcheck.check;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpendCheckRepository extends JpaRepository<SpendCheck, Long> {

    Optional<SpendCheck> findByIdAndUserId(Long id, Long userId);

    /** Count and total of the user's SKIPPED checks made under a plan. */
    @Query("""
            select new com.parthopratim.spendcheck.check.SkippedTotals(count(c), coalesce(sum(c.amountPaise), 0))
            from SpendCheck c
            where c.userId = :userId and c.planId = :planId
              and c.decision = com.parthopratim.spendcheck.check.CheckDecision.SKIPPED""")
    SkippedTotals skippedInPlan(@Param("userId") Long userId, @Param("planId") Long planId);

    /** Count and total of the user's SKIPPED checks created in {@code [from, to)}. */
    @Query("""
            select new com.parthopratim.spendcheck.check.SkippedTotals(count(c), coalesce(sum(c.amountPaise), 0))
            from SpendCheck c
            where c.userId = :userId and c.createdAt >= :from and c.createdAt < :to
              and c.decision = com.parthopratim.spendcheck.check.CheckDecision.SKIPPED""")
    SkippedTotals skippedBetween(@Param("userId") Long userId, @Param("from") Instant from,
                                 @Param("to") Instant to);
}
