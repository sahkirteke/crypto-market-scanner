package com.crypto.persistence.repository;

import com.crypto.persistence.entity.MarketScanRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketScanRunRepository extends JpaRepository<MarketScanRunEntity, Long> {
    Optional<MarketScanRunEntity> findTopByStatusOrderByScanTimeUtcDesc(String status);

    List<MarketScanRunEntity> findTop20ByOrderByScanTimeUtcDesc();

    Page<MarketScanRunEntity> findAllByOrderByScanTimeUtcDesc(Pageable pageable);
}
