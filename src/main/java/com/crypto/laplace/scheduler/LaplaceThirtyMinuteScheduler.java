package com.crypto.laplace.scheduler;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.model.StartupHistory;
import com.crypto.laplace.service.LaplaceDiagnosticLogService;
import com.crypto.laplace.service.LaplaceSignalService;
import com.crypto.laplace.service.LaplaceStartupHistoryService;
import com.crypto.laplace.service.StartupMarketUniverseService;
import com.crypto.laplace.service.ThirtyMinuteKlineService;
import com.crypto.laplace.session.LaplaceSessionManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.math.BigDecimal;
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
    private final LaplacePaperTradeCoordinator coordinator;
    private final LaplaceSessionManager session;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConcurrentHashMap<String, Instant> lastProcessed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> postStartupBarCounts = new ConcurrentHashMap<>();

    @Scheduled(cron = "${trading.laplace.cron}", zone = "${trading.laplace.zone}")
    public void scan() {
        LaplaceSessionManager.ScanContext scanContext=session.beginScan();
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
            List<LaplacePaperTradeCoordinator.SignalEnvelope> batch=symbols.stream().sorted().map(symbol->calculate(symbol,scanContext)).filter(java.util.Objects::nonNull).toList();
            coordinator.onSignalBatch(batch,scanContext.sessionId(),scanContext.current24hVolumes());
        } finally {
            running.set(false);
        }
    }

    public void clearSessionRuntime() {
        lastProcessed.clear();
        postStartupBarCounts.clear();
    }

    void process(String symbol) {
        process(symbol,new LaplaceSessionManager.ScanContext(session.currentSessionId(),universe.currentVolumeSnapshot()));
    }

    void process(String symbol,LaplaceSessionManager.ScanContext scanContext) {
        var item=calculate(symbol,scanContext);if(item!=null)coordinator.onSignal(item.signal(),item.inEntryUniverse(),scanContext.sessionId(),scanContext.current24hVolumes());
    }

    private LaplacePaperTradeCoordinator.SignalEnvelope calculate(String symbol,LaplaceSessionManager.ScanContext scanContext) {
        Instant close = null;
        try {
            StartupHistory history = startupHistory.history(symbol);
            if (history == null) {
                return null;
            }
            List<Kline> data = klines.loadClosed(symbol);
            if (!postStartupBarCounts.containsKey(symbol)) {
                LaplaceSignalResult baseline = signals.calculate(symbol, history.candles(), 0);
                session.processSignal(scanContext.sessionId(),()->coordinator.initializeBaseline(symbol,baseline.entrySignal()));
            }
            close = data.getLast().getCloseTime();
            Instant previous = lastProcessed.putIfAbsent(symbol, history.baselineCloseTime());
            previous = previous == null ? history.baselineCloseTime() : previous;
            if (!close.isAfter(previous)) {
                log.debug("LAPLACE_DUPLICATE_OR_STARTUP_CANDLE symbol={} candleCloseTime={}", symbol, close);
                return null;
            }
            if (!lastProcessed.replace(symbol, previous, close)) {
                log.debug("LAPLACE_DUPLICATE_CANDLE symbol={} candleCloseTime={}", symbol, close);
                return null;
            }
            int count = postStartupBarCounts.merge(symbol, 1, Integer::sum);
            LaplaceSignalResult result = signals.calculate(symbol, data, count);
            diagnostics.signal(result);
            if (count == 1) {
                log.info("LAPLACE_FIRST_POST_STARTUP_CANDLE_PROCESSED symbol={} candleCloseTime={} previousNormalizedSlope={} currentNormalizedSlope={} entrySignal={} strongReversalSignal={} eligibleForExecution={}",
                        symbol, close, result.previousNormalizedSlope(), result.currentNormalizedSlope(),
                        result.entrySignal(), result.strongReversalSignal(), result.eligibleForExecution());
            }
            log.debug("LAPLACE_SIGNAL_CALCULATED symbol={} candleCloseTime={} entrySignal={} strongReversalSignal={} startupState={} postStartupClosedBarCount={} eligibleForExecution={}",
                    symbol, close, result.entrySignal(), result.strongReversalSignal(), result.startupState(),
                    count, result.eligibleForExecution());
            return new LaplacePaperTradeCoordinator.SignalEnvelope(result,universe.symbols().contains(symbol));
        } catch (RuntimeException exception) {
            log.error("LAPLACE_SYMBOL_FAILED symbol={} candleCloseTime={} errorType={} errorMessage={}",
                    symbol, close, exception.getClass().getSimpleName(), exception.getMessage(), exception);
            diagnostics.error(symbol, exception.getClass().getSimpleName(), exception.getMessage());
            return null;
        }
    }
}
