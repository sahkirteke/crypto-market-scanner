package com.crypto.persistence.repository;

import com.crypto.persistence.entity.PaperPositionEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperPositionEventRepository extends JpaRepository<PaperPositionEventEntity, Long> {
    List<PaperPositionEventEntity> findByPosition_IdOrderByEventTimeUtcAsc(Long positionId);
}
