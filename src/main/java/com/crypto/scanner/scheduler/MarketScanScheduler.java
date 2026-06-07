package com.crypto.scanner.scheduler;

import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.MarketScannerOrchestratorService;
import com.crypto.scanner.service.ScanLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"scheduler", "manual-scheduler"})
@RequiredArgsConstructor
public class MarketScanScheduler {
    private final MarketScannerOrchestratorService marketScannerOrchestratorService;
    private final ScanLockService scanLockService;
    private final ScannerProperties scannerProperties;

    @Scheduled(cron = "${scanner.scheduler.one-hour-cron}", zone = "${scanner.scheduler.zone}")
    public void runOneHourScheduledScan() {
        executeScheduledScan(ScanType.ONE_HOUR, true);
    }

    @Scheduled(cron = "${scanner.scheduler.four-hour-cron}", zone = "${scanner.scheduler.zone}")
    public void runFourHourScheduledScan() {
        executeScheduledScan(ScanType.FOUR_HOUR, true);
    }

    public void triggerOneHourScanManually() {
        executeScheduledScan(ScanType.ONE_HOUR, false);
    }

    public void triggerFourHourScanManually() {
        executeScheduledScan(ScanType.FOUR_HOUR, false);
    }

    private void executeScheduledScan(ScanType scanType, boolean requireSchedulerEnabled) {
        if (requireSchedulerEnabled && !Boolean.TRUE.equals(scannerProperties.getScheduler().getEnabled())) {
            log.debug("SCHEDULED_SCAN_SKIPPED_DISABLED scanType={}", scanType);
            return;
        }

        if (!scanLockService.tryAcquire()) {
            log.info("SCHEDULED_SCAN_SKIPPED_LOCKED scanType={}", scanType);
            return;
        }

        try {
            log.info("SCHEDULED_SCAN_STARTED scanType={}", scanType);
            MarketScanResult result = marketScannerOrchestratorService.runAndPersist(scanType);
            log.info("SCHEDULED_SCAN_COMPLETED scanType={} scanRunId={}", scanType, result.getScanRunId());
        } catch (RuntimeException exception) {
            log.error("SCHEDULED_SCAN_FAILED scanType={} message={}", scanType, exception.getMessage(), exception);
        } finally {
            scanLockService.release();
            log.info("SCHEDULED_SCAN_LOCK_RELEASED scanType={}", scanType);
        }
    }
}
