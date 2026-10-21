package com.parthopratim.spendcheck.input;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RawInputRepository extends JpaRepository<RawInput, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);
}
