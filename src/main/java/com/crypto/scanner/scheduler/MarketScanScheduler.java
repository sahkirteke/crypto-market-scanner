package com.crypto.scanner.scheduler;

import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.paper.service.PaperPositionService;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.MarketScannerOrchestratorService;
import com.crypto.scanner.service.ScanLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketScanScheduler {
    private final MarketScannerOrchestratorService marketScannerOrchestratorService;
    private final ScanLockService scanLockService;
    private final ScannerProperties scannerProperties;
    private final PaperPositionService paperPositionService;

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
            tryOpenPaperPositionsAfterScan(scanType, result.getScanRunId());
            log.info("SCHEDULED_SCAN_COMPLETED scanType={} scanRunId={}", scanType, result.getScanRunId());
        } catch (RuntimeException exception) {
            log.error("SCHEDULED_SCAN_FAILED scanType={} message={}", scanType, exception.getMessage(), exception);
        } finally {
            scanLockService.release();
            log.info("SCHEDULED_SCAN_LOCK_RELEASED scanType={}", scanType);
        }
    }

    private void tryOpenPaperPositionsAfterScan(ScanType scanType, Long scanRunId) {
        ScannerProperties.PaperAuto paperAuto = scannerProperties.getPaperAuto();
        if (paperAuto == null
                || !Boolean.TRUE.equals(paperAuto.getEnabled())
                || !Boolean.TRUE.equals(paperAuto.getOpenAfterScan())) {
            return;
        }

        try {
            log.info("AUTO_PAPER_OPEN_AFTER_SCAN_STARTED scanType={} scanRunId={}", scanType, scanRunId);
            int openedCount = paperPositionService.openPositionsFromLatestSignals().size();
            log.info("AUTO_PAPER_OPEN_AFTER_SCAN_COMPLETED scanType={} opened={}", scanType, openedCount);
        } catch (RuntimeException exception) {
            log.error(
                    "AUTO_PAPER_OPEN_AFTER_SCAN_FAILED scanType={} scanRunId={} message={}",
                    scanType,
                    scanRunId,
                    exception.getMessage(),
                    exception
            );
        }
    }
}
