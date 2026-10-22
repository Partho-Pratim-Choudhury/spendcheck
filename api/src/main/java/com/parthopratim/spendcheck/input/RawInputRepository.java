package com.parthopratim.spendcheck.input;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawInputRepository extends JpaRepository<RawInput, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);

    List<RawInput> findByUserIdOrderByIdAsc(Long userId);
}
