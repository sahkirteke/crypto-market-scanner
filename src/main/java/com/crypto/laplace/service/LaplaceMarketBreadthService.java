package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.model.LaplaceMarketBreadthSnapshot;
import com.crypto.laplace.pool.LaplaceCoinPoolService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class LaplaceMarketBreadthService {
    private final BinanceFuturesClient client;
    private final LaplaceCoinPoolService coinPool;
    private final ConcurrentHashMap<Instant, LaplaceMarketBreadthSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile Instant cycleStartedAt = Instant.EPOCH;
    private volatile Set<String> cycleUniverse = Set.of();

    public synchronized void beginCycle(Instant startedAt, Set<String> activeSymbols) {
        cycleStartedAt = startedAt;
        cycleUniverse = Set.copyOf(activeSymbols);
        snapshots.clear();
    }

    public LaplaceMarketBreadthSnapshot snapshot(Instant signalCandleCloseTime) {
        return snapshots.computeIfAbsent(signalCandleCloseTime, this::calculate);
    }

    LaplaceMarketBreadthSnapshot calculate(Instant referenceTime) {
        int positive2h=0, valid2h=0, positive4h=0, valid4h=0;
        for (String symbol : cycleUniverse.stream().sorted().toList()) {
          try {
            List<Kline> candles = client.getKlines(symbol, "5m", 60).stream()
                    .filter(c -> c.getCloseTime() != null && !c.getCloseTime().isAfter(referenceTime))
                    .sorted(Comparator.comparing(Kline::getCloseTime)).toList();
            if (candles.isEmpty()) continue;
            Kline current = candles.getLast();
            Kline twoHoursAgo = exactCandle(candles, current.getCloseTime().minus(Duration.ofHours(2)));
            Kline fourHoursAgo = exactCandle(candles, current.getCloseTime().minus(Duration.ofHours(4)));
            if (valid(current, twoHoursAgo)) { valid2h++; if (returnPct(current, twoHoursAgo) > 0.0) positive2h++; }
            if (valid(current, fourHoursAgo)) { valid4h++; if (returnPct(current, fourHoursAgo) > 0.0) positive4h++; }
          } catch (RuntimeException error) {
            log.warn("LAPLACE_MARKET_BREADTH_SYMBOL_SKIPPED symbol={} referenceTime={} reason={}", symbol, referenceTime, error.getMessage());
          }
        }
        double b2=valid2h==0?Double.NaN:100.0*positive2h/valid2h;
        double b4=valid4h==0?Double.NaN:100.0*positive4h/valid4h;
        var result=new LaplaceMarketBreadthSnapshot(cycleStartedAt,referenceTime,b2,b4,positive2h,valid2h,positive4h,valid4h);
        log.info("LAPLACE_MARKET_BREADTH referenceTime={} marketBreadth2h={} marketBreadth4h={} positiveCoinCount2h={} validCoinCount2h={} positiveCoinCount4h={} validCoinCount4h={}",referenceTime,b2,b4,positive2h,valid2h,positive4h,valid4h);
        snapshots.keySet().removeIf(t -> t.isBefore(referenceTime.minus(Duration.ofHours(2))));
        return result;
    }

    private static Kline exactCandle(List<Kline> candles, Instant closeTime) {
        return candles.stream().filter(c -> closeTime.equals(c.getCloseTime())).findFirst().orElse(null);
    }
    private static boolean valid(Kline current,Kline past){return current.getClose()!=null&&past!=null&&past.getClose()!=null&&past.getClose().signum()>0;}
    private static double returnPct(Kline current,Kline past){return current.getClose().divide(past.getClose(),12,java.math.RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).doubleValue();}
}
