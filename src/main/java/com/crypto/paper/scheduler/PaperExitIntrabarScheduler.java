package com.crypto.paper.scheduler;

import com.crypto.paper.model.PaperExitEvaluationResult;
import com.crypto.paper.service.ExitEngineService;
import com.crypto.paper.service.PaperTradeLockService;
import com.crypto.scanner.config.ScannerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaperExitIntrabarScheduler {
    private final ScannerProperties scannerProperties;
    private final ExitEngineService exitEngineService;
    private final PaperTradeLockService paperTradeLockService;

    @Scheduled(cron = "${scanner.paper-exit.intrabar-cron}", zone = "${scanner.paper-exit.intrabar-zone}")
    public void evaluateOpenPaperPositionsOnFiveMinuteBars() {
        ScannerProperties.PaperAuto paperAuto = scannerProperties.getPaperAuto();
        ScannerProperties.PaperExit paperExit = scannerProperties.getPaperExit();
        if (paperAuto == null || !Boolean.TRUE.equals(paperAuto.getEnabled())
                || paperExit == null || !Boolean.TRUE.equals(paperExit.getIntrabarCheckEnabled())) {
            log.info("PAPER_INTRABAR_EXIT_CHECK_SKIPPED_DISABLED");
            return;
        }
        if (!paperTradeLockService.tryAcquire()) {
            log.info("PAPER_INTRABAR_EXIT_CHECK_SKIPPED_LOCKED");
            return;
        }
        String interval = paperExit.getIntrabarInterval() == null ? "5m" : paperExit.getIntrabarInterval();
        try {
            log.info("PAPER_INTRABAR_EXIT_CHECK_STARTED interval={}", interval);
            PaperExitEvaluationResult result = exitEngineService.evaluateOpenPositionsWithInterval(interval);
            log.info("PAPER_INTRABAR_EXIT_CHECK_COMPLETED checked={} events={} closed={}",
                    result.getCheckedCount(), result.getEventCount(), result.getClosedCount());
        } catch (Exception exception) {
            log.error("PAPER_INTRABAR_EXIT_CHECK_FAILED message={}", exception.getMessage(), exception);
        } finally {
            paperTradeLockService.release();
        }
    }
}
