package com.parthopratim.spendcheck.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {

    @EntityGraph(attributePaths = "category")
    Optional<Transaction> findByIdAndUserId(UUID id, Long userId);

    @Override
    @EntityGraph(attributePaths = "category")
    List<Transaction> findAll(Specification<Transaction> spec, Sort sort);

    /** The user's most recently created transaction in {@code status} created after {@code since}. */
    @EntityGraph(attributePaths = "category")
    Optional<Transaction> findFirstByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, TransactionStatus status, Instant since);

    /** Points the user's transactions with {@code from <= spentAt < to} at a plan. */
    @Modifying(flushAutomatically = true)
    @Query("update Transaction t set t.planId = :planId "
            + "where t.userId = :userId and t.spentAt >= :from and t.spentAt < :to")
    int assignPlan(@Param("userId") Long userId, @Param("planId") Long planId, @Param("from") Instant from,
                   @Param("to") Instant to);
}
