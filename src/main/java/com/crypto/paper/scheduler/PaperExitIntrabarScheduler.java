package com.crypto.paper.scheduler;

import com.crypto.common.time.IstanbulTimeUtil;
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

    @Scheduled(cron = "${scanner.intrabar-exit.cron}", zone = "${scanner.intrabar-exit.zone}")
    public void evaluateOpenPaperPositionsOnFiveMinuteBars() {
        ScannerProperties.PaperAuto paperAuto = scannerProperties.getPaperAuto();
        ScannerProperties.PaperExit paperExit = scannerProperties.getPaperExit();
        ScannerProperties.IntrabarExit intrabarExit = scannerProperties.getIntrabarExit();
        boolean v20Enabled = scannerProperties.getV20() != null && Boolean.TRUE.equals(scannerProperties.getV20().getEnabled());
        boolean intrabarEnabled = v20Enabled
                ? intrabarExit != null && Boolean.TRUE.equals(intrabarExit.getEnabled())
                : paperExit != null && Boolean.TRUE.equals(paperExit.getIntrabarCheckEnabled());
        if (paperAuto == null || !Boolean.TRUE.equals(paperAuto.getEnabled()) || !intrabarEnabled) {
            log.info("PAPER_INTRABAR_EXIT_CHECK_SKIPPED_DISABLED");
            return;
        }
        if (!paperTradeLockService.tryAcquire()) {
            log.info("PAPER_INTRABAR_EXIT_CHECK_SKIPPED_LOCKED");
            return;
        }
        String interval = v20Enabled
                ? (intrabarExit == null || intrabarExit.getInterval() == null ? "5m" : intrabarExit.getInterval())
                : (paperExit.getIntrabarInterval() == null ? "5m" : paperExit.getIntrabarInterval());
        try {
            log.info("PAPER_INTRABAR_EXIT_CHECK_STARTED time={} interval={}", IstanbulTimeUtil.nowText(), interval);
            PaperExitEvaluationResult result = exitEngineService.evaluateOpenPositionsWithInterval(interval);
            log.info("PAPER_INTRABAR_EXIT_CHECK_COMPLETED time={} checked={} events={} closed={}",
                    IstanbulTimeUtil.nowText(), result.getCheckedCount(), result.getEventCount(), result.getClosedCount());
        } catch (Exception exception) {
            log.error("PAPER_INTRABAR_EXIT_CHECK_FAILED message={}", exception.getMessage(), exception);
        } finally {
            paperTradeLockService.release();
        }
    }
}
