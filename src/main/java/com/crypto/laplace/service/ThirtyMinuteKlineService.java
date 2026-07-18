package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ThirtyMinuteKlineService {
    public static final String INTERVAL = "30m";
    private static final Duration STEP = Duration.ofMinutes(30);

    private final BinanceFuturesClient client;
    private final LaplaceStrategyProperties properties;

    public List<Kline> loadStartupClosed(String symbol) {
        int required = properties.getLaplace().getStartupClosedCandleCount();
        List<Kline> closed = loadAndValidate(symbol);
        if (closed.size() < required) {
            throw new IllegalStateException("Insufficient startup closed candles: " + closed.size());
        }
        return List.copyOf(closed.subList(closed.size() - required, closed.size()));
    }

    public List<Kline> loadClosed(String symbol) {
        List<Kline> closed = loadAndValidate(symbol);
        int required = properties.getLaplace().getStartupClosedCandleCount();
        if (closed.size() < required) {
            throw new IllegalStateException("Insufficient closed candles: " + closed.size());
        }
        return closed;
    }

    private List<Kline> loadAndValidate(String symbol) {
        Instant now = Instant.now();
        List<Kline> raw = client.getKlines(symbol, INTERVAL, properties.getLaplace().getKlineLimit());
        if (raw == null) {
            throw new IllegalStateException("Null kline response");
        }
        Map<Instant, Kline> unique = new TreeMap<>();
        for (Kline candle : raw) {
            if (candle == null || candle.getOpenTime() == null || candle.getCloseTime() == null) {
                throw new IllegalStateException("Candle time unavailable");
            }
            boolean closed = !candle.getCloseTime().isAfter(now) && !Boolean.FALSE.equals(candle.getClosed());
            if (!closed) {
                continue;
            }
            requireMarketFields(candle);
            if (unique.putIfAbsent(candle.getOpenTime(), candle) != null) {
                throw new IllegalStateException("Duplicate candle");
            }
        }
        List<Kline> result = new ArrayList<>(unique.values());
        for (int index = 1; index < result.size(); index++) {
            if (!STEP.equals(Duration.between(result.get(index - 1).getOpenTime(), result.get(index).getOpenTime()))) {
                throw new IllegalStateException("Data gap");
            }
        }
        return List.copyOf(result);
    }

    private void requireMarketFields(Kline candle) {
        if (candle.getOpen() == null || candle.getHigh() == null || candle.getLow() == null
                || candle.getClose() == null || candle.getVolume() == null) {
            throw new IllegalStateException("Candle OHLCV unavailable");
        }
    }
}
