package com.crypto.paper.service;

import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.scanner.config.ScannerProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "trading", name = "old-strategy-enabled", havingValue = "true")
@RequiredArgsConstructor
public class PaperExitScheduler {
    private final ScannerProperties scannerProperties;
    private final ExitEngineService exitEngineService;

    @Scheduled(cron = "${scanner.paper-auto.evaluate-cron}", zone = "${scanner.paper-auto.zone}")
    public void evaluateOpenPaperPositions() {
        ScannerProperties.PaperAuto paperAuto = scannerProperties.getPaperAuto();
        if (paperAuto == null
                || !Boolean.TRUE.equals(paperAuto.getEnabled())
                || !Boolean.TRUE.equals(paperAuto.getEvaluateEnabled())) {
            log.debug("AUTO_PAPER_EXIT_EVALUATION_SKIPPED_DISABLED");
            return;
        }

        try {
            log.info("AUTO_PAPER_EXIT_EVALUATION_STARTED");
            List<PaperPositionEntity> closedPositions = exitEngineService.evaluateOpenPositions();
            log.info("AUTO_PAPER_EXIT_EVALUATION_COMPLETED closed={}", closedPositions.size());
        } catch (RuntimeException exception) {
            log.error("AUTO_PAPER_EXIT_EVALUATION_FAILED message={}", exception.getMessage(), exception);
        }
    }
}
