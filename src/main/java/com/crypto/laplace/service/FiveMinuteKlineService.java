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
        Instant previousEarliestCloseTime = null;
        while (true) {
            Instant previousPageEnd = pageEnd;
            List<Kline> page = closedThrough(client.getKlines(symbol, INTERVAL, MAX_BINANCE_LIMIT, pageEnd), cutoffInclusive);
            if (page.isEmpty()) break;
            for (Kline candle : page) {
                Kline existing = pages.putIfAbsent(candle.getCloseTime(), candle);
                if (existing != null && !sameCandle(existing, candle)) throw new IllegalStateException("Conflicting duplicate 5m candle");
            }
            Instant earliest = page.getFirst().getCloseTime();
            if (Objects.equals(previousEarliestCloseTime, earliest)) throw new IllegalStateException("5m pagination repeated same page");
            if (!earliest.isAfter(afterExclusive)) break;
            if (page.size() < MAX_BINANCE_LIMIT) throw new IllegalStateException("Missing closed 5m candles before first returned candle");
            Instant nextPageEnd = page.getFirst().getOpenTime().minusMillis(1);
            if (!nextPageEnd.isBefore(previousPageEnd)) throw new IllegalStateException("5m pagination did not make progress");
            previousEarliestCloseTime = earliest;
            pageEnd = nextPageEnd;
        }
        List<Kline> closed = new ArrayList<>(pages.values());
        List<Kline> range = closed.stream().filter(c -> c.getCloseTime().isAfter(afterExclusive)).toList();
        if (!range.isEmpty() && Duration.between(afterExclusive, range.getFirst().getCloseTime()).getSeconds() > 300) {
            throw new IllegalStateException("Missing closed 5m candles before first returned candle");
        }
        validateContinuity(range);
        Instant expectedLast = lastExpectedClosedFiveMinuteCandle(cutoffInclusive);
        if (range.isEmpty()) {
            if (afterExclusive.isBefore(expectedLast)) throw new IllegalStateException("Closed 5m range does not reach cutoff");
        } else if (!range.getLast().getCloseTime().equals(expectedLast)) {
            throw new IllegalStateException("Closed 5m range does not reach cutoff");
        }
        return range;
    }

    public Instant lastExpectedClosedFiveMinuteCandle(Instant cutoffInclusive) {
        long seconds = cutoffInclusive.getEpochSecond();
        return Instant.ofEpochSecond(seconds - Math.floorMod(seconds, 300));
    }

    private boolean sameCandle(Kline a, Kline b) {
        return Objects.equals(a.getOpenTime(), b.getOpenTime()) && Objects.equals(a.getOpen(), b.getOpen())
                && Objects.equals(a.getHigh(), b.getHigh()) && Objects.equals(a.getLow(), b.getLow())
                && Objects.equals(a.getClose(), b.getClose()) && Objects.equals(a.getVolume(), b.getVolume())
                && Objects.equals(a.getQuoteAssetVolume(), b.getQuoteAssetVolume());
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
