package com.crypto.persistence.repository;

import com.crypto.common.enums.CoinClassification;
import com.crypto.persistence.entity.CoinScanResultEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinScanResultRepository extends JpaRepository<CoinScanResultEntity, Long> {
    List<CoinScanResultEntity> findByScanRun_Id(Long scanRunId);

    List<CoinScanResultEntity> findByScanRun_IdAndClassificationOrderByScoreDesc(
            Long scanRunId,
            CoinClassification classification
    );

    List<CoinScanResultEntity> findTop20BySymbolOrderByCreatedAtDesc(String symbol);
}
