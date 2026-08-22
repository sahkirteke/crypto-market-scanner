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
    private final LaplaceRuntimeService runtime;
    private Candidate pending;

    public synchronized void evaluate() {
        Set<String> symbols = universe.symbols();
        if (symbols.isEmpty()) return;
        List<Kline> btcCandles = data.loadClosed("BTCUSDT");
        Instant evaluationAnchor = latestTime(btcCandles);
        if (evaluationAnchor == null) return;

        if (pending != null) {
            Instant expected = pending.closeTime().plus(FIVE_MINUTES);
            int timing = evaluationAnchor.compareTo(expected);
            if (timing < 0) return;
            if (timing > 0) {
                log.info("LAPLACE_MARKET_SHOCK_CANDIDATE_EXPIRED direction={} candidateCandleCloseTime={} expectedConfirmationCandleCloseTime={} evaluationAnchorCandleCloseTime={} reason=CONFIRMATION_CYCLE_MISSED",
                        pending.direction(), pending.closeTime(), expected, evaluationAnchor);
                pending = null;
                return;
            }
            Map<String, List<Kline>> candles = loadSnapshotInputs(pending.symbols(), btcCandles);
            confirm(candles, evaluationAnchor);
            return;
        }

        Map<String, List<Kline>> candles = loadSnapshotInputs(symbols, btcCandles);
        detect(candles, symbols, evaluationAnchor);
    }

    private Map<String, List<Kline>> loadSnapshotInputs(Set<String> symbols, List<Kline> btcCandles) {
        Set<String> requested = new LinkedHashSet<>(symbols);
        requested.add("BTCUSDT"); requested.add("ETHUSDT");
        Map<String, List<Kline>> candles = new LinkedHashMap<>();
        candles.put("BTCUSDT", btcCandles);
        for (String symbol : requested) {
            if (!"BTCUSDT".equals(symbol)) candles.put(symbol, data.loadClosed(symbol));
        }
        return candles;
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
        long upCount = returns.values().stream().filter(v -> v.signum() > 0).count();
        long downCount = returns.values().stream().filter(v -> v.signum() < 0).count();
        BigDecimal up = pct(upCount, symbols.size());
        BigDecimal down = pct(downCount, symbols.size());
        LaplaceMarketShockDirection direction = null; BigDecimal breadth = null;
        if (up.compareTo(CANDIDATE_THRESHOLD) >= 0 && btc.signum() > 0 && eth.signum() > 0) { direction = LaplaceMarketShockDirection.UP; breadth = up; }
        else if (down.compareTo(CANDIDATE_THRESHOLD) >= 0 && btc.signum() < 0 && eth.signum() < 0) { direction = LaplaceMarketShockDirection.DOWN; breadth = down; }
        if (direction == null) return;
        long directionalCount = direction == LaplaceMarketShockDirection.UP ? upCount : downCount;
        pending = new Candidate(direction, latest, breadth, btc, eth, Map.copyOf(closes), Set.copyOf(symbols));
        log.info("LAPLACE_MARKET_SHOCK_CANDIDATE direction={} candidateCandleCloseTime={} candidateBreadthPct={} candidateDirectionalCount={} candidateValidSymbolCount={} totalUniverseCount={} btc15mReturn={} eth15mReturn={}",
                direction, latest, breadth, directionalCount, returns.size(), symbols.size(), btc, eth);
    }

    private void confirm(Map<String, List<Kline>> candles, Instant confirmationTime) {
        Candidate candidate = pending;
        pending = null;
        if (candleAt(candles.get("BTCUSDT"), confirmationTime) == null
                || candleAt(candles.get("ETHUSDT"), confirmationTime) == null) return;
        int valid = 0, continuing = 0;
        for (String symbol : candidate.symbols()) {
            BigDecimal candidateClose = candidate.closes().get(symbol);
            Kline confirmation = candleAt(candles.get(symbol), confirmationTime);
            if (candidateClose == null || confirmation == null) continue;
            valid++;
            int sign = confirmation.getClose().compareTo(candidateClose);
            if ((candidate.direction() == LaplaceMarketShockDirection.UP && sign > 0)
                    || (candidate.direction() == LaplaceMarketShockDirection.DOWN && sign < 0)) continuing++;
        }
        if (valid == 0) return;
        BigDecimal breadth = pct(continuing, candidate.symbols().size());
        if (breadth.compareTo(CONTINUATION_THRESHOLD) < 0) {
            log.info("LAPLACE_MARKET_SHOCK_TRANSIENT direction={} candidateCandleCloseTime={} confirmationCandleCloseTime={} continuationBreadthPct={} continuingSymbolCount={} confirmationValidSymbolCount={} totalUniverseCount={}",
                    candidate.direction(), candidate.closeTime(), confirmationTime, breadth, continuing, valid, candidate.symbols().size());
            return;
        }
        closeOpposite(candidate, confirmationTime, breadth);
    }

    private void closeOpposite(Candidate candidate, Instant confirmationTime, BigDecimal continuationBreadth) {
        String sessionId = runtime.current().getSessionId();
        List<LaplacePaperPositionEntity> open = positions.findBySessionIdAndStatus(sessionId, LaplacePositionStatus.OPEN);
        PositionSide oppositeSide = candidate.direction() == LaplaceMarketShockDirection.UP ? PositionSide.SHORT : PositionSide.LONG;
        List<LaplacePaperPositionEntity> opposite = open.stream().filter(p -> p.getSide() == oppositeSide).toList();
        BigDecimal ratio = open.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(opposite.size())
                .divide(BigDecimal.valueOf(open.size()), 8, RoundingMode.HALF_UP);
        log.info("LAPLACE_MARKET_SHOCK_PERSISTENT direction={} candidateBreadthPct={} continuationBreadthPct={} sessionId={} totalOpenPositions={} oppositeOpenPositions={} oppositeRatio={}",
                candidate.direction(), candidate.breadth(), continuationBreadth, sessionId, open.size(), opposite.size(), ratio);
        if (open.size() < 10 || ratio.compareTo(OPPOSITE_THRESHOLD) < 0) return;
        if (!runtime.isActive()) {
            log.info("LAPLACE_MARKET_SHOCK_EXIT_SKIPPED_RUNTIME_NOT_ACTIVE direction={} candidateCandleCloseTime={} confirmationCandleCloseTime={}",
                    candidate.direction(), candidate.closeTime(), confirmationTime);
            return;
        }
        LaplaceMarketShockContext context = new LaplaceMarketShockContext(candidate.direction(), candidate.closeTime(),
                confirmationTime, candidate.breadth(), continuationBreadth, open.size(), opposite.size(), ratio);
        boolean closedAny = false;
        for (LaplacePaperPositionEntity position : opposite) {
            if (!runtime.isActive()) {
                log.info("LAPLACE_MARKET_SHOCK_EXIT_SKIPPED_RUNTIME_NOT_ACTIVE direction={} candidateCandleCloseTime={} confirmationCandleCloseTime={}",
                        candidate.direction(), candidate.closeTime(), confirmationTime);
                break;
            }
            try {
                closedAny |= execution.closeForPersistentMarketShock(position.getId(), context);
            } catch (RuntimeException failure) {
                log.error("LAPLACE_MARKET_SHOCK_EXIT_FAILED positionId={} symbol={} error={}",
                        position.getId(), position.getSymbol(), failure.getMessage(), failure);
            }
        }
        if (closedAny) execution.drainTradeEvents();
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
                     BigDecimal btcReturn, BigDecimal ethReturn, Map<String, BigDecimal> closes,
                     Set<String> symbols) { }
}
