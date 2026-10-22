package com.parthopratim.spendcheck.input;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RawInputRepository extends JpaRepository<RawInput, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);

    List<RawInput> findByUserIdOrderByIdAsc(Long userId);

    /** Bulk delete; transactions.raw_input_id is nulled by the FK's ON DELETE SET NULL. */
    @Transactional
    @Modifying
    @Query("delete from RawInput r where r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
