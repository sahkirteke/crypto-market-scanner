package com.crypto.persistence.repository;

import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperPositionRepository extends JpaRepository<PaperPositionEntity, Long> {
    List<PaperPositionEntity> findByStatusOrderByOpenedAtDesc(PaperPositionStatus status);

    List<PaperPositionEntity> findByStatus(PaperPositionStatus status);

    Page<PaperPositionEntity> findByStatusOrderByClosedAtDesc(PaperPositionStatus status, Pageable pageable);

    Optional<PaperPositionEntity> findFirstBySymbolAndStatusOrderByOpenedAtDesc(
            String symbol,
            PaperPositionStatus status
    );

    boolean existsBySymbolAndStatus(String symbol, PaperPositionStatus status);

    Page<PaperPositionEntity> findBySymbolOrderByOpenedAtDesc(String symbol, Pageable pageable);

    Page<PaperPositionEntity> findAllByOrderByOpenedAtDesc(Pageable pageable);
}
