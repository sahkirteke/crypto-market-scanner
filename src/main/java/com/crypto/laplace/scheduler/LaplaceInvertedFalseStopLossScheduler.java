package com.crypto.laplace.scheduler;

import com.crypto.laplace.execution.LaplaceInvertedFalseExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionRepository;
import com.crypto.laplace.service.FiveMinuteKlineService;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LaplaceInvertedFalseStopLossScheduler {
    private final LaplaceInvertedFalsePositionRepository positions;
    private final FiveMinuteKlineService klines;
    private final LaplaceInvertedFalseExecutionService execution;
    private final LaplaceInvertedFalseRuntimeService runtime;
    private final ConcurrentHashMap<String, Instant> lastProcessed = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "${trading.laplace.stop-loss-cron}", zone = "${trading.laplace.zone}")
    public void evaluate() {
        if (!runtime.isActive()) return;
        if (!running.compareAndSet(false, true)) return;
        try {
            positions.findByStrategyAndStatus(LaplaceInvertedFalseExecutionService.STRATEGY, LaplacePositionStatus.OPEN)
                    .forEach(position -> evaluate(position.getId(), position.getSymbol()));
        } finally {
            running.set(false);
        }
    }

    void evaluate(String positionId, String symbol) {
        try {
            var candle = klines.loadLatestClosed(symbol);
            Instant previous = lastProcessed.get(positionId);
            if (previous != null && !candle.getCloseTime().isAfter(previous)) return;
            if (execution.closeAtStopLoss(positionId, candle)) execution.drainTradeEvents();
            lastProcessed.put(positionId, candle.getCloseTime());
        } catch (RuntimeException exception) {
            log.error("LAPLACE_STOP_LOSS_EVALUATION_FAILED positionId={} symbol={} error={}",
                    positionId, symbol, exception.getMessage(), exception);
        }
    }

    public void clearRuntimeState() {
        lastProcessed.clear();
        running.set(false);
    }
}
