package com.crypto.laplace.execution;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class Kural4MarketContextService {
    private final BinanceFuturesClient client;
    private final ConcurrentHashMap<Instant, CompletableFuture<Kural4MarketContext>> cache = new ConcurrentHashMap<>();
    public Kural4MarketContextService(BinanceFuturesClient client) { this.client = client; }

    public Kural4MarketContext contextAt(Instant signalTime) {
        if (signalTime == null) return null;
        CompletableFuture<Kural4MarketContext> future = cache.computeIfAbsent(signalTime, time -> {
            CompletableFuture<Kural4MarketContext> loading = new CompletableFuture<>();
            try {
                Kural4MarketContext loaded = load(time);
                if (loaded == null) loading.completeExceptionally(new ContextUnavailableException());
                else loading.complete(loaded);
            } catch (RuntimeException failure) {
                loading.completeExceptionally(failure);
            }
            return loading;
        });
        try {
            return future.join();
        } catch (RuntimeException unavailable) {
            cache.remove(signalTime, future);
            return null;
        }
    }

    private Kural4MarketContext load(Instant signalTime) {
        try {
            List<Kline> completed = client.getKlines("BTCUSDT", "5m", 100).stream()
                    .filter(c -> c.getCloseTime() != null && !c.getCloseTime().isAfter(signalTime)
                            && !Boolean.FALSE.equals(c.getClosed()))
                    .sorted(Comparator.comparing(Kline::getCloseTime)).toList();
            if (completed.size() < 12) return null;
            Kline current = completed.get(completed.size() - 1);
            Kline at15 = atOrBefore(completed, signalTime.minus(15, ChronoUnit.MINUTES));
            Kline at30 = atOrBefore(completed, signalTime.minus(30, ChronoUnit.MINUTES));
            if (at15 == null || at30 == null) return null;
            List<Kline> last12 = completed.subList(completed.size() - 12, completed.size());
            double low = last12.stream().mapToDouble(c -> c.getLow().doubleValue()).min().orElse(Double.NaN);
            double high = last12.stream().mapToDouble(c -> c.getHigh().doubleValue()).max().orElse(Double.NaN);
            if (high <= low) return null;
            double close = current.getClose().doubleValue();
            return new Kural4MarketContext(signalTime, current.getCloseTime(), close,
                    (close / at15.getClose().doubleValue() - 1) * 100,
                    (close / at30.getClose().doubleValue() - 1) * 100,
                    (close - low) / (high - low), completed.size());
        } catch (RuntimeException unavailable) { return null; }
    }
    private Kline atOrBefore(List<Kline> candles, Instant time) {
        for (int i = candles.size() - 1; i >= 0; i--) if (!candles.get(i).getCloseTime().isAfter(time)) return candles.get(i);
        return null;
    }
    private static final class ContextUnavailableException extends RuntimeException { }
}
