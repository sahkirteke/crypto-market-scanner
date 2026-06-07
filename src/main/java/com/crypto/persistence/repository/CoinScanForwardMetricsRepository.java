package com.crypto.persistence.repository;

import com.crypto.persistence.entity.CoinScanForwardMetricsEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinScanForwardMetricsRepository extends JpaRepository<CoinScanForwardMetricsEntity, Long> {
    boolean existsByCoinScanResult_Id(Long coinScanResultId);
    Optional<CoinScanForwardMetricsEntity> findByCoinScanResult_Id(Long coinScanResultId);
}
