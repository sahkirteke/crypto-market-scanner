package com.crypto.paper.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.paper.model.PaperExitEvaluationResult;
import com.crypto.paper.service.ExitEngineService;
import com.crypto.paper.service.PaperTradeLockService;
import com.crypto.scanner.config.ScannerProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperExitIntrabarSchedulerTest {
    @Test
    void doesNotCallExitEngineWhenIntrabarCheckDisabled() {
        ScannerProperties properties = new ScannerProperties();
        properties.getPaperExit().setIntrabarCheckEnabled(false);
        ExitEngineService exitEngineService = mock(ExitEngineService.class);
        PaperTradeLockService lockService = mock(PaperTradeLockService.class);
        PaperExitIntrabarScheduler scheduler = new PaperExitIntrabarScheduler(properties, exitEngineService, lockService);

        scheduler.evaluateOpenPaperPositionsOnFiveMinuteBars();

        verify(exitEngineService, never()).evaluateOpenPositionsWithInterval("5m");
        verify(lockService, never()).tryAcquire();
    }

    @Test
    void doesNotCallExitEngineWhenLockCannotBeAcquired() {
        ScannerProperties properties = new ScannerProperties();
        ExitEngineService exitEngineService = mock(ExitEngineService.class);
        PaperTradeLockService lockService = mock(PaperTradeLockService.class);
        when(lockService.tryAcquire()).thenReturn(false);
        PaperExitIntrabarScheduler scheduler = new PaperExitIntrabarScheduler(properties, exitEngineService, lockService);

        scheduler.evaluateOpenPaperPositionsOnFiveMinuteBars();

        verify(exitEngineService, never()).evaluateOpenPositionsWithInterval("5m");
    }

    @Test
    void callsExitEngineWithConfiguredIntervalWhenEnabledAndLocked() {
        ScannerProperties properties = new ScannerProperties();
        ExitEngineService exitEngineService = mock(ExitEngineService.class);
        PaperTradeLockService lockService = mock(PaperTradeLockService.class);
        when(lockService.tryAcquire()).thenReturn(true);
        when(exitEngineService.evaluateOpenPositionsWithInterval("5m")).thenReturn(PaperExitEvaluationResult.builder()
                .checkedCount(0)
                .eventCount(0)
                .closedCount(0)
                .closedPositions(List.of())
                .build());
        PaperExitIntrabarScheduler scheduler = new PaperExitIntrabarScheduler(properties, exitEngineService, lockService);

        scheduler.evaluateOpenPaperPositionsOnFiveMinuteBars();

        verify(exitEngineService).evaluateOpenPositionsWithInterval("5m");
        verify(lockService).release();
    }
}
