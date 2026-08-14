package com.crypto.laplace.scheduler;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplaceSignalFanOut;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.model.StartupHistory;
import com.crypto.laplace.service.LaplaceDiagnosticLogService;
import com.crypto.laplace.service.LaplaceSignalService;
import com.crypto.laplace.service.LaplaceStartupHistoryService;
import com.crypto.laplace.service.StartupMarketUniverseService;
import com.crypto.laplace.service.ThirtyMinuteKlineService;
import com.crypto.laplace.service.LaplaceMarketDataGate;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "trading.laplace", name = "enabled", havingValue = "true")
public class LaplaceThirtyMinuteScheduler {
    private final StartupMarketUniverseService universe;
    private final LaplaceStartupHistoryService startupHistory;
    private final ThirtyMinuteKlineService klines;
    private final LaplaceSignalService signals;
    private final LaplaceDiagnosticLogService diagnostics;
    private final LaplaceSignalFanOut coordinator;
    private final LaplaceMarketDataGate marketDataGate;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConcurrentHashMap<String, Instant> lastProcessed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> postStartupBarCounts = new ConcurrentHashMap<>();

    @Scheduled(cron = "${trading.laplace.cron}", zone = "${trading.laplace.zone}")
    public void scan() {
        if (!marketDataGate.allowsMarketData()) return;
        Set<String> managed = coordinator.managementSymbols();
        if (!universe.isReady() && managed.isEmpty()) {
            log.error("LAPLACE_SCAN_SKIPPED marketUniverseReady=false");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("LAPLACE_SCAN_SKIPPED concurrentRun=true");
            return;
        }
        try {
            Set<String> symbols = new HashSet<>(startupHistory.readySymbols());
            for (String symbol : managed) {
                if (!startupHistory.isReady(symbol)) {
                    startupHistory.initializeSymbol(symbol);
                }
                if (startupHistory.isReady(symbol)) {
                    symbols.add(symbol);
                }
            }
            symbols.forEach(this::process);
        } finally {
            running.set(false);
        }
    }

    void process(String symbol) {
        Instant close = null;
        try {
            StartupHistory history = startupHistory.history(symbol);
            if (history == null) {
                return;
            }
            List<Kline> data = klines.loadClosed(symbol);
            if (!postStartupBarCounts.containsKey(symbol)) {
                LaplaceSignalResult baseline = signals.calculate(symbol, history.candles(), 0);
                coordinator.initializeBaseline(symbol, baseline.entrySignal());
            }
            close = data.getLast().getCloseTime();
            Instant previous = lastProcessed.putIfAbsent(symbol, history.baselineCloseTime());
            previous = previous == null ? history.baselineCloseTime() : previous;
            if (!close.isAfter(previous)) {
                log.debug("LAPLACE_DUPLICATE_OR_STARTUP_CANDLE symbol={} candleCloseTime={}", symbol, close);
                return;
            }
            if (!lastProcessed.replace(symbol, previous, close)) {
                log.debug("LAPLACE_DUPLICATE_CANDLE symbol={} candleCloseTime={}", symbol, close);
                return;
            }
            int count = postStartupBarCounts.merge(symbol, 1, Integer::sum);
            LaplaceSignalResult result = signals.calculate(symbol, data, count);
            diagnostics.signal(result);
            coordinator.onSignal(result, universe.symbols().contains(symbol));
            if (count == 1) {
                log.info("LAPLACE_FIRST_POST_STARTUP_CANDLE_PROCESSED symbol={} candleCloseTime={} previousNormalizedSlope={} currentNormalizedSlope={} entrySignal={} strongReversalSignal={} eligibleForExecution={}",
                        symbol, close, result.previousNormalizedSlope(), result.currentNormalizedSlope(),
                        result.entrySignal(), result.strongReversalSignal(), result.eligibleForExecution());
            }
            log.debug("LAPLACE_SIGNAL_CALCULATED symbol={} candleCloseTime={} entrySignal={} strongReversalSignal={} startupState={} postStartupClosedBarCount={} eligibleForExecution={}",
                    symbol, close, result.entrySignal(), result.strongReversalSignal(), result.startupState(),
                    count, result.eligibleForExecution());
        } catch (RuntimeException exception) {
            log.error("LAPLACE_SYMBOL_FAILED symbol={} candleCloseTime={} errorType={} errorMessage={}",
                    symbol, close, exception.getClass().getSimpleName(), exception.getMessage(), exception);
            diagnostics.error(symbol, exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    public void clearRuntimeState() {
        lastProcessed.clear();
        postStartupBarCounts.clear();
        running.set(false);
    }
}
