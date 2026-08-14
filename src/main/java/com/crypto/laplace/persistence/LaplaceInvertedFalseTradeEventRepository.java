package com.crypto.laplace.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaplaceInvertedFalseTradeEventRepository extends JpaRepository<LaplaceInvertedFalseTradeEventEntity, String> {
    List<LaplaceInvertedFalseTradeEventEntity> findTop100ByJsonlWrittenFalseOrderByCreatedAtAsc();
}
