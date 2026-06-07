package com.crypto.paper.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.scanner.config.ScannerProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperExitSchedulerTest {
    private ScannerProperties scannerProperties;
    private ExitEngineService exitEngineService;
    private PaperExitScheduler paperExitScheduler;

    @BeforeEach
    void setUp() {
        scannerProperties = new ScannerProperties();
        exitEngineService = mock(ExitEngineService.class);
        when(exitEngineService.evaluateOpenPositions()).thenReturn(List.of());
        paperExitScheduler = new PaperExitScheduler(scannerProperties, exitEngineService);
    }

    @Test
    void evaluatesOpenPaperPositionsWhenPaperAutoEvaluationIsEnabled() {
        scannerProperties.getPaperAuto().setEnabled(true);
        scannerProperties.getPaperAuto().setEvaluateEnabled(true);

        paperExitScheduler.evaluateOpenPaperPositions();

        verify(exitEngineService).evaluateOpenPositions();
    }

    @Test
    void skipsEvaluationWhenPaperAutoIsDisabled() {
        scannerProperties.getPaperAuto().setEnabled(false);

        paperExitScheduler.evaluateOpenPaperPositions();

        verify(exitEngineService, never()).evaluateOpenPositions();
    }

    @Test
    void skipsEvaluationWhenPaperAutoEvaluationIsDisabled() {
        scannerProperties.getPaperAuto().setEnabled(true);
        scannerProperties.getPaperAuto().setEvaluateEnabled(false);

        paperExitScheduler.evaluateOpenPaperPositions();

        verify(exitEngineService, never()).evaluateOpenPositions();
    }
}
