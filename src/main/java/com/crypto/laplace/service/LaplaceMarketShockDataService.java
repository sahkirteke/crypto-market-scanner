package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Isolated loader which never exposes the current, incomplete 5m candle. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaplaceMarketShockDataService {
    private static final int REQUIRED_LIMIT = 6;
    private final BinanceFuturesClient client;
    private final Clock clock;

    public List<Kline> loadClosed(String symbol) {
        try {
            Instant now = clock.instant();
            return client.getKlines(symbol, "5m", REQUIRED_LIMIT).stream()
                    .filter(c -> Boolean.TRUE.equals(c.getClosed()))
                    .filter(c -> c.getCloseTime() != null && !c.getCloseTime().isAfter(now))
                    .filter(c -> c.getClose() != null && c.getClose().signum() > 0)
                    .sorted(Comparator.comparing(Kline::getCloseTime))
                    .toList();
        } catch (RuntimeException failure) {
            log.error("LAPLACE_MARKET_SHOCK_DATA_FAILED symbol={} error={}", symbol, failure.getMessage(), failure);
            return List.of();
        }
    }
}
