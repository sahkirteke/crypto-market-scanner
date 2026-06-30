package com.crypto.scanner.service;

import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.service.MarketScanPersistenceService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketScannerOrchestratorService {
    private final MarketScannerService marketScannerService;
    private final MarketScanPersistenceService marketScanPersistenceService;

    public MarketScanResult runAndPersist(ScanType scanType) {
        return runScanAndPersist(scanType);
    }

    public MarketScanResult runScanAndPersist(ScanType scanType) {
        Instant scanStartTime = Instant.now();
        try {
            MarketScanResult result = marketScannerService.runScan(scanType);
            MarketScanRunEntity savedRun = marketScanPersistenceService.saveCompletedScan(result);
            result.setScanRunId(savedRun.getId());
            log.info("SCAN_DB_PERSIST_SKIPPED scanRunId={} scanType={} reason=PAPER_POSITIONS_ONLY", savedRun.getId(), scanType);
            return result;
        } catch (RuntimeException exception) {
            log.error("SCAN_PERSIST_FAILED scanType={} message={}", scanType, exception.getMessage(), exception);
            try {
                marketScanPersistenceService.saveFailedScan(scanType, scanStartTime, exception.getMessage());
            } catch (RuntimeException persistFailureException) {
                log.error("SCAN_FAILED_STATUS_PERSIST_FAILED scanType={} message={}",
                        scanType, persistFailureException.getMessage(), persistFailureException);
            }
            throw exception;
        }
    }
}
