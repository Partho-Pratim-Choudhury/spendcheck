package com.parthopratim.spendcheck.input;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RawInputRepository extends JpaRepository<RawInput, Long> {

    /**
     * {@code SELECT … FOR SHARE} on the user's raw input: while the caller's transaction is open the purge job's
     * delete waits, so a transaction can't be inserted pointing at a row purged after this check.
     */
    @Query(value = "SELECT id FROM raw_inputs WHERE id = :id AND user_id = :userId FOR SHARE", nativeQuery = true)
    Optional<Long> lockForShare(@Param("id") Long id, @Param("userId") Long userId);

    List<RawInput> findByUserIdOrderByIdAsc(Long userId);

    /** Bulk delete; transactions.raw_input_id is nulled by the FK's ON DELETE SET NULL. */
    @Transactional
    @Modifying
    @Query("delete from RawInput r where r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
