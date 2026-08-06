package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.time.Instant;
import java.util.Comparator;
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

    private void validate(Kline candle) {
        if (candle.getHigh() == null || candle.getLow() == null) {
            throw new IllegalStateException("5m candle high/low unavailable");
        }
    }
}
