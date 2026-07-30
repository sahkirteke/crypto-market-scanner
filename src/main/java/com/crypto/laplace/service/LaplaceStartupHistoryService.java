package com.crypto.laplace.service;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.PreparedLaplaceCandle;
import com.crypto.laplace.model.StartupHistory;
import com.crypto.laplace.model.StartupState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Order(200)
@ConditionalOnProperty(prefix = "trading.laplace", name = "enabled", havingValue = "true")
public class LaplaceStartupHistoryService implements ApplicationRunner {
    private final StartupMarketUniverseService universe;
    private final ThirtyMinuteKlineService klines;
    private final LaplaceKernelRegressionCalculator regression;
    private final Atr14Calculator atr;
    private final LaplaceStrategyProperties properties;
    private final Map<String, StartupHistory> histories = new ConcurrentHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        if (!universe.isReady()) {
            return;
        }
        universe.symbols().forEach(this::initializeSymbol);
    }

    public void initializeSymbol(String symbol) {
        int requested = properties.getLaplace().getStartupClosedCandleCount();
        log.info("LAPLACE_STARTUP_HISTORY_LOAD_STARTED symbol={} requestedClosedCandleCount={}", symbol, requested);
        try {
            List<Kline> candles = klines.loadStartupClosed(symbol);
            List<PreparedLaplaceCandle> prepared = prepare(candles);
            StartupHistory history = new StartupHistory(symbol, candles, prepared,
                    candles.get(candles.size() - 1).getCloseTime(), StartupState.READY_WAITING_NEXT_CLOSE);
            histories.put(symbol, history);
            log.info("LAPLACE_STARTUP_HISTORY_LOAD_COMPLETED symbol={} requestedClosedCandleCount={} receivedClosedCandleCount={} oldestCandleOpenTime={} latestClosedCandleCloseTime={} startupState={}",
                    symbol, requested, candles.size(), candles.get(0).getOpenTime(),
                    history.baselineCloseTime(), history.state());
        } catch (RuntimeException exception) {
            histories.remove(symbol);
            log.error("LAPLACE_STARTUP_HISTORY_LOAD_FAILED symbol={} requestedClosedCandleCount={} receivedClosedCandleCount={} startupState={} errorMessage={}",
                    symbol, requested, 0, StartupState.DATA_ERROR, exception.getMessage(), exception);
        }
    }

    public boolean isReady(String symbol) {
        return histories.containsKey(symbol);
    }

    public StartupHistory history(String symbol) {
        return histories.get(symbol);
    }

    public Set<String> readySymbols() {
        return Set.copyOf(histories.keySet());
    }

    private List<PreparedLaplaceCandle> prepare(List<Kline> candles) {
        List<PreparedLaplaceCandle> result = new ArrayList<>(candles.size());
        Double previousRegression = null;
        for (int index = 0; index < candles.size(); index++) {
            Kline candle = candles.get(index);
            Double regressionValue = index >= LaplaceKernelRegressionCalculator.BANDWIDTH - 1
                    ? regression.at(candles, index) : null;
            Double atrValue = index >= Atr14Calculator.PERIOD ? atr.at(candles, index) : null;
            Double slope = regressionValue != null && previousRegression != null
                    ? regressionValue - previousRegression : null;
            Double normalized = slope != null && atrValue != null ? slope / atrValue : null;
            result.add(new PreparedLaplaceCandle(candle.getOpenTime(), candle.getCloseTime(),
                    candle.getOpen(), candle.getHigh(), candle.getLow(), candle.getClose(), candle.getVolume(),
                    regressionValue, atrValue, slope, normalized));
            if (regressionValue != null) {
                previousRegression = regressionValue;
            }
        }
        return List.copyOf(result);
    }
}
