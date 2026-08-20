package com.crypto.laplace.service;

import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Two-candle, market-wide shock state machine for INVERTED_TRUE emergency exits only. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaplaceMarketShockService {
    static final BigDecimal CANDIDATE_THRESHOLD = new BigDecimal("75");
    static final BigDecimal CONTINUATION_THRESHOLD = new BigDecimal("60");
    static final BigDecimal OPPOSITE_THRESHOLD = new BigDecimal("0.50");
    static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
    private final StartupMarketUniverseService universe;
    private final LaplaceMarketShockDataService data;
    private final LaplacePaperPositionRepository positions;
    private final LaplacePaperExecutionService execution;
    private Candidate pending;

    public synchronized void evaluate() {
        Set<String> symbols = universe.symbols();
        if (symbols.isEmpty()) return;
        Set<String> requested = new LinkedHashSet<>(symbols);
        requested.add("BTCUSDT"); requested.add("ETHUSDT");
        Map<String, List<Kline>> candles = new LinkedHashMap<>();
        requested.forEach(symbol -> candles.put(symbol, data.loadClosed(symbol)));
        Instant latest = latestTime(candles.get("BTCUSDT"));
        if (latest == null || candleAt(candles.get("ETHUSDT"), latest) == null) return;

        if (pending != null) {
            Instant expected = pending.closeTime().plus(FIVE_MINUTES);
            if (latest.equals(pending.closeTime())) return;
            if (latest.equals(expected)) confirm(candles, symbols, latest);
            else if (latest.isAfter(expected)) {
                log.info("LAPLACE_MARKET_SHOCK_CANDIDATE_EXPIRED direction={} candidateCandleCloseTime={} latestCandleCloseTime={}",
                        pending.direction(), pending.closeTime(), latest);
                pending = null;
                detect(candles, symbols, latest);
            }
            return;
        }
        detect(candles, symbols, latest);
    }

    private void detect(Map<String, List<Kline>> candles, Set<String> symbols, Instant latest) {
        Map<String, BigDecimal> returns = new LinkedHashMap<>();
        Map<String, BigDecimal> closes = new LinkedHashMap<>();
        for (String symbol : symbols) {
            Kline current = candleAt(candles.get(symbol), latest);
            Kline prior = candleAt(candles.get(symbol), latest.minus(FIFTEEN_MINUTES));
            if (current != null && prior != null) {
                returns.put(symbol, ratio(current.getClose(), prior.getClose()));
                closes.put(symbol, current.getClose());
            }
        }
        BigDecimal btc = returnAt(candles.get("BTCUSDT"), latest, FIFTEEN_MINUTES);
        BigDecimal eth = returnAt(candles.get("ETHUSDT"), latest, FIFTEEN_MINUTES);
        if (btc == null || eth == null || returns.isEmpty()) return;
        BigDecimal up = pct(returns.values().stream().filter(v -> v.signum() > 0).count(), returns.size());
        BigDecimal down = pct(returns.values().stream().filter(v -> v.signum() < 0).count(), returns.size());
        LaplaceMarketShockDirection direction = null; BigDecimal breadth = null;
        if (up.compareTo(CANDIDATE_THRESHOLD) >= 0 && btc.signum() > 0 && eth.signum() > 0) { direction = LaplaceMarketShockDirection.UP; breadth = up; }
        else if (down.compareTo(CANDIDATE_THRESHOLD) >= 0 && btc.signum() < 0 && eth.signum() < 0) { direction = LaplaceMarketShockDirection.DOWN; breadth = down; }
        if (direction == null) return;
        pending = new Candidate(direction, latest, breadth, btc, eth, Map.copyOf(closes));
        log.info("LAPLACE_MARKET_SHOCK_CANDIDATE direction={} candidateCandleCloseTime={} breadthPct={} validSymbolCount={} totalUniverseCount={} btc15mReturn={} eth15mReturn={}",
                direction, latest, breadth, returns.size(), symbols.size(), btc, eth);
    }

    private void confirm(Map<String, List<Kline>> candles, Set<String> symbols, Instant confirmationTime) {
        Candidate candidate = pending;
        pending = null;
        if (candleAt(candles.get("BTCUSDT"), confirmationTime) == null
                || candleAt(candles.get("ETHUSDT"), confirmationTime) == null) return;
        int valid = 0, continuing = 0;
        for (String symbol : symbols) {
            BigDecimal candidateClose = candidate.closes().get(symbol);
            Kline confirmation = candleAt(candles.get(symbol), confirmationTime);
            if (candidateClose == null || confirmation == null) continue;
            valid++;
            int sign = confirmation.getClose().compareTo(candidateClose);
            if ((candidate.direction() == LaplaceMarketShockDirection.UP && sign > 0)
                    || (candidate.direction() == LaplaceMarketShockDirection.DOWN && sign < 0)) continuing++;
        }
        if (valid == 0) return;
        BigDecimal breadth = pct(continuing, valid);
        if (breadth.compareTo(CONTINUATION_THRESHOLD) < 0) {
            log.info("LAPLACE_MARKET_SHOCK_TRANSIENT direction={} candidateCandleCloseTime={} confirmationCandleCloseTime={} continuationBreadthPct={}",
                    candidate.direction(), candidate.closeTime(), confirmationTime, breadth);
            return;
        }
        closeOpposite(candidate, confirmationTime, breadth);
    }

    private void closeOpposite(Candidate candidate, Instant confirmationTime, BigDecimal continuationBreadth) {
        List<LaplacePaperPositionEntity> open = positions.findByStrategyAndStatus(
                LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN);
        PositionSide oppositeSide = candidate.direction() == LaplaceMarketShockDirection.UP ? PositionSide.SHORT : PositionSide.LONG;
        List<LaplacePaperPositionEntity> opposite = open.stream().filter(p -> p.getSide() == oppositeSide).toList();
        BigDecimal ratio = open.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(opposite.size())
                .divide(BigDecimal.valueOf(open.size()), 8, RoundingMode.HALF_UP);
        log.info("LAPLACE_MARKET_SHOCK_PERSISTENT direction={} candidateBreadthPct={} continuationBreadthPct={} totalOpenPositions={} oppositeOpenPositions={} oppositeRatio={}",
                candidate.direction(), candidate.breadth(), continuationBreadth, open.size(), opposite.size(), ratio);
        if (open.size() < 10 || ratio.compareTo(OPPOSITE_THRESHOLD) < 0) return;
        LaplaceMarketShockContext context = new LaplaceMarketShockContext(candidate.direction(), candidate.closeTime(),
                confirmationTime, candidate.breadth(), continuationBreadth, open.size(), opposite.size(), ratio);
        for (LaplacePaperPositionEntity position : opposite) {
            try {
                if (execution.closeForPersistentMarketShock(position.getId(), context)) execution.drainTradeEvents();
            } catch (RuntimeException failure) {
                log.error("LAPLACE_MARKET_SHOCK_EXIT_FAILED positionId={} symbol={} error={}",
                        position.getId(), position.getSymbol(), failure.getMessage(), failure);
            }
        }
    }

    public synchronized void clearRuntimeState() { pending = null; }
    Candidate pendingCandidate() { return pending; }
    private Instant latestTime(List<Kline> values) { return values == null ? null : values.stream().filter(c -> Boolean.TRUE.equals(c.getClosed())).map(Kline::getCloseTime).filter(Objects::nonNull).max(Instant::compareTo).orElse(null); }
    private Kline candleAt(List<Kline> values, Instant time) { return values == null ? null : values.stream().filter(c -> Boolean.TRUE.equals(c.getClosed())).filter(c -> time.equals(c.getCloseTime())).findFirst().orElse(null); }
    private BigDecimal returnAt(List<Kline> values, Instant latest, Duration distance) {
        Kline current = candleAt(values, latest), prior = candleAt(values, latest.minus(distance));
        return current == null || prior == null ? null : ratio(current.getClose(), prior.getClose());
    }
    private BigDecimal ratio(BigDecimal current, BigDecimal prior) { return current.divide(prior, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE); }
    private BigDecimal pct(long count, long total) { return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP); }
    record Candidate(LaplaceMarketShockDirection direction, Instant closeTime, BigDecimal breadth,
                     BigDecimal btcReturn, BigDecimal ethReturn, Map<String, BigDecimal> closes) { }
}
