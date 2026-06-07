package com.crypto.persistence.repository;

import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperPositionRepository extends JpaRepository<PaperPositionEntity, Long> {
    List<PaperPositionEntity> findByStatusOrderByOpenedAtDesc(PaperPositionStatus status);

    List<PaperPositionEntity> findByStatusInOrderByOpenedAtDesc(List<PaperPositionStatus> statuses);

    List<PaperPositionEntity> findByStatus(PaperPositionStatus status);

    Page<PaperPositionEntity> findByStatusOrderByOpenedAtDesc(PaperPositionStatus status, Pageable pageable);

    List<PaperPositionEntity> findByStatusOrderByClosedAtDesc(PaperPositionStatus status);

    Page<PaperPositionEntity> findByStatusOrderByClosedAtDesc(PaperPositionStatus status, Pageable pageable);

    List<PaperPositionEntity> findByStatusAndClosedAtBetweenOrderByClosedAtDesc(
            PaperPositionStatus status,
            Instant start,
            Instant end
    );

    Page<PaperPositionEntity> findByStatusAndSymbolOrderByClosedAtDesc(
            PaperPositionStatus status,
            String symbol,
            Pageable pageable
    );

    Optional<PaperPositionEntity> findFirstBySymbolAndStatusOrderByOpenedAtDesc(
            String symbol,
            PaperPositionStatus status
    );

    boolean existsBySymbolAndStatus(String symbol, PaperPositionStatus status);

    boolean existsBySymbolAndStatusIn(String symbol, List<PaperPositionStatus> statuses);

    Optional<PaperPositionEntity> findByIdAndStatus(Long id, PaperPositionStatus status);

    long countByStatus(PaperPositionStatus status);

    long countByStatusAndSide(PaperPositionStatus status, PositionSide side);

    Page<PaperPositionEntity> findBySymbolOrderByOpenedAtDesc(String symbol, Pageable pageable);

    Page<PaperPositionEntity> findAllByOrderByOpenedAtDesc(Pageable pageable);
}
