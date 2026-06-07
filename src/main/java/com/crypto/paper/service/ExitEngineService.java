package com.crypto.paper.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.TechnicalSnapshot;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExitEngineService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PCT_SCALE = 8;

    private final PaperPositionRepository paperPositionRepository;
    private final BinanceFuturesClient binanceFuturesClient;
    private final ScannerProperties scannerProperties;

    @Autowired(required = false) private PaperPositionEventRepository eventRepository;
    @Autowired(required = false) private JsonlDecisionLogService jsonlDecisionLogService;
    @Autowired(required = false) private IndicatorService indicatorService;
    @Autowired(required = false) private MarketScanRunRepository marketScanRunRepository;
    @Autowired(required = false) private CoinScanResultRepository coinScanResultRepository;

    public List<PaperPositionEntity> evaluateOpenPositions() {
        if (!booleanValue(paperExitConfig().getEnabled(), true)) {
            log.info("PAPER_EXIT_DISABLED");
            return List.of();
        }
        List<PaperPositionEntity> openPositions = paperPositionRepository.findByStatusInOrderByOpenedAtDesc(activeStatuses());
        List<PaperPositionEntity> evaluated = openPositions.stream()
                .map(this::evaluateWithLastClosedCandle)
                .filter(Objects::nonNull)
                .map(paperPositionRepository::save)
                .toList();
        log.info("PAPER_EXIT_EVALUATION_DONE openChecked={} closed={}", openPositions.size(), evaluated.stream().filter(p -> p.getStatus() == PaperPositionStatus.CLOSED).count());
        return evaluated.stream().filter(p -> p.getStatus() == PaperPositionStatus.CLOSED).toList();
    }

    public PaperPositionEntity evaluatePosition(PaperPositionEntity position, BigDecimal currentPrice) {
        if (currentPrice == null) {
            log.warn("PAPER_EXIT_PRICE_MISSING symbol={}", position.getSymbol());
            return null;
        }
        Instant now = Instant.now();
        return evaluatePositionOnCandle(position, now.minusSeconds(3600), now, currentPrice, currentPrice, currentPrice, fallbackAtr(position));
    }

    public PaperPositionEntity evaluatePositionOnCandle(PaperPositionEntity position, Instant candleOpenTime, Instant candleCloseTime,
            BigDecimal candleHigh, BigDecimal candleLow, BigDecimal candleClose, BigDecimal atr14) {
        Instant now = Instant.now();
        updatePricePath(position, candleHigh, candleLow, candleClose);
        updateHoldingDuration(position, now);
        position.setBarsInPosition(intValue(position.getBarsInPosition(), 0) + 1);
        position.setLastCheckedAt(now);
        updateUnrealized(position, candleClose);

        if (position.getCurrentStop() == null && position.getTp1() == null && position.getTp2() == null) {
            PaperExitReason legacy = resolveLegacyExitReason(position, calculateUnrealizedPnlPct(position, candleClose));
            if (legacy != null) {
                closeRemaining(position, candleClose, legacy, candleCloseTime);
            }
            return position;
        }

        if (stopHit(position, candleHigh, candleLow)) {
            PaperExitReason reason = Boolean.TRUE.equals(position.getTrailingActive()) ? PaperExitReason.TRAILING_STOP : PaperExitReason.STOP_LOSS;
            closeRemaining(position, defaultBigDecimal(position.getCurrentStop(), candleClose), reason, candleCloseTime);
            return position;
        }
        if (tp1Hit(position, candleHigh, candleLow)) {
            partialClose(position, position.getTp1(), scannerProperties.getPaperRisk().getTp1ClosePct(), PaperPositionEventType.PARTIAL_TP1, candleCloseTime);
            position.setTp1Hit(true);
            position.setTrailingActive(true);
            position.setTrailingActivatedAtBarCloseTime(candleCloseTime);
            BigDecimal feeBuffer = position.getEntryPrice().multiply(scannerProperties.getPaperRisk().getFeeBufferPct());
            position.setCurrentStop(position.getSide() == PositionSide.SHORT ? position.getEntryPrice().subtract(feeBuffer) : position.getEntryPrice().add(feeBuffer));
        }
        if (tp2Hit(position, candleHigh, candleLow)) {
            partialClose(position, position.getTp2(), scannerProperties.getPaperRisk().getTp2ClosePct(), PaperPositionEventType.PARTIAL_TP2, candleCloseTime);
            position.setTp2Hit(true);
        }
        updateStatus(position);
        updateTrailing(position, candleHigh, candleLow, atr14, candleCloseTime);
        if (timeStop(position)) {
            closeRemaining(position, candleClose, PaperExitReason.TIME_STOP, candleCloseTime);
            return position;
        }
        PaperExitReason strategicExit = resolveStrategicExit(position);
        if (strategicExit != null) {
            closeRemaining(position, candleClose, strategicExit, candleCloseTime);
        }
        log.info("PAPER_POSITION_EVALUATED id={} symbol={} side={} close={} status={} remainingPct={}", position.getId(), position.getSymbol(), position.getSide(), candleClose, position.getStatus(), position.getRemainingPositionPct());
        return position;
    }

    public BigDecimal calculateUnrealizedPnlPct(PaperPositionEntity position, BigDecimal currentPrice) {
        return rawPnlPct(position.getSide(), position.getEntryPrice(), currentPrice);
    }

    public BigDecimal calculateRealizedPnlUsdt(PaperPositionEntity position, BigDecimal pnlPct) {
        BigDecimal notionalUsdt = position.getNotionalUsdt() == null ? BigDecimal.ZERO : position.getNotionalUsdt();
        return notionalUsdt.multiply(pnlPct).divide(ONE_HUNDRED, PCT_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNetPnlPct(PaperPositionEntity position, BigDecimal exitPrice) {
        ScannerProperties.PaperCost cost = costConfig();
        BigDecimal raw = rawPnlPct(position.getSide(), position.getEntryPrice(), exitPrice);
        BigDecimal fees = cost.getTakerFeePct().multiply(BigDecimal.valueOf(200));
        BigDecimal slippage = cost.getSlippagePct().multiply(BigDecimal.valueOf(200));
        return raw.subtract(fees).subtract(slippage).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }


    private PaperExitReason resolveLegacyExitReason(PaperPositionEntity position, BigDecimal pnlPct) {
        BigDecimal stopLossPct = defaultBigDecimal(position.getStopLossPct(), paperExitConfig().getStopLossPct());
        BigDecimal takeProfitPct = defaultBigDecimal(position.getTakeProfitPct(), paperExitConfig().getTakeProfitPct());
        if (pnlPct.compareTo(stopLossPct.negate()) <= 0) return PaperExitReason.STOP_LOSS;
        if (pnlPct.compareTo(takeProfitPct) >= 0) return PaperExitReason.TAKE_PROFIT;
        if (timeStop(position)) return PaperExitReason.TIME_STOP;
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
                    closeRemaining(evaluated, last.getClose(), PaperExitReason.SIGNAL_INVALIDATION, last.getCloseTime());
                }
            }
            return evaluated;
        } catch (Exception exception) {
            log.warn("PAPER_EXIT_CANDLE_UNAVAILABLE symbol={} reason={}", position.getSymbol(), exception.getMessage());
            return null;
        }
    }

    private boolean stopHit(PaperPositionEntity p, BigDecimal high, BigDecimal low) {
        if (p.getCurrentStop() == null) return false;
        return p.getSide() == PositionSide.SHORT ? ge(high, p.getCurrentStop()) : le(low, p.getCurrentStop());
    }
    private boolean tp1Hit(PaperPositionEntity p, BigDecimal high, BigDecimal low) {
        return !Boolean.TRUE.equals(p.getTp1Hit()) && p.getTp1() != null && (p.getSide() == PositionSide.SHORT ? le(low, p.getTp1()) : ge(high, p.getTp1()));
    }
    private boolean tp2Hit(PaperPositionEntity p, BigDecimal high, BigDecimal low) {
        return !Boolean.TRUE.equals(p.getTp2Hit()) && p.getTp2() != null && (p.getSide() == PositionSide.SHORT ? le(low, p.getTp2()) : ge(high, p.getTp2()));
    }

    private void partialClose(PaperPositionEntity p, BigDecimal price, BigDecimal closePct, PaperPositionEventType type, Instant time) {
        BigDecimal remaining = defaultBigDecimal(p.getRemainingPositionPct(), new BigDecimal("100")).subtract(closePct).max(BigDecimal.ZERO);
        p.setRemainingPositionPct(remaining);
        Realized realized = realized(p, price, closePct);
        mergeRealized(p, realized, price);
        writeEvent(p, type, time, price, adjustedExit(p.getSide(), price), closePct, realized, type.name());
        log.info("{} positionId={} symbol={} closedPct={} remainingPct={} price={}", type, p.getId(), p.getSymbol(), closePct, remaining, price);
    }

    private void updateTrailing(PaperPositionEntity p, BigDecimal high, BigDecimal low, BigDecimal atr14, Instant candleCloseTime) {
        if (!Boolean.TRUE.equals(p.getTrailingActive())) return;
        boolean sameCandle = p.getTrailingActivatedAtBarCloseTime() != null && !candleCloseTime.isAfter(p.getTrailingActivatedAtBarCloseTime());
        BigDecimal trailingDistance = defaultBigDecimal(atr14, fallbackAtr(p)).multiply(scannerProperties.getPaperRisk().getTrailingAtrMultiplier());
        BigDecimal oldStop = p.getCurrentStop();
        if (p.getSide() == PositionSide.SHORT) {
            p.setLowestPriceSinceEntry(min(defaultBigDecimal(p.getLowestPriceSinceEntry(), p.getEntryPrice()), low));
            BigDecimal candidateStop = p.getLowestPriceSinceEntry().add(trailingDistance);
            p.setCurrentStop(oldStop == null ? candidateStop : min(oldStop, candidateStop));
        } else {
            p.setHighestPriceSinceEntry(max(defaultBigDecimal(p.getHighestPriceSinceEntry(), p.getEntryPrice()), high));
            BigDecimal candidateStop = p.getHighestPriceSinceEntry().subtract(trailingDistance);
            p.setCurrentStop(oldStop == null ? candidateStop : max(oldStop, candidateStop));
        }
        if (oldStop == null || p.getCurrentStop().compareTo(oldStop) != 0) {
            writeEvent(p, PaperPositionEventType.TRAILING_UPDATED, candleCloseTime, p.getCurrentStop(), null, null, null, "TRAILING_UPDATED");
        }
        if (!sameCandle && stopHit(p, high, low)) {
            closeRemaining(p, p.getCurrentStop(), PaperExitReason.TRAILING_STOP, candleCloseTime);
        }
    }

    private boolean timeStop(PaperPositionEntity p) {
        int timeStopMinutes = intValue(p.getTimeStopMinutes(), intValue(paperExitConfig().getTimeStopMinutes(), 240));
        return p.getMinutesHeld() != null && p.getMinutesHeld() >= timeStopMinutes;
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

    private void closeRemaining(PaperPositionEntity p, BigDecimal exitPrice, PaperExitReason reason, Instant time) {
        BigDecimal closePct = defaultBigDecimal(p.getRemainingPositionPct(), new BigDecimal("100"));
        Realized realized = realized(p, exitPrice, closePct);
        p.setStatus(PaperPositionStatus.CLOSED);
        p.setClosedAt(time);
        p.setExitPrice(exitPrice);
        p.setExitPriceAdjusted(adjustedExit(p.getSide(), exitPrice));
        mergeRealized(p, realized, exitPrice);
        p.setRealizedPnlPct(p.getNetRealizedPnlPct());
        p.setRealizedPnlUsdt(calculateRealizedPnlUsdt(p, p.getNetRealizedPnlPct()));
        p.setExitReason(reason.name());
        p.setExitDetail("paper exit reason=" + reason.name());
        p.setRemainingPositionPct(BigDecimal.ZERO);
        writeEvent(p, PaperPositionEventType.valueOf(reason.name()), time, exitPrice, p.getExitPriceAdjusted(), closePct, realized, reason.name());
        writeEvent(p, PaperPositionEventType.CLOSED, time, exitPrice, p.getExitPriceAdjusted(), closePct, realized, reason.name());
        log.info("PAPER_POSITION_CLOSED id={} symbol={} side={} exitReason={} exitPrice={} pnlPct={}", p.getId(), p.getSymbol(), p.getSide(), reason, exitPrice, p.getRealizedPnlPct());
    }

    private void mergeRealized(PaperPositionEntity p, Realized r, BigDecimal exitPrice) {
        p.setExitPrice(exitPrice);
        p.setRawRealizedPnlPct(defaultBigDecimal(p.getRawRealizedPnlPct(), BigDecimal.ZERO).add(r.rawWeighted()));
        p.setNetRealizedPnlPct(defaultBigDecimal(p.getNetRealizedPnlPct(), BigDecimal.ZERO).add(r.netWeighted()));
        p.setLeveragedNetRealizedPnlPct(defaultBigDecimal(p.getLeveragedNetRealizedPnlPct(), BigDecimal.ZERO).add(r.leveragedWeighted()));
        p.setRealizedPnlPct(p.getNetRealizedPnlPct());
        p.setRealizedPnlUsdt(calculateRealizedPnlUsdt(p, p.getNetRealizedPnlPct()));
    }

    private Realized realized(PaperPositionEntity p, BigDecimal exitPrice, BigDecimal pctClosed) {
        BigDecimal raw = rawPnlPct(p.getSide(), p.getEntryPrice(), exitPrice);
        BigDecimal net = calculateNetPnlPct(p, exitPrice);
        BigDecimal leveraged = net.multiply(BigDecimal.valueOf(intValue(costConfig().getLeverage(), 3))).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal weight = pctClosed.divide(ONE_HUNDRED, PCT_SCALE + 4, RoundingMode.HALF_UP);
        return new Realized(raw, net, leveraged, raw.multiply(weight), net.multiply(weight), leveraged.multiply(weight));
    }
    private record Realized(BigDecimal raw, BigDecimal net, BigDecimal leveraged, BigDecimal rawWeighted, BigDecimal netWeighted, BigDecimal leveragedWeighted) {}

    private void writeEvent(PaperPositionEntity p, PaperPositionEventType type, Instant time, BigDecimal price, BigDecimal adjusted, BigDecimal closePct, Realized r, String reason) {
        if (eventRepository != null) {
            eventRepository.save(PaperPositionEventEntity.builder().position(p).eventTimeUtc(time).eventType(type).price(price).adjustedPrice(adjusted)
                    .positionPctClosed(closePct).rawPnlPct(r == null ? null : r.raw()).netPnlPct(r == null ? null : r.net()).leveragedNetPnlPct(r == null ? null : r.leveraged())
                    .feePct(costConfig().getTakerFeePct().multiply(BigDecimal.valueOf(200))).slippagePct(costConfig().getSlippagePct().multiply(BigDecimal.valueOf(200))).leverage(costConfig().getLeverage()).reason(reason).build());
        }
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logPaper(Map.of("event", type.name(), "symbol", p.getSymbol(), "side", p.getSide().name(), "positionId", p.getId() == null ? "" : p.getId(), "exitPrice", price == null ? "" : price, "exitReason", reason == null ? "" : reason));
        }
    }

    private void updateStatus(PaperPositionEntity p) {
        if (defaultBigDecimal(p.getRemainingPositionPct(), new BigDecimal("100")).compareTo(BigDecimal.ZERO) <= 0) p.setStatus(PaperPositionStatus.CLOSED);
        else if (Boolean.TRUE.equals(p.getTp1Hit()) || Boolean.TRUE.equals(p.getTp2Hit())) p.setStatus(PaperPositionStatus.PARTIALLY_CLOSED);
    }

    private void updatePricePath(PaperPositionEntity p, BigDecimal high, BigDecimal low, BigDecimal close) {
        BigDecimal entry = p.getEntryPrice();
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
        BigDecimal raw = rawPnlPct(p.getSide(), p.getEntryPrice(), close);
        BigDecimal net = calculateNetPnlPct(p, close);
        p.setRawUnrealizedPnlPct(raw);
        p.setNetUnrealizedPnlPct(net);
        p.setLeveragedNetUnrealizedPnlPct(net.multiply(BigDecimal.valueOf(intValue(costConfig().getLeverage(), 3))).setScale(PCT_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal rawPnlPct(PositionSide side, BigDecimal entry, BigDecimal exit) {
        BigDecimal numerator = side == PositionSide.SHORT ? entry.subtract(exit) : exit.subtract(entry);
        return numerator.divide(entry, PCT_SCALE + 4, RoundingMode.HALF_UP).multiply(ONE_HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }
    private BigDecimal adjustedExit(PositionSide side, BigDecimal price) {
        BigDecimal slip = costConfig().getSlippagePct();
        return side == PositionSide.SHORT ? price.multiply(BigDecimal.ONE.add(slip)) : price.multiply(BigDecimal.ONE.subtract(slip));
    }
    private BigDecimal fallbackAtr(PaperPositionEntity p) { return defaultBigDecimal(p.getRiskPerUnit(), p.getEntryPrice().multiply(new BigDecimal("0.012"))).divide(scannerProperties.getPaperRisk().getAtrStopMultiplier(), PCT_SCALE + 4, RoundingMode.HALF_UP); }
    private List<PaperPositionStatus> activeStatuses() { return List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED); }
    private ScannerProperties.PaperExit paperExitConfig() { return scannerProperties.getPaperExit() == null ? new ScannerProperties.PaperExit() : scannerProperties.getPaperExit(); }
    private ScannerProperties.PaperCost costConfig() { return scannerProperties.getPaperCost() == null ? new ScannerProperties.PaperCost() : scannerProperties.getPaperCost(); }
    private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) { return numerator.divide(denominator, PCT_SCALE + 4, RoundingMode.HALF_UP).multiply(ONE_HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP); }
    private BigDecimal defaultBigDecimal(BigDecimal primary, BigDecimal fallback) { return primary == null ? fallback : primary; }
    private boolean booleanValue(Boolean value, boolean defaultValue) { return value == null ? defaultValue : value; }
    private int intValue(Integer value, int defaultValue) { return value == null ? defaultValue : value; }
    private boolean ge(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) >= 0; }
    private boolean le(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) <= 0; }
    private boolean gt(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) > 0; }
    private boolean lt(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) < 0; }
    private BigDecimal max(BigDecimal a, BigDecimal b) { return a.compareTo(b) >= 0 ? a : b; }
    private BigDecimal min(BigDecimal a, BigDecimal b) { return a.compareTo(b) <= 0 ? a : b; }
}
