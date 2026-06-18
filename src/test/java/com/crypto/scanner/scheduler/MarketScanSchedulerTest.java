package com.crypto.scanner.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.paper.service.PaperPositionService;
import com.crypto.paper.service.PaperPositionService.PaperOpenSummary;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.MarketScannerOrchestratorService;
import com.crypto.scanner.service.PendingEntryConfirmService;
import com.crypto.scanner.service.ScanLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketScanSchedulerTest {
    private MarketScannerOrchestratorService marketScannerOrchestratorService;
    private ScanLockService scanLockService;
    private ScannerProperties scannerProperties;
    private PaperPositionService paperPositionService;
    private PendingEntryConfirmService pendingEntryConfirmService;
    private MarketScanScheduler marketScanScheduler;

    @BeforeEach
    void setUp() {
        marketScannerOrchestratorService = mock(MarketScannerOrchestratorService.class);
        scanLockService = mock(ScanLockService.class);
        scannerProperties = new ScannerProperties();
        paperPositionService = mock(PaperPositionService.class);
        pendingEntryConfirmService = mock(PendingEntryConfirmService.class);
        when(paperPositionService.openPositionsFromScanRun(100L)).thenReturn(emptySummary());
        when(paperPositionService.openPositionsFromScanRun(101L)).thenReturn(emptySummary());
        when(paperPositionService.openPositionsFromScanRun(102L)).thenReturn(emptySummary());
        marketScanScheduler = new MarketScanScheduler(
                marketScannerOrchestratorService,
                scanLockService,
                scannerProperties,
                paperPositionService,
                pendingEntryConfirmService);
    }

    @Test
    void disabledSchedulerDoesNotCallOrchestrator() {
        scannerProperties.getScheduler().setEnabled(false);

        marketScanScheduler.runFourHourScheduledScan();

        verify(marketScannerOrchestratorService, never()).runAndPersist(ScanType.FOUR_HOUR);
    }

    @Test
    void enabledSchedulerCallsOrchestrator() {
        scannerProperties.getScheduler().setEnabled(true);
        when(scanLockService.tryAcquire()).thenReturn(true);
        when(marketScannerOrchestratorService.runAndPersist(ScanType.FOUR_HOUR))
                .thenReturn(MarketScanResult.builder().scanRunId(100L).build());

        marketScanScheduler.runFourHourScheduledScan();

        verify(marketScannerOrchestratorService).runAndPersist(ScanType.FOUR_HOUR);
    }

    @Test
    void enabledSchedulerCachesPaperPositionsForConfirmAfterScan() {
        scannerProperties.getScheduler().setEnabled(true);
        scannerProperties.getPaperAuto().setEnabled(true);
        scannerProperties.getPaperAuto().setOpenAfterScan(true);
        when(scanLockService.tryAcquire()).thenReturn(true);
        when(marketScannerOrchestratorService.runAndPersist(ScanType.ONE_HOUR))
                .thenReturn(MarketScanResult.builder().scanRunId(100L).build());

        marketScanScheduler.runOneHourScheduledScan();

        verify(pendingEntryConfirmService).cacheCandidatesForConfirm(ScanType.ONE_HOUR, 100L);
        verify(paperPositionService, never()).openPositionsFromScanRun(100L);
        verify(paperPositionService, never()).openPositionsFromLatestSignals();
    }

    @Test
    void disabledPaperAutoDoesNotOpenPaperPositionsAfterScan() {
        scannerProperties.getScheduler().setEnabled(true);
        scannerProperties.getPaperAuto().setEnabled(false);
        when(scanLockService.tryAcquire()).thenReturn(true);
        when(marketScannerOrchestratorService.runAndPersist(ScanType.ONE_HOUR))
                .thenReturn(MarketScanResult.builder().scanRunId(100L).build());

        marketScanScheduler.runOneHourScheduledScan();

        verify(paperPositionService, never()).openPositionsFromLatestSignals();
        verify(paperPositionService, never()).openPositionsFromScanRun(100L);
        verify(pendingEntryConfirmService).cacheCandidatesForConfirm(ScanType.ONE_HOUR, 100L);
    }

    @Test
    void lockedSchedulerDoesNotCallOrchestrator() {
        scannerProperties.getScheduler().setEnabled(true);
        when(scanLockService.tryAcquire()).thenReturn(false);

        marketScanScheduler.runFourHourScheduledScan();

        verify(marketScannerOrchestratorService, never()).runAndPersist(ScanType.FOUR_HOUR);
    }

    @Test
    void lockIsReleasedEvenWhenScanFails() {
        scannerProperties.getScheduler().setEnabled(true);
        when(scanLockService.tryAcquire()).thenReturn(true);
        doThrow(new RuntimeException("scan failed"))
                .when(marketScannerOrchestratorService).runAndPersist(ScanType.FOUR_HOUR);

        marketScanScheduler.runFourHourScheduledScan();

        verify(scanLockService).release();
    }

    @Test
    void oneHourScheduledScanUsesOneHourScanType() {
        scannerProperties.getScheduler().setEnabled(true);
        when(scanLockService.tryAcquire()).thenReturn(true);
        when(marketScannerOrchestratorService.runAndPersist(ScanType.ONE_HOUR))
                .thenReturn(MarketScanResult.builder().scanRunId(101L).build());

        marketScanScheduler.runOneHourScheduledScan();

        verify(marketScannerOrchestratorService).runAndPersist(ScanType.ONE_HOUR);
    }

    @Test
    void fourHourScheduledScanUsesFourHourScanType() {
        scannerProperties.getScheduler().setEnabled(true);
        when(scanLockService.tryAcquire()).thenReturn(true);
        when(marketScannerOrchestratorService.runAndPersist(ScanType.FOUR_HOUR))
                .thenReturn(MarketScanResult.builder().scanRunId(102L).build());

        marketScanScheduler.runFourHourScheduledScan();

        verify(marketScannerOrchestratorService).runAndPersist(ScanType.FOUR_HOUR);
    }
    private PaperOpenSummary emptySummary() {
        return new PaperOpenSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
