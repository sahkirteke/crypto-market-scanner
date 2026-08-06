package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FiveMinuteKlineService {
    static final String INTERVAL = "5m";
    static final int MAX_BINANCE_LIMIT = 1000;
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

    public List<Kline> loadLastClosedThrough(String symbol, Instant cutoffInclusive, int requiredCount) {
        if (requiredCount <= 0 || requiredCount > MAX_BINANCE_LIMIT) throw new IllegalArgumentException("Invalid required 5m candle count");
        List<Kline> closed = closedThrough(client.getKlines(symbol, INTERVAL, MAX_BINANCE_LIMIT), cutoffInclusive);
        if (closed.size() < requiredCount) throw new IllegalStateException("Insufficient closed 5m history");
        return List.copyOf(closed.subList(closed.size() - requiredCount, closed.size()));
    }

    public List<Kline> loadClosedRange(String symbol, Instant afterExclusive, Instant cutoffInclusive) {
        TreeMap<Instant,Kline> pages = new TreeMap<>();
        Instant pageEnd = cutoffInclusive;
        while (true) {
            List<Kline> page = closedThrough(client.getKlines(symbol, INTERVAL, MAX_BINANCE_LIMIT, pageEnd), cutoffInclusive);
            if (page.isEmpty()) break;
            page.forEach(c -> pages.putIfAbsent(c.getCloseTime(), c));
            Instant earliest = page.getFirst().getCloseTime();
            if (!earliest.isAfter(afterExclusive)) break;
            if (page.size() < MAX_BINANCE_LIMIT) throw new IllegalStateException("Missing closed 5m candles before first returned candle");
            pageEnd = earliest.minusMillis(1);
        }
        List<Kline> closed = new ArrayList<>(pages.values());
        List<Kline> range = closed.stream().filter(c -> c.getCloseTime().isAfter(afterExclusive)).toList();
        if (!range.isEmpty() && Duration.between(afterExclusive, range.getFirst().getCloseTime()).getSeconds() > 300) {
            throw new IllegalStateException("Missing closed 5m candles before first returned candle");
        }
        validateContinuity(range);
        return range;
    }

    private List<Kline> closedThrough(List<Kline> raw, Instant cutoffInclusive) {
        TreeMap<Instant, Kline> unique = new TreeMap<>();
        for (Kline candle : raw) {
            if (candle == null || candle.getOpenTime() == null || candle.getCloseTime() == null
                    || candle.getCloseTime().isAfter(cutoffInclusive) || !Boolean.TRUE.equals(candle.getClosed())) continue;
            validateComplete(candle);
            unique.putIfAbsent(candle.getCloseTime(), candle);
        }
        return new ArrayList<>(unique.values());
    }

    private void validateContinuity(List<Kline> candles) {
        for (int i = 1; i < candles.size(); i++) if (Duration.between(candles.get(i - 1).getCloseTime(), candles.get(i).getCloseTime()).getSeconds() > 300)
            throw new IllegalStateException("Missing closed 5m candles");
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
