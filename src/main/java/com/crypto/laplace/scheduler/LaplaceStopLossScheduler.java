package com.crypto.laplace.scheduler;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "trading.laplace", name = "enabled", havingValue = "true")
public class LaplaceStopLossScheduler {
    private static final String INTERVAL = "5m";
    private static final int KLINE_LIMIT = 3;

    private final LaplacePaperTradeCoordinator coordinator;
    private final BinanceFuturesClient binanceFuturesClient;

    @Scheduled(cron = "${trading.laplace.stop-loss-cron}", zone = "${trading.laplace.zone}")
    public void evaluateOpenPositions() {
        Map<String, Kline> candles = new LinkedHashMap<>();
        for (String symbol : coordinator.managementSymbols()) {
            try {
                lastClosed(binanceFuturesClient.getKlines(symbol, INTERVAL, KLINE_LIMIT))
                        .ifPresent(candle -> candles.put(symbol, candle));
            } catch (RuntimeException exception) {
                log.warn("LAPLACE_STOP_LOSS_CANDLE_UNAVAILABLE symbol={} reason={}", symbol, exception.getMessage());
            }
        }
        if (!candles.isEmpty()) {
            coordinator.closeStopLosses(candles);
        }
    }

    private java.util.Optional<Kline> lastClosed(List<Kline> candles) {
        return (candles == null ? List.<Kline>of() : candles).stream()
                .filter(candle -> candle != null && Boolean.TRUE.equals(candle.getClosed()))
                .reduce((first, second) -> second);
    }
}
