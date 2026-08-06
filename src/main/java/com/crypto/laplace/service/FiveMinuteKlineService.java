package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FiveMinuteKlineService {
    static final String INTERVAL = "5m";
    private final BinanceFuturesClient client;

    public Kline loadLatestClosed(String symbol) {
        Instant now = Instant.now();
        return client.getKlines(symbol, INTERVAL, 3).stream()
                .filter(candle -> candle != null && candle.getOpenTime() != null && candle.getCloseTime() != null)
                .filter(candle -> !candle.getCloseTime().isAfter(now) && !Boolean.FALSE.equals(candle.getClosed()))
                .peek(this::validate)
                .max(Comparator.comparing(Kline::getCloseTime))
                .orElseThrow(() -> new IllegalStateException("No closed 5m candle available"));
    }

    public List<Kline> loadClosedThrough(String symbol, Instant cutoffInclusive, int limit) {
        if (cutoffInclusive == null || limit <= 0) throw new IllegalArgumentException("Invalid 5m history request");
        TreeMap<Instant, Kline> unique = new TreeMap<>();
        for (Kline candle : client.getKlines(symbol, INTERVAL, limit)) {
            if (candle == null || candle.getOpenTime() == null || candle.getCloseTime() == null
                    || candle.getCloseTime().isAfter(cutoffInclusive) || Boolean.FALSE.equals(candle.getClosed())) continue;
            validateComplete(candle);
            if (unique.putIfAbsent(candle.getCloseTime(), candle) != null) {
                throw new IllegalStateException("Duplicate closed 5m candle");
            }
        }
        return List.copyOf(unique.values());
    }

    private void validate(Kline candle) {
        if (candle.getHigh() == null || candle.getLow() == null) {
            throw new IllegalStateException("5m candle high/low unavailable");
        }
    }

    private void validateComplete(Kline c) {
        validate(c);
        if (c.getOpen() == null || c.getClose() == null || c.getVolume() == null || c.getQuoteAssetVolume() == null
                || c.getOpen().signum() <= 0 || c.getHigh().signum() <= 0 || c.getLow().signum() <= 0
                || c.getClose().signum() <= 0 || c.getVolume().signum() < 0 || c.getQuoteAssetVolume().signum() < 0) {
            throw new IllegalStateException("Invalid closed 5m OHLCV candle");
        }
    }
}
