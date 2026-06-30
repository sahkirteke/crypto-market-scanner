package com.crypto.persistence.service;

import com.crypto.common.enums.ScanType;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.MarketScanPersistenceMapper;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketScanPersistenceService {
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    private static final AtomicLong IN_MEMORY_SCAN_RUN_IDS = new AtomicLong(-1L);

    @SuppressWarnings("unused")
    private final MarketScanRunRepository marketScanRunRepository;
    @SuppressWarnings("unused")
    private final CoinScanResultRepository coinScanResultRepository;
    private final MarketScanPersistenceMapper marketScanPersistenceMapper;

    @Autowired(required = false)
    private JsonlDecisionLogService jsonlDecisionLogService;

    public MarketScanRunEntity saveCompletedScan(MarketScanResult result) {
        MarketScanRunEntity runEntity = marketScanPersistenceMapper.toRunEntity(result, STATUS_COMPLETED);
        runEntity.setId(nextInMemoryScanRunId());
        if (result != null) {
            result.setScanRunId(runEntity.getId());
        }
        log.info(
                "SCAN_DB_PERSIST_SKIPPED scanRunId={} scanType={} status={} reason=PAPER_POSITIONS_ONLY",
                runEntity.getId(),
                runEntity.getScanType(),
                runEntity.getStatus()
        );
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logScanner(Map.of(
                    "event", "SCAN_DB_PERSIST_SKIPPED",
                    "scanRunId", runEntity.getId(),
                    "scanType", runEntity.getScanType() == null ? "" : runEntity.getScanType().name(),
                    "status", runEntity.getStatus(),
                    "reason", "PAPER_POSITIONS_ONLY"
            ));
        }
        return runEntity;
    }

    public MarketScanRunEntity saveFailedScan(ScanType scanType, Instant scanTimeUtc, String errorMessage) {
        MarketScanRunEntity runEntity = new MarketScanRunEntity();
        runEntity.setId(nextInMemoryScanRunId());
        runEntity.setScanType(scanType);
        runEntity.setScanTimeUtc(scanTimeUtc);
        runEntity.setStatus(STATUS_FAILED);
        runEntity.setErrorMessage(errorMessage);
        runEntity.setTotalSymbols(0);
        runEntity.setPreFilterPassedCount(0);
        runEntity.setStrongLongCount(0);
        runEntity.setStrongShortCount(0);
        runEntity.setWatchlistCount(0);
        runEntity.setEliminatedCount(0);
        runEntity.setReasonsJson("[]");
        runEntity.setWarningsJson("[]");
        log.warn(
                "SCAN_FAILED_DB_PERSIST_SKIPPED scanRunId={} scanType={} reason=PAPER_POSITIONS_ONLY error={}",
                runEntity.getId(),
                scanType,
                errorMessage
        );
        return runEntity;
    }

    private Long nextInMemoryScanRunId() {
        return IN_MEMORY_SCAN_RUN_IDS.getAndDecrement();
    }
}
