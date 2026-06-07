package com.crypto.persistence.repository;

import com.crypto.common.enums.CoinClassification;
import com.crypto.persistence.entity.CoinScanResultEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoinScanResultRepository extends JpaRepository<CoinScanResultEntity, Long> {
    List<CoinScanResultEntity> findByScanRun_Id(Long scanRunId);

    @Query("""
            select r
            from CoinScanResultEntity r
            join fetch r.scanRun sr
            where sr.id = :scanRunId
            order by r.score desc
            """)
    List<CoinScanResultEntity> findByScanRunIdWithScanRun(@Param("scanRunId") Long scanRunId);

    @Query("""
            select r
            from CoinScanResultEntity r
            join fetch r.scanRun sr
            where sr.id = (
                select max(s.id)
                from MarketScanRunEntity s
                where s.status = 'COMPLETED'
            )
            order by r.score desc
            """)
    List<CoinScanResultEntity> findLatestCompletedResultsWithScanRun();

    long countByScanRun_Id(Long scanRunId);

    List<CoinScanResultEntity> findByScanRun_IdAndClassificationOrderByScoreDesc(
            Long scanRunId,
            CoinClassification classification
    );

    Page<CoinScanResultEntity> findByScanRun_IdAndClassificationOrderByScoreDesc(
            Long scanRunId,
            CoinClassification classification,
            Pageable pageable
    );

    Page<CoinScanResultEntity> findBySymbolOrderByCreatedAtDesc(String symbol, Pageable pageable);

    List<CoinScanResultEntity> findTop20BySymbolOrderByCreatedAtDesc(String symbol);

    Optional<CoinScanResultEntity> findFirstByScanRun_IdAndSymbolOrderByCreatedAtDesc(Long scanRunId, String symbol);
}
