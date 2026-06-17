package com.crypto.paper.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.paper.log.SymbolTradeJsonlLogService;
import com.crypto.paper.log.SymbolTradeJsonlLogService.PaperExitContext;
import com.crypto.paper.model.KlineCandle;
import com.crypto.paper.model.PaperExitEvaluationResult;
import com.crypto.paper.model.PaperExitReason;
import com.crypto.paper.model.PaperPositionEventType;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.entity.PaperPositionEventEntity;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.crypto.persistence.repository.PaperPositionEventRepository;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.IndicatorService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExitEngineService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PCT_SCALE = 8;
    private static final int QUANTITY_SCALE = 12;

    private final PaperPositionRepository paperPositionRepository;
    private final BinanceFuturesClient binanceFuturesClient;
    private final ScannerProperties scannerProperties;

    @Autowired(required = false) private PaperPositionEventRepository eventRepository;
    @Autowired(required = false) private JsonlDecisionLogService jsonlDecisionLogService;
    @Autowired(required = false) private IndicatorService indicatorService;
    @Autowired(required = false) private MarketScanRunRepository marketScanRunRepository;
    @Autowired(required = false) private CoinScanResultRepository coinScanResultRepository;
    @Autowired(required = false) private ObjectMapper objectMapper;
    @Autowired(required = false) private SymbolTradeJsonlLogService symbolTradeJsonlLogService;

    private int lastEvaluationEventCount;
    private int lastEvaluationSkippedErrors;

    public List<PaperPositionEntity> evaluateOpenPositions() {
        if (!booleanValue(paperExitConfig().getEnabled(), true)) {
            log.info("PAPER_EXIT_DISABLED");
            return List.of();
        }
        lastEvaluationSkippedErrors = 0;
        List<PaperPositionEntity> openPositions = paperPositionRepository.findByStatusInOrderByOpenedAtDesc(activeStatuses());
        List<PaperPositionEntity> evaluated = new ArrayList<>();
        for (PaperPositionEntity position : openPositions) {
            PaperPositionEntity evaluatedPosition = evaluateWithLastClosedCandle(position);
            if (evaluatedPosition != null) {
                evaluated.add(paperPositionRepository.save(evaluatedPosition));
            }
        }
        long closedCount = evaluated.stream().filter(p -> p.getStatus() == PaperPositionStatus.CLOSED).count();
        log.info("PAPER_EXIT_EVALUATION_DONE openChecked={} closed={} skippedErrors={}",
                openPositions.size(), closedCount, lastEvaluationSkippedErrors);
        return evaluated.stream().filter(p -> p.getStatus() == PaperPositionStatus.CLOSED).toList();
    }

    public PaperExitEvaluationResult evaluateOpenPositionsWithInterval(String interval) {
        if (!booleanValue(paperExitConfig().getEnabled(), true)) {
            log.info("PAPER_EXIT_DISABLED");
            return PaperExitEvaluationResult.builder()
                    .checkedCount(0)
                    .eventCount(0)
                    .closedCount(0)
                    .skippedErrorCount(0)
                    .closedPositions(List.of())
                    .build();
        }
        String effectiveInterval = interval == null || interval.isBlank()
                ? defaultString(paperExitConfig().getIntrabarInterval(), "5m")
                : interval;
        int limit = Math.max(1, intValue(paperExitConfig().getIntrabarKlineLimit(), 3));
        lastEvaluationEventCount = 0;
        List<PaperPositionEntity> openPositions = paperPositionRepository.findByStatusInOrderByOpenedAtDesc(activeStatuses());
        List<PaperPositionEntity> closedPositions = new ArrayList<>();
        int checked = 0;
        int skippedErrors = 0;
        for (PaperPositionEntity position : openPositions) {
            try {
                List<Kline> rawKlines = binanceFuturesClient.getKlines(position.getSymbol(), effectiveInterval, limit);
                Kline lastClosed = lastClosedKline(rawKlines);
                if (lastClosed == null) {
                    log.warn("PAPER_EXIT_INTRABAR_CANDLE_UNAVAILABLE symbol={} interval={}", position.getSymbol(), effectiveInterval);
                    continue;
                }
                checked++;
                PaperPositionEntity evaluated = evaluatePositionWithCandle(position, KlineCandle.builder()
                        .openTime(lastClosed.getOpenTime())
                        .closeTime(lastClosed.getCloseTime())
                        .open(lastClosed.getOpen())
                        .high(lastClosed.getHigh())
                        .low(lastClosed.getLow())
                        .close(lastClosed.getClose())
                        .interval(effectiveInterval)
                        .build(), effectiveInterval);
                paperPositionRepository.save(evaluated);
                if (evaluated.getStatus() == PaperPositionStatus.CLOSED) {
                    closedPositions.add(evaluated);
                }
            } catch (Exception exception) {
                skippedErrors++;
                log.warn("PAPER_EXIT_INTRABAR_POSITION_FAILED id={} symbol={} interval={} reason={}",
                        position.getId(), position.getSymbol(), effectiveInterval, exception.getMessage());
            }
        }
        log.info("AUTO_PAPER_EXIT_EVALUATION_COMPLETED checked={} closed={} skippedErrors={}",
                checked, closedPositions.size(), skippedErrors);
        return PaperExitEvaluationResult.builder()
                .checkedCount(checked)
                .eventCount(lastEvaluationEventCount)
                .closedCount(closedPositions.size())
                .skippedErrorCount(skippedErrors)
                .closedPositions(closedPositions)
                .build();
    }

    public PaperPositionEntity evaluatePositionWithCandle(PaperPositionEntity position, KlineCandle candle, String interval) {
        if (position == null || candle == null || candle.getCloseTime() == null) {
            return position;
        }
        if (!hasRequiredExitCandle(candle.getHigh(), candle.getLow(), candle.getClose())) {
            log.warn("PAPER_EXIT_CANDLE_DATA_NOT_READY id={} symbol={} interval={} candleCloseTime={}",
                    position.getId(), position.getSymbol(), interval, IstanbulTimeUtil.format(candle.getCloseTime()));
            return position;
        }
        String effectiveInterval = interval == null || interval.isBlank() ? defaultString(candle.getInterval(), "5m") : interval;
        if (shouldSkipCandleBeforePositionOpened(position, candle.getOpenTime(), candle.getCloseTime(), effectiveInterval)) {
            return position;
        }
        if (position.getLastExitCandleCloseTime() != null && !candle.getCloseTime().isAfter(position.getLastExitCandleCloseTime())) {
            log.info("PAPER_EXIT_CANDLE_SKIPPED_ALREADY_PROCESSED id={} symbol={} candleCloseTime={}",
                    position.getId(), position.getSymbol(), IstanbulTimeUtil.format(candle.getCloseTime()));
            return position;
        }

        Instant now = Instant.now();
        updatePricePath(position, candle.getHigh(), candle.getLow(), candle.getClose());
        updateHoldingDuration(position, now);
        position.setBarsInPosition(intValue(position.getBarsInPosition(), 0) + 1);
        position.setLastCheckedAt(now);
        updateUnrealized(position, candle.getClose());

        IntrabarEventContext candleStartContext = new IntrabarEventContext(position, candle, effectiveInterval);
        PaperExitReason earlyExitReason = resolveEarlyExit(position, candle.getClose());
        if (earlyExitReason != null) {
            closeRemaining(position, candle.getClose(), earlyExitReason, candle.getCloseTime(), candleStartContext);
            position.setLastExitCandleCloseTime(candle.getCloseTime());
            log.info("PAPER_POSITION_EARLY_EXIT id={} symbol={} interval={} reason={} currentPnlPct={} maxFavorableMovePct={}",
                    position.getId(), position.getSymbol(), effectiveInterval, earlyExitReason, currentPnlPct(position, candle.getClose()), position.getMaxFavorableMovePct());
            return position;
        }

        BigDecimal stopBefore = position.getCurrentStop();
        boolean tp1HitBefore = Boolean.TRUE.equals(position.getTp1Hit());
        boolean tp2HitBefore = Boolean.TRUE.equals(position.getTp2Hit());
        boolean trailingActiveBefore = Boolean.TRUE.equals(position.getTrailingActive());
        boolean stopTouched = stopHit(position, candle.getHigh(), candle.getLow(), stopBefore);
        boolean tpTouched = tp1Hit(position, candle.getHigh(), candle.getLow(), tp1HitBefore)
                || tp2Hit(position, candle.getHigh(), candle.getLow(), tp2HitBefore);
        if (stopTouched) {
            if (tpTouched) {
                log.info("PAPER_EXIT_CONSERVATIVE_STOP_FIRST id={} symbol={} interval={}",
                        position.getId(), position.getSymbol(), effectiveInterval);
            }
            PaperExitReason reason = stopReason(position, tp1HitBefore, tp2HitBefore, trailingActiveBefore, stopBefore, candle.getCloseTime());
            closeRemaining(position, defaultBigDecimal(stopBefore, candle.getClose()), reason, candle.getCloseTime(), candleStartContext);
            position.setLastExitCandleCloseTime(candle.getCloseTime());
            log.info("PAPER_POSITION_EVALUATED id={} symbol={} interval={} candleHigh={} candleLow={} status={} remainingPct={}",
                    position.getId(), position.getSymbol(), effectiveInterval, candle.getHigh(), candle.getLow(), position.getStatus(), position.getRemainingPositionPct());
            return position;
        }

        if (tp1Hit(position, candle.getHigh(), candle.getLow(), tp1HitBefore)) {
            IntrabarEventContext tp1Context = new IntrabarEventContext(position, candle, effectiveInterval);
            position.setTp1Hit(true);
            PartialCloseResult partial = partialClose(position, position.getTp1(), tpClosePct(exitTpConfig().getTp1CloseRatio()),
                    PaperPositionEventType.PARTIAL_TP1, candle.getCloseTime(), tp1Context);
            position.setTrailingActive(true);
            position.setTrailingActivatedAtBarCloseTime(candle.getCloseTime());
            updateBreakEvenStop(position, candle.getCloseTime(), new IntrabarEventContext(position, candle, effectiveInterval));
            writeSymbolTradePartialExit(position, partial, PaperExitReason.PARTIAL_TP1, tp1Context);
        }
        if (tp2Hit(position, candle.getHigh(), candle.getLow(), tp2HitBefore)) {
            IntrabarEventContext tp2Context = new IntrabarEventContext(position, candle, effectiveInterval);
            position.setTp2Hit(true);
            PartialCloseResult partial = partialClose(position, position.getTp2(), tpClosePct(exitTpConfig().getTp2CloseRatio()),
                    PaperPositionEventType.PARTIAL_TP2, candle.getCloseTime(), tp2Context);
            moveStopToTp1AfterTp2(position, candle.getCloseTime(), tp2Context);
            writeSymbolTradePartialExit(position, partial, PaperExitReason.PARTIAL_TP2, tp2Context);
        }
        updateStatus(position);
        updateTrailing(position, candle.getHigh(), candle.getLow(), fallbackAtr(position), candle.getCloseTime(),
                new IntrabarEventContext(position, candle, effectiveInterval));
        position.setLastExitCandleCloseTime(candle.getCloseTime());
        log.info("PAPER_POSITION_EVALUATED id={} symbol={} interval={} candleHigh={} candleLow={} status={} remainingPct={}",
                position.getId(), position.getSymbol(), effectiveInterval, candle.getHigh(), candle.getLow(), position.getStatus(), position.getRemainingPositionPct());
        return position;
    }

    public PaperPositionEntity evaluatePosition(PaperPositionEntity position, BigDecimal currentPrice) {
        if (currentPrice == null) {
            log.warn("PAPER_EXIT_PRICE_MISSING symbol={}", position.getSymbol());
            return null;
        }
        Instant now = Instant.now();
        return evaluatePositionOnCandle(position, now, now, currentPrice, currentPrice, currentPrice, fallbackAtr(position));
    }

    public PaperPositionEntity evaluatePositionOnCandle(PaperPositionEntity position, Instant candleOpenTime, Instant candleCloseTime,
            BigDecimal candleHigh, BigDecimal candleLow, BigDecimal candleClose, BigDecimal atr14) {
        if (!hasRequiredExitCandle(candleHigh, candleLow, candleClose)) {
            log.warn("PAPER_EXIT_CANDLE_DATA_NOT_READY id={} symbol={} interval={} candleCloseTime={}",
                    position == null ? null : position.getId(), position == null ? null : position.getSymbol(), "1h", IstanbulTimeUtil.format(candleCloseTime));
            return position;
        }
        if (shouldSkipCandleBeforePositionOpened(position, candleOpenTime, candleCloseTime, "1h")) {
            return position;
        }
        Instant now = Instant.now();
        updatePricePath(position, candleHigh, candleLow, candleClose);
        updateHoldingDuration(position, now);
        position.setBarsInPosition(intValue(position.getBarsInPosition(), 0) + 1);
        position.setLastCheckedAt(now);
        updateUnrealized(position, candleClose);
        IntrabarEventContext oneHourContext = new IntrabarEventContext(position, KlineCandle.builder()
                .openTime(candleOpenTime)
                .closeTime(candleCloseTime)
                .high(candleHigh)
                .low(candleLow)
                .close(candleClose)
                .interval("1h")
                .build(), "1h");
        PaperExitReason earlyExitReason = resolveEarlyExit(position, candleClose);
        if (earlyExitReason != null) {
            closeRemaining(position, candleClose, earlyExitReason, candleCloseTime, oneHourContext);
            log.info("PAPER_POSITION_EARLY_EXIT id={} symbol={} interval={} reason={} currentPnlPct={} maxFavorableMovePct={}",
                    position.getId(), position.getSymbol(), "1h", earlyExitReason, currentPnlPct(position, candleClose), position.getMaxFavorableMovePct());
            return position;
        }

        if (position.getCurrentStop() == null && position.getTp1() == null && position.getTp2() == null) {
            PaperExitReason legacy = resolveLegacyExitReason(position, calculateUnrealizedPnlPct(position, candleClose));
            if (legacy != null) {
                closeRemaining(position, candleClose, legacy, candleCloseTime, oneHourContext);
            }
            return position;
        }

        if (stopHit(position, candleHigh, candleLow)) {
            PaperExitReason reason = stopReason(position, Boolean.TRUE.equals(position.getTp1Hit()), Boolean.TRUE.equals(position.getTp2Hit()), Boolean.TRUE.equals(position.getTrailingActive()), position.getCurrentStop(), candleCloseTime);
            closeRemaining(position, defaultBigDecimal(position.getCurrentStop(), candleClose), reason, candleCloseTime, oneHourContext);
            return position;
        }
        if (tp1Hit(position, candleHigh, candleLow)) {
            IntrabarEventContext tp1Context = oneHourContext;
            position.setTp1Hit(true);
            PartialCloseResult partial = partialClose(position, position.getTp1(), tpClosePct(exitTpConfig().getTp1CloseRatio()), PaperPositionEventType.PARTIAL_TP1, candleCloseTime, tp1Context);
            position.setTrailingActive(true);
            position.setTrailingActivatedAtBarCloseTime(candleCloseTime);
            updateBreakEvenStop(position, candleCloseTime, oneHourContext);
            writeSymbolTradePartialExit(position, partial, PaperExitReason.PARTIAL_TP1, tp1Context);
        }
        if (tp2Hit(position, candleHigh, candleLow)) {
            IntrabarEventContext tp2Context = oneHourContext;
            position.setTp2Hit(true);
            PartialCloseResult partial = partialClose(position, position.getTp2(), tpClosePct(exitTpConfig().getTp2CloseRatio()), PaperPositionEventType.PARTIAL_TP2, candleCloseTime, tp2Context);
            moveStopToTp1AfterTp2(position, candleCloseTime, tp2Context);
            writeSymbolTradePartialExit(position, partial, PaperExitReason.PARTIAL_TP2, tp2Context);
        }
        updateStatus(position);
        updateTrailing(position, candleHigh, candleLow, atr14, candleCloseTime, oneHourContext);
        if (timeStop(position) && shouldCloseTimeStop(position, candleClose)) {
            closeRemaining(position, candleClose, PaperExitReason.TIME_STOP, candleCloseTime, oneHourContext);
            return position;
        }
        PaperExitReason strategicExit = resolveStrategicExit(position);
        if (strategicExit != null) {
            closeRemaining(position, candleClose, strategicExit, candleCloseTime, oneHourContext);
        }
        log.info("PAPER_POSITION_EVALUATED id={} symbol={} side={} close={} status={} remainingPct={}", position.getId(), position.getSymbol(), position.getSide(), candleClose, position.getStatus(), position.getRemainingPositionPct());
        return position;
    }

    public BigDecimal calculateUnrealizedPnlPct(PaperPositionEntity position, BigDecimal currentPrice) {
        return rawPnlPct(position.getSide(), pnlEntryPrice(position), currentPrice);
    }

    public BigDecimal calculateRealizedPnlUsdt(PaperPositionEntity position, BigDecimal pnlPct) {
        BigDecimal notionalUsdt = position.getNotionalUsdt() == null ? BigDecimal.ZERO : position.getNotionalUsdt();
        return notionalUsdt.multiply(pnlPct).divide(ONE_HUNDRED, PCT_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNetPnlPct(PaperPositionEntity position, BigDecimal exitPrice) {
        return realized(position, exitPrice, ONE_HUNDRED, "TAKER").net();
    }


    private PaperExitReason resolveLegacyExitReason(PaperPositionEntity position, BigDecimal pnlPct) {
        BigDecimal stopLossPct = defaultBigDecimal(position.getStopLossPct(), paperExitConfig().getStopLossPct());
        BigDecimal takeProfitPct = defaultBigDecimal(position.getTakeProfitPct(), paperExitConfig().getTakeProfitPct());
        if (pnlPct.compareTo(stopLossPct.negate()) <= 0) return PaperExitReason.STOP_LOSS;
        if (pnlPct.compareTo(takeProfitPct) >= 0) return PaperExitReason.TAKE_PROFIT;
        if (timeStop(position) && shouldCloseTimeStop(position, position.getCurrentPrice())) return PaperExitReason.TIME_STOP;
        return null;
    }


    public boolean shouldSignalInvalidate(PaperPositionEntity p, BigDecimal close1h, BigDecimal ema20, BigDecimal macdHist, MarketRegime marketRegime) {
        if (p.getSide() == PositionSide.SHORT) {
            return gt(close1h, ema20) && gt(macdHist, BigDecimal.ZERO) && marketRegime != MarketRegime.RISK_OFF;
        }
        return lt(close1h, ema20) && lt(macdHist, BigDecimal.ZERO) && marketRegime != MarketRegime.RISK_ON;
    }

    public boolean shouldMarketRegimeExit(PaperPositionEntity p, MarketRegime marketRegime, Integer shortScore) {
        if (p.getSide() == PositionSide.LONG) {
            return marketRegime == MarketRegime.PANIC;
        }
        return marketRegime == MarketRegime.RISK_ON && intValue(shortScore, 0) < 80;
    }

    public boolean shouldOppositeSignalExit(PaperPositionEntity p, CoinClassification classification, Integer longScore, Integer shortScore) {
        if (p.getSide() == PositionSide.LONG) {
            return classification == CoinClassification.STRONG_SHORT && intValue(shortScore, 0) >= 85;
        }
        return classification == CoinClassification.STRONG_LONG && intValue(longScore, 0) >= 85;
    }

    private PaperPositionEntity evaluateWithLastClosedCandle(PaperPositionEntity position) {
        try {
            List<Kline> rawKlines = binanceFuturesClient.getKlines(position.getSymbol(), "1h", 250);
            List<Kline> closed = (rawKlines == null ? List.<Kline>of() : rawKlines).stream()
                    .filter(k -> Boolean.TRUE.equals(k.getClosed()))
                    .toList();
            if (closed.isEmpty()) return null;
            Kline last = closed.get(closed.size() - 1);
            if (shouldSkipCandleBeforePositionOpened(position, last.getOpenTime(), last.getCloseTime(), "1h")) {
                return position;
            }
            BigDecimal atr = fallbackAtr(position);
            TechnicalSnapshot snapshot = null;
            if (indicatorService != null && closed.size() >= 220) {
                snapshot = indicatorService.calculateOneHour(position.getSymbol(), closed);
                atr = defaultBigDecimal(snapshot.getAtr14(), atr);
            }
            PaperPositionEntity evaluated = evaluatePositionOnCandle(position, last.getOpenTime(), last.getCloseTime(), last.getHigh(), last.getLow(), last.getClose(), atr);
            if (evaluated.getStatus() != PaperPositionStatus.CLOSED && snapshot != null && marketScanRunRepository != null) {
                MarketScanRunEntity run = marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc("COMPLETED").orElse(null);
                if (run != null && shouldSignalInvalidate(evaluated, snapshot.getClose(), snapshot.getEma20(), snapshot.getMacdHist(), run.getMarketRegime())) {
                    closeRemaining(evaluated, last.getClose(), PaperExitReason.SIGNAL_INVALIDATION, last.getCloseTime(), new IntrabarEventContext(evaluated, KlineCandle.builder().openTime(last.getOpenTime()).closeTime(last.getCloseTime()).high(last.getHigh()).low(last.getLow()).close(last.getClose()).interval("1h").build(), "1h"));
                }
            }
            return evaluated;
        } catch (Exception exception) {
            lastEvaluationSkippedErrors++;
            log.warn("PAPER_EXIT_CANDLE_UNAVAILABLE symbol={} reason={}", position.getSymbol(), exception.getMessage());
            return null;
        }
    }

    private Kline lastClosedKline(List<Kline> rawKlines) {
        List<Kline> closed = (rawKlines == null ? List.<Kline>of() : rawKlines).stream()
                .filter(kline -> kline != null && Boolean.TRUE.equals(kline.getClosed()))
                .toList();
        return closed.isEmpty() ? null : closed.get(closed.size() - 1);
    }

    private boolean hasRequiredExitCandle(BigDecimal high, BigDecimal low, BigDecimal close) {
        return high != null && low != null && close != null;
    }

    private boolean canUseTrailingStop(PaperPositionEntity position, boolean tp1HitBefore, boolean trailingActiveBefore, Instant candleCloseTime) {
        if (!tp1HitBefore || !trailingActiveBefore || position == null || position.getTrailingActivatedAtBarCloseTime() == null) {
            return false;
        }
        return candleCloseTime == null || candleCloseTime.isAfter(position.getTrailingActivatedAtBarCloseTime());
    }

    private boolean stopHit(PaperPositionEntity p, BigDecimal high, BigDecimal low) {
        return stopHit(p, high, low, p.getCurrentStop());
    }

    private boolean stopHit(PaperPositionEntity p, BigDecimal high, BigDecimal low, BigDecimal stop) {
        if (stop == null) return false;
        return p.getSide() == PositionSide.SHORT ? ge(high, stop) : le(low, stop);
    }

    private boolean tp1Hit(PaperPositionEntity p, BigDecimal high, BigDecimal low) {
        return tp1Hit(p, high, low, Boolean.TRUE.equals(p.getTp1Hit()));
    }

    private boolean tp1Hit(PaperPositionEntity p, BigDecimal high, BigDecimal low, boolean alreadyHit) {
        return !alreadyHit && p.getTp1() != null && (p.getSide() == PositionSide.SHORT ? le(low, p.getTp1()) : ge(high, p.getTp1()));
    }

    private boolean tp2Hit(PaperPositionEntity p, BigDecimal high, BigDecimal low) {
        return tp2Hit(p, high, low, Boolean.TRUE.equals(p.getTp2Hit()));
    }

    private boolean tp2Hit(PaperPositionEntity p, BigDecimal high, BigDecimal low, boolean alreadyHit) {
        return !alreadyHit && p.getTp2() != null && (p.getSide() == PositionSide.SHORT ? le(low, p.getTp2()) : ge(high, p.getTp2()));
    }

    private PartialCloseResult partialClose(PaperPositionEntity p, BigDecimal price, BigDecimal closePct, PaperPositionEventType type, Instant time) {
        return partialClose(p, price, closePct, type, time, null);
    }

    private PartialCloseResult partialClose(PaperPositionEntity p, BigDecimal price, BigDecimal closePct, PaperPositionEventType type, Instant time, IntrabarEventContext context) {
        BigDecimal remainingBefore = defaultBigDecimal(p.getRemainingPositionPct(), new BigDecimal("100"));
        Boolean tp1HitBefore = context == null ? (type == PaperPositionEventType.PARTIAL_TP1 ? false : p.getTp1Hit()) : context.tp1HitBefore();
        Boolean tp2HitBefore = context == null ? (type == PaperPositionEventType.PARTIAL_TP2 ? false : p.getTp2Hit()) : context.tp2HitBefore();
        Boolean trailingActiveBefore = context == null ? p.getTrailingActive() : context.trailingActiveBefore();
        BigDecimal remaining = remainingBefore.subtract(closePct).max(BigDecimal.ZERO);
        p.setRemainingPositionPct(remaining);
        Realized realized = realized(p, price, closePct);
        mergeRealized(p, realized, price);
        Instant eventTime = time == null ? Instant.now() : time;
        BigDecimal adjusted = adjustedExit(p.getSide(), price);
        writeEvent(p, type, eventTime, price, adjusted, closePct, realized, type.name(), context);
        log.info("{} positionId={} symbol={} closedPct={} remainingPct={} price={}", type, p.getId(), p.getSymbol(), closePct, remaining, price);
        return new PartialCloseResult(price, adjusted, closePct, remainingBefore, remaining, realized, eventTime,
                tp1HitBefore, tp2HitBefore, trailingActiveBefore);
    }

    private void updateBreakEvenStop(PaperPositionEntity p, Instant time, IntrabarEventContext context) {
        if (!booleanValue(breakEvenConfig().getEnabled(), true)) return;
        BigDecimal oldStop = p.getCurrentStop();
        BigDecimal entry = defaultBigDecimal(p.getEntryPriceAdjusted(), p.getEntryPrice());
        BigDecimal bufferPct = booleanValue(breakEvenConfig().getIncludeFees(), true)
                ? scannerProperties.getPaperRisk().getFeeBufferPct().multiply(ONE_HUNDRED)
                : BigDecimal.ZERO;
        bufferPct = bufferPct.add(breakEvenConfig().getExtraSlippagePct());
        BigDecimal buffer = entry.multiply(bufferPct).divide(ONE_HUNDRED, PCT_SCALE + 4, RoundingMode.HALF_UP);
        BigDecimal newStop = p.getSide() == PositionSide.SHORT
                ? entry.subtract(buffer)
                : entry.add(buffer);
        p.setCurrentStop(newStop);
        if (oldStop == null || newStop.compareTo(oldStop) != 0) {
            writeEvent(p, PaperPositionEventType.STOP_UPDATED, time, newStop, null, null, null, "STOP_UPDATED", context);
        }
    }


    private PaperExitReason stopReason(PaperPositionEntity position, boolean tp1HitBefore, boolean tp2HitBefore, boolean trailingActiveBefore, BigDecimal stop, Instant candleCloseTime) {
        if (tp2HitBefore && booleanValue(afterTp2Config().getMoveSlToTp1(), true) && stop != null && position.getTp1() != null && stop.compareTo(position.getTp1()) == 0) {
            return PaperExitReason.valueOf(afterTp2Config().getExitReason());
        }
        return canUseTrailingStop(position, tp1HitBefore, trailingActiveBefore, candleCloseTime)
                ? PaperExitReason.TRAILING_STOP
                : PaperExitReason.STOP_LOSS;
    }

    private void moveStopToTp1AfterTp2(PaperPositionEntity p, Instant time, IntrabarEventContext context) {
        if (!booleanValue(afterTp2Config().getMoveSlToTp1(), true) || p.getTp1() == null) return;
        BigDecimal oldStop = p.getCurrentStop();
        p.setCurrentStop(p.getTp1());
        if (oldStop == null || p.getTp1().compareTo(oldStop) != 0) {
            writeEvent(p, PaperPositionEventType.STOP_UPDATED, time, p.getTp1(), null, null, null, "STOP_MOVED_TO_TP1_AFTER_TP2", context);
        }
    }

    private void updateTrailing(PaperPositionEntity p, BigDecimal high, BigDecimal low, BigDecimal atr14, Instant candleCloseTime) {
        updateTrailing(p, high, low, atr14, candleCloseTime, null);
    }

    private void updateTrailing(PaperPositionEntity p, BigDecimal high, BigDecimal low, BigDecimal atr14, Instant candleCloseTime, IntrabarEventContext context) {
        if (!Boolean.TRUE.equals(p.getTrailingActive()) || p.getTrailingActivatedAtBarCloseTime() == null) return;
        BigDecimal trailingDistance = defaultBigDecimal(atr14, fallbackAtr(p)).multiply(scannerProperties.getPaperRisk().getTrailingAtrMultiplier());
        BigDecimal oldStop = p.getCurrentStop();
        if (p.getSide() == PositionSide.SHORT) {
            p.setLowestPriceSinceEntry(min(defaultBigDecimal(p.getLowestPriceSinceEntry(), p.getEntryPrice()), low));
            BigDecimal candidateStop = p.getLowestPriceSinceEntry().add(trailingDistance);
            if (Boolean.TRUE.equals(p.getTp2Hit()) && p.getTp1() != null) {
                candidateStop = min(candidateStop, p.getTp1());
            }
            p.setCurrentStop(oldStop == null ? candidateStop : min(oldStop, candidateStop));
        } else {
            p.setHighestPriceSinceEntry(max(defaultBigDecimal(p.getHighestPriceSinceEntry(), p.getEntryPrice()), high));
            BigDecimal candidateStop = p.getHighestPriceSinceEntry().subtract(trailingDistance);
            if (Boolean.TRUE.equals(p.getTp2Hit()) && p.getTp1() != null) {
                candidateStop = max(candidateStop, p.getTp1());
            }
            p.setCurrentStop(oldStop == null ? candidateStop : max(oldStop, candidateStop));
        }
        if (oldStop == null || p.getCurrentStop().compareTo(oldStop) != 0) {
            writeEvent(p, PaperPositionEventType.TRAILING_UPDATED, candleCloseTime, p.getCurrentStop(), null, null, null, "TRAILING_UPDATED", context);
        }
    }

    private boolean timeStop(PaperPositionEntity p) {
        int timeStopMinutes = intValue(p.getTimeStopMinutes(), intValue(paperExitConfig().getTimeStopMinutes(), 240));
        return p.getMinutesHeld() != null && p.getMinutesHeld() >= timeStopMinutes;
    }

    private boolean shouldCloseTimeStop(PaperPositionEntity p, BigDecimal close) {
        if (!booleanValue(paperExitConfig().getTimeStopCloseOnlyIfNonPositive(), true)) {
            return true;
        }
        return calculateUnrealizedPnlPct(p, close).compareTo(BigDecimal.ZERO) <= 0;
    }

    private PaperExitReason resolveEarlyExit(PaperPositionEntity p, BigDecimal currentPrice) {
        ScannerProperties.EarlyExit config = earlyExitConfig();
        if (!booleanValue(config.getEnabled(), true)) return null;
        if (booleanValue(config.getOnlyBeforeTp1(), true) && Boolean.TRUE.equals(p.getTp1Hit())) return null;
        BigDecimal currentPnlPct = currentPnlPct(p, currentPrice);
        BigDecimal maxFavorable = defaultBigDecimal(p.getMaxFavorableMovePct(), BigDecimal.ZERO);
        ScannerProperties.TimeNegative timeNegative = config.getTimeNegative() == null ? new ScannerProperties.TimeNegative() : config.getTimeNegative();
        if (booleanValue(timeNegative.getEnabled(), true)
                && intValue(p.getMinutesHeld(), 0) >= intValue(timeNegative.getMinutes(), 15)
                && (!booleanValue(timeNegative.getRequireNegativePnl(), true) || currentPnlPct.compareTo(BigDecimal.ZERO) < 0)
                && maxFavorable.compareTo(timeNegative.getMaxFavorablePct()) < 0) {
            return PaperExitReason.valueOf(timeNegative.getExitReason());
        }
        ScannerProperties.AdversePct adversePct = config.getAdversePct() == null ? new ScannerProperties.AdversePct() : config.getAdversePct();
        if (booleanValue(adversePct.getEnabled(), true)
                && currentPnlPct.compareTo(adversePct.getAdversePct()) <= 0
                && maxFavorable.compareTo(adversePct.getMaxFavorablePct()) < 0) {
            return PaperExitReason.valueOf(adversePct.getExitReason());
        }
        return null;
    }

    private BigDecimal currentPnlPct(PaperPositionEntity p, BigDecimal currentPrice) {
        return rawPnlPct(p.getSide(), defaultBigDecimal(p.getEntryPriceAdjusted(), p.getEntryPrice()), currentPrice);
    }

    private PaperExitReason resolveStrategicExit(PaperPositionEntity p) {
        if (marketScanRunRepository == null || coinScanResultRepository == null) return null;
        MarketScanRunEntity run = marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc("COMPLETED").orElse(null);
        if (run == null) return null;
        CoinScanResultEntity result = coinScanResultRepository.findFirstByScanRun_IdAndSymbolOrderByCreatedAtDesc(run.getId(), p.getSymbol()).orElse(null);
        if (shouldMarketRegimeExit(p, run.getMarketRegime(), result == null ? null : result.getShortScore())) return PaperExitReason.MARKET_REGIME_EXIT;
        if (result == null) return null;
        if (shouldOppositeSignalExit(p, result.getClassification(), result.getLongScore(), result.getShortScore())) return PaperExitReason.OPPOSITE_SIGNAL_EXIT;
        return null;
    }


    private boolean shouldSkipCandleBeforePositionOpened(PaperPositionEntity position, Instant candleOpenTime, Instant candleCloseTime, String interval) {
        if (position == null || position.getOpenedAt() == null || candleCloseTime == null
                || candleCloseTime.isAfter(position.getOpenedAt())) {
            return false;
        }
        log.info("PAPER_EXIT_CANDLE_SKIPPED_BEFORE_POSITION_OPEN positionId={} symbol={} openedAt={} candleCloseTime={} interval={}",
                position.getId(),
                position.getSymbol(),
                IstanbulTimeUtil.format(position.getOpenedAt()),
                IstanbulTimeUtil.format(candleCloseTime),
                interval);
        return true;
    }

    private Instant correctedCloseTime(PaperPositionEntity position, Instant requestedCloseTime) {
        Instant closeTime = requestedCloseTime == null ? Instant.now() : requestedCloseTime;
        if (position == null || position.getOpenedAt() == null || !closeTime.isBefore(position.getOpenedAt())) {
            return closeTime;
        }
        Instant corrected = Instant.now().isBefore(position.getOpenedAt()) ? position.getOpenedAt() : Instant.now();
        log.warn("PAPER_CLOSE_TIME_CORRECTED_BEFORE_OPEN positionId={} symbol={} openedAt={} requestedCloseTime={}",
                position.getId(),
                position.getSymbol(),
                IstanbulTimeUtil.format(position.getOpenedAt()),
                IstanbulTimeUtil.format(closeTime));
        return corrected;
    }

    private void closeRemaining(PaperPositionEntity p, BigDecimal exitPrice, PaperExitReason reason, Instant time) {
        closeRemaining(p, exitPrice, reason, time, null);
    }

    private void closeRemaining(PaperPositionEntity p, BigDecimal exitPrice, PaperExitReason reason, Instant time, IntrabarEventContext context) {
        BigDecimal closePct = defaultBigDecimal(p.getRemainingPositionPct(), new BigDecimal("100"));
        Instant closeTime = correctedCloseTime(p, time);
        Realized realized = realized(p, exitPrice, closePct, feeTypeForReason(reason));
        p.setStatus(PaperPositionStatus.CLOSED);
        p.setClosedAt(closeTime);
        p.setExitPrice(exitPrice);
        p.setExitPriceAdjusted(adjustedExit(p.getSide(), exitPrice));
        mergeRealized(p, realized, exitPrice);
        p.setRealizedPnlPct(p.getNetRealizedPnlPct());
        p.setExitReason(reason.name());
        p.setExitDetail("paper exit reason=" + reason.name());
        p.setRemainingPositionPct(BigDecimal.ZERO);
        writeEvent(p, PaperPositionEventType.valueOf(reason.name()), closeTime, exitPrice, p.getExitPriceAdjusted(), closePct, realized, reason.name(), context);
        writeEvent(p, PaperPositionEventType.CLOSED, closeTime, exitPrice, p.getExitPriceAdjusted(), closePct, realized, reason.name(), context);
        writeExitTradeLog(p, exitPrice, closePct, reason, context, realized);
        writeSymbolTradeExit(p, exitPrice, closePct, realized, reason, context);
        log.info("PAPER_POSITION_CLOSED closedAt={} id={} symbol={} side={} exitReason={} exitPrice={} pnlPct={}", IstanbulTimeUtil.format(p.getClosedAt()), p.getId(), p.getSymbol(), p.getSide(), reason, exitPrice, p.getRealizedPnlPct());
    }

    private void writeSymbolTradeExit(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal closePct, Realized realized, PaperExitReason reason, IntrabarEventContext context) {
        if (symbolTradeJsonlLogService == null || p.getStatus() != PaperPositionStatus.CLOSED || Boolean.TRUE.equals(p.getSymbolTradeFinalExitLogged()) || Boolean.TRUE.equals(p.getSymbolTradeExitLogged())) {
            return;
        }
        backfillSymbolTradeEntryIfMissing(p);
        BigDecimal closedPct = defaultBigDecimal(closePct, context == null ? ONE_HUNDRED : defaultBigDecimal(context.remainingPositionPctBefore(), ONE_HUNDRED));
        BigDecimal realizedNetWeighted = realized == null ? netWeightedForClose(p, exitPrice, closedPct) : realized.netWeighted();
        BigDecimal realizedPnlUsdt = realized == null ? netPnlForClose(p, exitPrice, closedPct) : realized.netPnl();
        PaperExitContext exitContext = PaperExitContext.builder()
                .exitPrice(exitPrice)
                .exitPriceAdjusted(p.getExitPriceAdjusted())
                .exitTime(p.getClosedAt())
                .exitSeq(nextSymbolTradeExitSeq(p))
                .exitReason(reason.name())
                .firstHit(firstHit(reason))
                .exitTrigger(exitTrigger(reason, context))
                .interval(context == null ? "1h" : context.interval())
                .closedPositionPct(closedPct)
                .remainingPositionPctBefore(context == null ? closedPct : context.remainingPositionPctBefore())
                .remainingPositionPctAfter(p.getRemainingPositionPct())
                .realizedPnlUsdt(realizedPnlUsdt)
                .realizedPnlPct(realizedNetWeighted)
                .rawRealizedPnlPct(realized == null ? rawWeightedForClose(p, exitPrice, closedPct) : realized.rawWeighted())
                .netRealizedPnlPct(realizedNetWeighted)
                .leveragedNetRealizedPnlPct(realized == null ? leveragedWeightedForClose(p, exitPrice, closedPct) : realized.leveragedWeighted())
                .initialBalance(tradingConfig().getInitialBalanceUsdt())
                .currentBalance(realized == null ? tradingConfig().getInitialBalanceUsdt().add(defaultBigDecimal(p.getRealizedPnlUsdt(), BigDecimal.ZERO)) : realized.currentBalance())
                .marginUsdt(marginUsdt())
                .closedQty(realized == null ? null : realized.closedQty())
                .remainingQty(realized == null ? null : realized.remainingQty())
                .grossPnl(realized == null ? null : realized.grossPnl())
                .entryFeePart(realized == null ? null : realized.entryFeePart())
                .exitFee(realized == null ? null : realized.exitFee())
                .totalFee(realized == null ? null : realized.totalFee())
                .feeType(realized == null ? feeTypeForReason(reason) : realized.feeType())
                .netPnl(realized == null ? null : realized.netPnl())
                .cumulativeRealizedPnl(realized == null ? p.getRealizedPnlUsdt() : realized.cumulativeRealizedPnl())
                .netPnlPctOnMargin(realized == null ? null : realized.leveraged())
                .netPnlPctOnNotional(realized == null ? null : realized.net())
                .tp1HitBefore(context == null ? p.getTp1Hit() : context.tp1HitBefore())
                .tp1HitAfter(p.getTp1Hit())
                .tp2HitBefore(context == null ? p.getTp2Hit() : context.tp2HitBefore())
                .tp2HitAfter(p.getTp2Hit())
                .trailingActiveBefore(context == null ? p.getTrailingActive() : context.trailingActiveBefore())
                .trailingActiveAfter(p.getTrailingActive())
                .candleOpenTime(context == null ? null : context.candle().getOpenTime())
                .candleCloseTime(context == null ? null : context.candle().getCloseTime())
                .candleHigh(context == null ? null : context.candle().getHigh())
                .candleLow(context == null ? null : context.candle().getLow())
                .candleClose(context == null ? null : context.candle().getClose())
                .build();
        if (symbolTradeJsonlLogService.logExit(p, exitContext)) {
            p.setSymbolTradeFinalExitLogged(true);
            p.setSymbolTradeExitLogged(true);
        }
    }

    private void writeSymbolTradePartialExit(PaperPositionEntity p, PartialCloseResult partial, PaperExitReason reason, IntrabarEventContext context) {
        if (symbolTradeJsonlLogService == null || partial == null) {
            return;
        }
        if ((reason == PaperExitReason.PARTIAL_TP1 && Boolean.TRUE.equals(p.getSymbolTradeTp1ExitLogged()))
                || (reason == PaperExitReason.PARTIAL_TP2 && Boolean.TRUE.equals(p.getSymbolTradeTp2ExitLogged()))) {
            return;
        }
        backfillSymbolTradeEntryIfMissing(p);
        PaperExitContext exitContext = PaperExitContext.builder()
                .exitPrice(partial.price())
                .exitPriceAdjusted(partial.adjustedPrice())
                .exitTime(partial.exitTime())
                .exitSeq(nextSymbolTradeExitSeq(p))
                .exitReason(reason.name())
                .firstHit(firstHit(reason))
                .exitTrigger(exitTrigger(reason, context))
                .interval(context == null ? "1h" : context.interval())
                .closedPositionPct(partial.closePct())
                .remainingPositionPctBefore(partial.remainingBefore())
                .remainingPositionPctAfter(partial.remainingAfter())
                .realizedPnlUsdt(partial.realized().netPnl())
                .realizedPnlPct(partial.realized().netWeighted())
                .rawRealizedPnlPct(partial.realized().rawWeighted())
                .netRealizedPnlPct(partial.realized().netWeighted())
                .leveragedNetRealizedPnlPct(partial.realized().leveragedWeighted())
                .initialBalance(tradingConfig().getInitialBalanceUsdt())
                .currentBalance(partial.realized().currentBalance())
                .marginUsdt(marginUsdt())
                .closedQty(partial.realized().closedQty())
                .remainingQty(partial.realized().remainingQty())
                .grossPnl(partial.realized().grossPnl())
                .entryFeePart(partial.realized().entryFeePart())
                .exitFee(partial.realized().exitFee())
                .totalFee(partial.realized().totalFee())
                .feeType(partial.realized().feeType())
                .netPnl(partial.realized().netPnl())
                .cumulativeRealizedPnl(partial.realized().cumulativeRealizedPnl())
                .netPnlPctOnMargin(partial.realized().leveraged())
                .netPnlPctOnNotional(partial.realized().net())
                .tp1HitBefore(partial.tp1HitBefore())
                .tp1HitAfter(p.getTp1Hit())
                .tp2HitBefore(partial.tp2HitBefore())
                .tp2HitAfter(p.getTp2Hit())
                .trailingActiveBefore(partial.trailingActiveBefore())
                .trailingActiveAfter(p.getTrailingActive())
                .candleOpenTime(context == null ? null : context.candle().getOpenTime())
                .candleCloseTime(context == null ? null : context.candle().getCloseTime())
                .candleHigh(context == null ? null : context.candle().getHigh())
                .candleLow(context == null ? null : context.candle().getLow())
                .candleClose(context == null ? null : context.candle().getClose())
                .build();
        if (symbolTradeJsonlLogService.logExit(p, exitContext)) {
            if (reason == PaperExitReason.PARTIAL_TP1) {
                p.setSymbolTradeTp1ExitLogged(true);
            } else if (reason == PaperExitReason.PARTIAL_TP2) {
                p.setSymbolTradeTp2ExitLogged(true);
            }
        }
    }

    private void backfillSymbolTradeEntryIfMissing(PaperPositionEntity p) {
        if (symbolTradeJsonlLogService == null || Boolean.TRUE.equals(p.getSymbolTradeEntryLogged())) {
            return;
        }
        if (symbolTradeJsonlLogService.logEntry(p)) {
            p.setSymbolTradeEntryLogged(true);
            log.info("SYMBOL_TRADE_ENTRY_BACKFILLED_BEFORE_EXIT symbol={} positionId={}", p.getSymbol(), p.getId());
        }
    }

    private int nextSymbolTradeExitSeq(PaperPositionEntity p) {
        int seq = 1;
        if (Boolean.TRUE.equals(p.getSymbolTradeTp1ExitLogged())) seq++;
        if (Boolean.TRUE.equals(p.getSymbolTradeTp2ExitLogged())) seq++;
        if (Boolean.TRUE.equals(p.getSymbolTradeFinalExitLogged()) || Boolean.TRUE.equals(p.getSymbolTradeExitLogged())) seq++;
        return seq;
    }

    private void writeExitTradeLog(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal closePct, PaperExitReason reason, IntrabarEventContext context, Realized realized) {
        if (jsonlDecisionLogService == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        Realized tradeRealized = realized == null ? realized(p, exitPrice, closePct, feeTypeForReason(reason)) : realized;
        payload.put("type", "EXIT");
        payload.put("positionId", p.getId() == null ? "" : p.getId());
        payload.put("scanRunId", p.getSourceScanRunId());
        payload.put("sourceScanType", p.getSourceScanType() == null ? "" : p.getSourceScanType().name());
        payload.put("sourceClassification", p.getSourceClassification() == null ? "" : p.getSourceClassification().name());
        payload.put("candidateId", p.getSourceCandidateId());
        payload.put("symbol", p.getSymbol());
        payload.put("time", p.getClosedAt());
        payload.put("side", p.getSide().name());
        payload.put("initialBalance", tradingConfig().getInitialBalanceUsdt());
        payload.put("currentBalance", tradeRealized.currentBalance());
        payload.put("marginUsdt", marginUsdt());
        payload.put("notionalUsdt", p.getNotionalUsdt());
        payload.put("leverage", p.getLeverage());
        payload.put("entryPrice", p.getEntryPrice());
        payload.put("entryPriceAdjusted", p.getEntryPriceAdjusted());
        payload.put("exitPrice", exitPrice);
        payload.put("exitPriceAdjusted", adjustedExit(p.getSide(), exitPrice));
        payload.put("qty", p.getQuantity());
        payload.put("quantity", p.getQuantity());
        payload.put("closedQty", tradeRealized.closedQty());
        payload.put("remainingQty", tradeRealized.remainingQty());
        payload.put("grossPnl", tradeRealized.grossPnl());
        payload.put("entryFeePart", tradeRealized.entryFeePart());
        payload.put("exitFee", tradeRealized.exitFee());
        payload.put("totalFee", tradeRealized.totalFee());
        payload.put("feeType", tradeRealized.feeType());
        payload.put("netPnl", tradeRealized.netPnl());
        payload.put("cumulativeRealizedPnl", tradeRealized.cumulativeRealizedPnl());
        payload.put("netPnlPctOnMargin", tradeRealized.leveraged());
        payload.put("netPnlPctOnNotional", tradeRealized.net());
        payload.put("realizedPnl", tradeRealized.netPnl());
        payload.put("rawPnlPct", p.getRawRealizedPnlPct());
        payload.put("netPnlPct", p.getNetRealizedPnlPct());
        payload.put("leveragedNetPnlPct", p.getLeveragedNetRealizedPnlPct());
        payload.put("exitReason", reason.name());
        payload.put("tp1", p.getTp1());
        payload.put("tp2", p.getTp2());
        payload.put("slPrice", stopPriceForTradeLog(p, exitPrice, reason));
        payload.put("stopLoss", p.getInitialStop());
        payload.put("currentStopLoss", p.getCurrentStop());
        payload.put("firstHit", firstHit(reason));
        payload.put("exitTrigger", exitTrigger(reason, context));
        payload.put("interval", context == null ? "" : context.interval());
        payload.put("openedAt", p.getOpenedAt());
        payload.put("closedAt", p.getClosedAt());
        payload.put("minutesHeld", minutesHeldForTradeLog(p));
        payload.put("barsInPosition", p.getBarsInPosition());
        if (context != null) {
            payload.put("candleOpenTime", context.candle().getOpenTime());
            payload.put("candleCloseTime", context.candle().getCloseTime());
            payload.put("candleHigh", context.candle().getHigh());
            payload.put("candleLow", context.candle().getLow());
            payload.put("candleClose", context.candle().getClose());
        }
        jsonlDecisionLogService.logPaperTrade(payload);
    }

    private BigDecimal realizedPnlForTradeLog(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal closePct) {
        if (p.getRealizedPnlUsdt() != null && closePct != null && closePct.compareTo(ONE_HUNDRED) < 0) {
            return p.getRealizedPnlUsdt();
        }
        if (p.getEntryPrice() == null || exitPrice == null || p.getQuantity() == null) {
            return p.getRealizedPnlUsdt();
        }
        BigDecimal diff = p.getSide() == PositionSide.SHORT ? p.getEntryPrice().subtract(exitPrice) : exitPrice.subtract(p.getEntryPrice());
        return diff.multiply(p.getQuantity()).stripTrailingZeros();
    }

    private BigDecimal stopPriceForTradeLog(PaperPositionEntity p, BigDecimal exitPrice, PaperExitReason reason) {
        return reason == PaperExitReason.STOP_LOSS || reason == PaperExitReason.TRAILING_STOP || reason == PaperExitReason.TRAILING_STOP_AT_TP1_AFTER_TP2
                ? exitPrice
                : p.getCurrentStop();
    }

    private String firstHit(PaperExitReason reason) {
        return switch (reason) {
            case STOP_LOSS -> "SL_FIRST";
            case TAKE_PROFIT, PARTIAL_TP1, PARTIAL_TP2 -> "TP_FIRST";
            case TRAILING_STOP, TRAILING_STOP_AT_TP1_AFTER_TP2 -> "TRAILING_FIRST";
            case TIME_STOP -> "TIME_STOP";
            case SIGNAL_INVALIDATION -> "SIGNAL_INVALIDATION";
            case MARKET_REGIME_EXIT -> "MARKET_REGIME_EXIT";
            case OPPOSITE_SIGNAL_EXIT -> "OPPOSITE_SIGNAL_EXIT";
            default -> reason.name();
        };
    }

    private String exitTrigger(PaperExitReason reason, IntrabarEventContext context) {
        String suffix = context == null || context.interval() == null || context.interval().isBlank() ? "1H" : context.interval().toUpperCase();
        return switch (reason) {
            case STOP_LOSS -> "SL_" + suffix;
            case TAKE_PROFIT, PARTIAL_TP1, PARTIAL_TP2 -> "TP_" + suffix;
            case TRAILING_STOP, TRAILING_STOP_AT_TP1_AFTER_TP2 -> "TRAILING_" + suffix;
            case TIME_STOP -> "TIME_STOP";
            case SIGNAL_INVALIDATION, MARKET_REGIME_EXIT, OPPOSITE_SIGNAL_EXIT -> "SCAN";
            default -> reason.name();
        };
    }

    private int minutesHeldForTradeLog(PaperPositionEntity p) {
        if (p.getOpenedAt() == null || p.getClosedAt() == null) {
            return intValue(p.getMinutesHeld(), 0);
        }
        long minutes = Math.max(0, Duration.between(p.getOpenedAt(), p.getClosedAt()).toMinutes());
        return Math.toIntExact(Math.min(minutes, Integer.MAX_VALUE));
    }

    private void mergeRealized(PaperPositionEntity p, Realized r, BigDecimal exitPrice) {
        p.setExitPrice(exitPrice);
        p.setRawRealizedPnlPct(defaultBigDecimal(p.getRawRealizedPnlPct(), BigDecimal.ZERO).add(r.rawWeighted()));
        p.setNetRealizedPnlPct(defaultBigDecimal(p.getNetRealizedPnlPct(), BigDecimal.ZERO).add(r.net()));
        p.setLeveragedNetRealizedPnlPct(defaultBigDecimal(p.getLeveragedNetRealizedPnlPct(), BigDecimal.ZERO).add(r.leveraged()));
        p.setRealizedPnlPct(p.getNetRealizedPnlPct());
        p.setRealizedPnlUsdt(defaultBigDecimal(p.getRealizedPnlUsdt(), BigDecimal.ZERO).add(r.netPnl()));
    }

    private BigDecimal rawWeightedForClose(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal pctClosed) {
        return realized(p, exitPrice, pctClosed, feeTypeForReason(null)).rawWeighted();
    }

    private BigDecimal netWeightedForClose(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal pctClosed) {
        return realized(p, exitPrice, pctClosed, feeTypeForReason(null)).net();
    }

    private BigDecimal leveragedWeightedForClose(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal pctClosed) {
        return realized(p, exitPrice, pctClosed, feeTypeForReason(null)).leveraged();
    }

    private BigDecimal netPnlForClose(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal pctClosed) {
        return realized(p, exitPrice, pctClosed, feeTypeForReason(null)).netPnl();
    }

    private Realized realized(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal pctClosed) {
        return realized(p, exitPrice, pctClosed, feeTypeForReason(null));
    }

    private Realized realized(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal pctClosed, String feeType) {
        BigDecimal closeRatio = weight(pctClosed);
        BigDecimal totalQty = defaultBigDecimal(p.getQuantity(), BigDecimal.ZERO);
        BigDecimal closedQty = totalQty.multiply(closeRatio).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal remainingQty = totalQty.subtract(closedQty).max(BigDecimal.ZERO);
        BigDecimal entryAdjusted = pnlEntryPrice(p);
        BigDecimal exitAdjusted = adjustedExit(p.getSide(), exitPrice);
        BigDecimal grossPnl = p.getSide() == PositionSide.SHORT
                ? closedQty.multiply(entryAdjusted.subtract(exitAdjusted))
                : closedQty.multiply(exitAdjusted.subtract(entryAdjusted));
        BigDecimal entryNotional = defaultBigDecimal(p.getNotionalUsdt(), entryAdjusted.multiply(totalQty));
        BigDecimal exitNotional = exitAdjusted.multiply(closedQty).abs();
        BigDecimal entryFeePart = entryNotional.multiply(entryFeeRate()).multiply(closeRatio);
        BigDecimal exitFee = exitNotional.multiply(feeRate(feeType));
        BigDecimal totalFee = entryFeePart.add(exitFee);
        BigDecimal netPnl = grossPnl.subtract(totalFee);
        BigDecimal closedNotional = entryNotional.multiply(closeRatio);
        BigDecimal closedMargin = marginUsdt().multiply(closeRatio);
        BigDecimal priceMovePct = rawPnlPct(p.getSide(), entryAdjusted, exitAdjusted);
        BigDecimal netPnlPctOnNotional = closedNotional.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : netPnl.divide(closedNotional, PCT_SCALE + 4, RoundingMode.HALF_UP).multiply(ONE_HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal netPnlPctOnMargin = closedMargin.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : netPnl.divide(closedMargin, PCT_SCALE + 4, RoundingMode.HALF_UP).multiply(ONE_HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal cumulativeRealizedPnl = defaultBigDecimal(p.getRealizedPnlUsdt(), BigDecimal.ZERO).add(netPnl);
        BigDecimal currentBalance = tradingConfig().getInitialBalanceUsdt().add(cumulativeRealizedPnl);
        return new Realized(priceMovePct, netPnlPctOnNotional, netPnlPctOnMargin, priceMovePct.multiply(closeRatio), netPnlPctOnNotional, netPnlPctOnMargin,
                closedQty, remainingQty, grossPnl, entryFeePart, exitFee, totalFee, netPnl, cumulativeRealizedPnl, currentBalance, feeType);
    }

    private BigDecimal weight(BigDecimal pctClosed) {
        return pctClosed.divide(ONE_HUNDRED, PCT_SCALE + 4, RoundingMode.HALF_UP);
    }

    private record Realized(BigDecimal raw, BigDecimal net, BigDecimal leveraged, BigDecimal rawWeighted, BigDecimal netWeighted, BigDecimal leveragedWeighted,
                            BigDecimal closedQty, BigDecimal remainingQty, BigDecimal grossPnl, BigDecimal entryFeePart, BigDecimal exitFee, BigDecimal totalFee,
                            BigDecimal netPnl, BigDecimal cumulativeRealizedPnl, BigDecimal currentBalance, String feeType) {}
    private record PartialCloseResult(BigDecimal price, BigDecimal adjustedPrice, BigDecimal closePct, BigDecimal remainingBefore,
                                      BigDecimal remainingAfter, Realized realized, Instant exitTime,
                                      Boolean tp1HitBefore, Boolean tp2HitBefore, Boolean trailingActiveBefore) {}

    private void writeEvent(PaperPositionEntity p, PaperPositionEventType type, Instant time, BigDecimal price, BigDecimal adjusted, BigDecimal closePct, Realized r, String reason) {
        writeEvent(p, type, time, price, adjusted, closePct, r, reason, null);
    }

    private void writeEvent(PaperPositionEntity p, PaperPositionEventType type, Instant time, BigDecimal price, BigDecimal adjusted, BigDecimal closePct, Realized r, String reason, IntrabarEventContext context) {
        lastEvaluationEventCount++;
        Map<String, Object> details = eventDetails(p, type, reason, r, context);
        saveEvent(p, type, time, price, adjusted, closePct, r, reason, details);
        if (jsonlDecisionLogService != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", type.name());
            payload.put("symbol", p.getSymbol());
            payload.put("side", p.getSide().name());
            payload.put("positionId", p.getId() == null ? "" : p.getId());
            payload.put("scanRunId", p.getSourceScanRunId());
            payload.put("sourceScanType", p.getSourceScanType() == null ? "" : p.getSourceScanType().name());
            payload.put("sourceClassification", p.getSourceClassification() == null ? "" : p.getSourceClassification().name());
            payload.put("candidateId", p.getSourceCandidateId());
            payload.put("exitPrice", price == null ? "" : price);
            payload.put("exitReason", reason == null ? "" : reason);
            payload.putAll(details);
            jsonlDecisionLogService.logPaper(payload);
        }
    }

    private PaperPositionEventEntity saveEvent(PaperPositionEntity p, PaperPositionEventType type, Instant time, BigDecimal price, BigDecimal adjusted, BigDecimal closePct, Realized r, String reason, Map<String, Object> details) {
        PaperPositionEventEntity event = PaperPositionEventEntity.builder()
                .position(p)
                .eventTimeUtc(time == null ? Instant.now() : time)
                .eventType(type)
                .price(price)
                .adjustedPrice(adjusted)
                .positionPctClosed(closePct)
                .rawPnlPct(r == null ? null : r.raw())
                .netPnlPct(r == null ? null : r.net())
                .leveragedNetPnlPct(r == null ? null : r.leveraged())
                .feePct(tradingConfig().getTakerFeeRate().multiply(BigDecimal.valueOf(200)))
                .slippagePct(costConfig().getSlippagePct().multiply(BigDecimal.valueOf(200)))
                .leverage(tradingConfig().getLeverage())
                .reason(reason)
                .detailsJson(toJson(details))
                .build();
        if (eventRepository == null) {
            return event;
        }
        PaperPositionEventEntity saved = eventRepository.save(event);
        return saved == null ? event : saved;
    }

    private Map<String, Object> eventDetails(PaperPositionEntity p, PaperPositionEventType type, String reason, Realized r, IntrabarEventContext context) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (context != null) {
            details.put("interval", context.interval());
            details.put("candleOpenTime", IstanbulTimeUtil.format(context.candle().getOpenTime()));
            details.put("candleCloseTime", IstanbulTimeUtil.format(context.candle().getCloseTime()));
            details.put("candleHigh", context.candle().getHigh());
            details.put("candleLow", context.candle().getLow());
            details.put("candleClose", context.candle().getClose());
            details.put("currentStopBefore", context.currentStopBefore());
            details.put("tp1HitBefore", context.tp1HitBefore());
            details.put("tp2HitBefore", context.tp2HitBefore());
            details.put("remainingPositionPctBefore", context.remainingPositionPctBefore());
            details.put("trailingActiveBefore", context.trailingActiveBefore());
        }
        details.put("openedAt", IstanbulTimeUtil.format(p.getOpenedAt()));
        details.put("closedAt", IstanbulTimeUtil.format(p.getClosedAt()));
        details.put("lastCheckedAt", IstanbulTimeUtil.format(p.getLastCheckedAt()));
        details.put("trailingActivatedAtBarCloseTime", IstanbulTimeUtil.format(p.getTrailingActivatedAtBarCloseTime()));
        details.put("currentStopAfter", p.getCurrentStop());
        details.put("tp1", p.getTp1());
        details.put("tp2", p.getTp2());
        details.put("tp1HitAfter", p.getTp1Hit());
        details.put("tp2HitAfter", p.getTp2Hit());
        details.put("remainingPositionPctAfter", p.getRemainingPositionPct());
        details.put("trailingActiveAfter", p.getTrailingActive());
        details.put("exitReason", reason);
        details.put("initialBalance", tradingConfig().getInitialBalanceUsdt());
        details.put("currentBalance", r == null ? tradingConfig().getInitialBalanceUsdt().add(defaultBigDecimal(p.getRealizedPnlUsdt(), BigDecimal.ZERO)) : r.currentBalance());
        details.put("marginUsdt", marginUsdt());
        details.put("notionalUsdt", p.getNotionalUsdt());
        details.put("entryPrice", p.getEntryPrice());
        details.put("entryPriceAdjusted", p.getEntryPriceAdjusted());
        details.put("exitPriceAdjusted", r == null ? null : adjustedExit(p.getSide(), p.getExitPrice()));
        details.put("quantity", p.getQuantity());
        details.put("closedQty", r == null ? null : r.closedQty());
        details.put("remainingQty", r == null ? null : r.remainingQty());
        details.put("grossPnl", r == null ? null : r.grossPnl());
        details.put("entryFeePart", r == null ? null : r.entryFeePart());
        details.put("exitFee", r == null ? null : r.exitFee());
        details.put("totalFee", r == null ? null : r.totalFee());
        details.put("feeType", r == null ? null : r.feeType());
        details.put("netPnl", r == null ? null : r.netPnl());
        details.put("cumulativeRealizedPnl", r == null ? p.getRealizedPnlUsdt() : r.cumulativeRealizedPnl());
        details.put("netPnlPctOnMargin", r == null ? p.getLeveragedNetUnrealizedPnlPct() : r.leveraged());
        details.put("netPnlPctOnNotional", r == null ? p.getNetUnrealizedPnlPct() : r.net());
        details.put("rawPnlPct", r == null ? p.getRawUnrealizedPnlPct() : r.raw());
        details.put("netPnlPct", r == null ? p.getNetUnrealizedPnlPct() : r.net());
        details.put("leveragedNetPnlPct", r == null ? p.getLeveragedNetUnrealizedPnlPct() : r.leveraged());
        return details;
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            ObjectMapper mapper = (objectMapper == null ? new ObjectMapper() : objectMapper.copy()).findAndRegisterModules();
            return mapper.writeValueAsString(details);
        } catch (Exception exception) {
            return toSimpleJson(details);
        }
    }

    private String toSimpleJson(Map<String, Object> details) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('\"').append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append('\"').append(escapeJson(value.toString())).append('\"');
            }
        }
        return json.append('}').toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void updateStatus(PaperPositionEntity p) {
        if (defaultBigDecimal(p.getRemainingPositionPct(), new BigDecimal("100")).compareTo(BigDecimal.ZERO) <= 0) p.setStatus(PaperPositionStatus.CLOSED);
        else if (Boolean.TRUE.equals(p.getTp1Hit()) || Boolean.TRUE.equals(p.getTp2Hit())) p.setStatus(PaperPositionStatus.PARTIALLY_CLOSED);
    }

    private void updatePricePath(PaperPositionEntity p, BigDecimal high, BigDecimal low, BigDecimal close) {
        BigDecimal entry = defaultBigDecimal(p.getEntryPriceAdjusted(), p.getEntryPrice());
        p.setCurrentPrice(close);
        p.setHighestPrice(max(defaultBigDecimal(p.getHighestPrice(), entry), high));
        p.setLowestPrice(min(defaultBigDecimal(p.getLowestPrice(), entry), low));
        p.setHighestPriceSinceEntry(max(defaultBigDecimal(p.getHighestPriceSinceEntry(), entry), high));
        p.setLowestPriceSinceEntry(min(defaultBigDecimal(p.getLowestPriceSinceEntry(), entry), low));
        if (p.getSide() == PositionSide.SHORT) {
            p.setMaxFavorableMovePct(pct(entry.subtract(p.getLowestPrice()), entry));
            p.setMaxAdverseMovePct(pct(entry.subtract(p.getHighestPrice()), entry));
        } else {
            p.setMaxFavorableMovePct(pct(p.getHighestPrice().subtract(entry), entry));
            p.setMaxAdverseMovePct(pct(p.getLowestPrice().subtract(entry), entry));
        }
    }

    private void updateHoldingDuration(PaperPositionEntity p, Instant now) {
        Instant openedAt = p.getOpenedAt() == null ? now : p.getOpenedAt();
        long minutesHeld = Math.max(0, Duration.between(openedAt, now).toMinutes());
        int barMinutes = Math.max(1, intValue(paperExitConfig().getBarMinutes(), 60));
        p.setMinutesHeld(Math.toIntExact(Math.min(minutesHeld, Integer.MAX_VALUE)));
        p.setBarsHeld(Math.toIntExact(Math.min(minutesHeld / barMinutes, Integer.MAX_VALUE)));
    }

    private void updateUnrealized(PaperPositionEntity p, BigDecimal close) {
        BigDecimal raw = rawPnlPct(p.getSide(), pnlEntryPrice(p), close);
        BigDecimal net = calculateNetPnlPct(p, close);
        p.setRawUnrealizedPnlPct(raw);
        p.setNetUnrealizedPnlPct(net);
        p.setLeveragedNetUnrealizedPnlPct(net.multiply(BigDecimal.valueOf(intValue(tradingConfig().getLeverage(), 10))).setScale(PCT_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal rawPnlPct(PositionSide side, BigDecimal entry, BigDecimal exit) {
        BigDecimal numerator = side == PositionSide.SHORT ? entry.subtract(exit) : exit.subtract(entry);
        return numerator.divide(entry, PCT_SCALE + 4, RoundingMode.HALF_UP).multiply(ONE_HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }
    private BigDecimal adjustedExit(PositionSide side, BigDecimal price) {
        BigDecimal slip = costConfig().getSlippagePct();
        return side == PositionSide.SHORT ? price.multiply(BigDecimal.ONE.add(slip)) : price.multiply(BigDecimal.ONE.subtract(slip));
    }
    private BigDecimal fallbackAtr(PaperPositionEntity p) {
        return defaultBigDecimal(p.getRiskPerUnit(), p.getEntryPrice().multiply(new BigDecimal("0.012")));
    }
    private List<PaperPositionStatus> activeStatuses() { return List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED); }
    private ScannerProperties.PaperExit paperExitConfig() { return scannerProperties.getPaperExit() == null ? new ScannerProperties.PaperExit() : scannerProperties.getPaperExit(); }
    private ScannerProperties.PaperCost costConfig() { return scannerProperties.getPaperCost() == null ? new ScannerProperties.PaperCost() : scannerProperties.getPaperCost(); }
    private ScannerProperties.Trading tradingConfig() { return scannerProperties.getTrading() == null ? new ScannerProperties.Trading() : scannerProperties.getTrading(); }
    private ScannerProperties.Tp exitTpConfig() { return scannerProperties.getExit() == null || scannerProperties.getExit().getTp() == null ? new ScannerProperties.Tp() : scannerProperties.getExit().getTp(); }
    private ScannerProperties.AfterTp2 afterTp2Config() { return scannerProperties.getExit() == null || scannerProperties.getExit().getAfterTp2() == null ? new ScannerProperties.AfterTp2() : scannerProperties.getExit().getAfterTp2(); }
    private ScannerProperties.BreakEvenAfterTp1 breakEvenConfig() { return scannerProperties.getExit() == null || scannerProperties.getExit().getBreakEvenAfterTp1() == null ? new ScannerProperties.BreakEvenAfterTp1() : scannerProperties.getExit().getBreakEvenAfterTp1(); }
    private ScannerProperties.EarlyExit earlyExitConfig() { return scannerProperties.getExit() == null || scannerProperties.getExit().getEarlyExit() == null ? new ScannerProperties.EarlyExit() : scannerProperties.getExit().getEarlyExit(); }
    private BigDecimal tpClosePct(BigDecimal ratio) { return defaultBigDecimal(ratio, BigDecimal.ZERO).multiply(ONE_HUNDRED); }
    private BigDecimal marginUsdt() { return defaultBigDecimal(tradingConfig().getMarginPerTradeUsdt(), new BigDecimal("10")); }
    private BigDecimal entryFeeRate() { return defaultBigDecimal(tradingConfig().getMakerFeeRate(), new BigDecimal("0.0002")); }
    private BigDecimal feeRate(String feeType) { return "MAKER".equalsIgnoreCase(defaultString(feeType, "MAKER")) ? defaultBigDecimal(tradingConfig().getMakerFeeRate(), new BigDecimal("0.0002")) : defaultBigDecimal(tradingConfig().getTakerFeeRate(), new BigDecimal("0.0005")); }
    private String feeTypeForReason(PaperExitReason reason) {
        if (reason == PaperExitReason.PARTIAL_TP1 || reason == PaperExitReason.PARTIAL_TP2 || reason == null) return "MAKER";
        if (reason == PaperExitReason.STOP_LOSS || reason == PaperExitReason.TRAILING_STOP || reason == PaperExitReason.TRAILING_STOP_AT_TP1_AFTER_TP2) {
            return scannerProperties.getExit() == null || scannerProperties.getExit().getStopLoss() == null ? "TAKER" : defaultString(scannerProperties.getExit().getStopLoss().getStopExitFeeType(), "TAKER");
        }
        if (reason == PaperExitReason.EARLY_EXIT_TIME_NEGATIVE || reason == PaperExitReason.EARLY_EXIT_ADVERSE_PCT) {
            return defaultString(earlyExitConfig().getExitFeeType(), "TAKER");
        }
        return "TAKER";
    }
    private BigDecimal pnlEntryPrice(PaperPositionEntity p) { return booleanValue(tradingConfig().getUseAdjustedPricesForPnl(), true) ? defaultBigDecimal(p.getEntryPriceAdjusted(), p.getEntryPrice()) : p.getEntryPrice(); }
    private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) { return numerator.divide(denominator, PCT_SCALE + 4, RoundingMode.HALF_UP).multiply(ONE_HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP); }
    private BigDecimal defaultBigDecimal(BigDecimal primary, BigDecimal fallback) { return primary == null ? fallback : primary; }
    private String defaultString(String value, String defaultValue) { return value == null || value.isBlank() ? defaultValue : value; }
    private boolean booleanValue(Boolean value, boolean defaultValue) { return value == null ? defaultValue : value; }
    private int intValue(Integer value, int defaultValue) { return value == null ? defaultValue : value; }
    private boolean ge(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) >= 0; }
    private boolean le(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) <= 0; }
    private boolean gt(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) > 0; }
    private boolean lt(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) < 0; }
    private BigDecimal max(BigDecimal a, BigDecimal b) { return a.compareTo(b) >= 0 ? a : b; }
    private BigDecimal min(BigDecimal a, BigDecimal b) { return a.compareTo(b) <= 0 ? a : b; }

    private record IntrabarEventContext(
            PaperPositionEntity position,
            KlineCandle candle,
            String interval,
            BigDecimal currentStopBefore,
            Boolean tp1HitBefore,
            Boolean tp2HitBefore,
            BigDecimal remainingPositionPctBefore,
            Boolean trailingActiveBefore) {
        IntrabarEventContext(PaperPositionEntity position, KlineCandle candle, String interval) {
            this(position, candle, interval, position.getCurrentStop(), position.getTp1Hit(), position.getTp2Hit(),
                    position.getRemainingPositionPct(), position.getTrailingActive());
        }
    }
}
