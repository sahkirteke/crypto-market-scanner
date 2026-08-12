package com.crypto.laplace.scheduler;

import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.service.LaplacePositionManagementService;
import com.crypto.laplace.session.LaplaceSessionManager;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "trading.laplace", name = "paper-execution-enabled", havingValue = "true")
public class LaplaceStopLossScheduler {
    private final LaplacePaperPositionRepository positions;
    private final LaplacePositionManagementService management;
    private final LaplacePaperExecutionService execution;
    private final LaplaceSessionManager session;
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "${trading.laplace.stop-loss-cron}", zone = "${trading.laplace.zone}")
    public void evaluate() {
        if (!running.compareAndSet(false, true)) return;
        try {
            positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)
                    .forEach(position -> evaluate(position.getId(), position.getSymbol()));
        } finally {
            running.set(false);
        }
    }

    void evaluate(String positionId, String symbol) {
        try {
            management.catchUp(positionId, Instant.now());
            execution.drainTradeEvents();
            session.tick();
        } catch (RuntimeException exception) {
            log.error("LAPLACE_STOP_LOSS_EVALUATION_FAILED positionId={} symbol={} error={}",
                    positionId, symbol, exception.getMessage(), exception);
        }
    }
}
