package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.persistence.LaplaceTradeEventEntity;
import com.crypto.laplace.persistence.LaplaceTradeEventRepository;
import com.crypto.laplace.service.LaplaceSymbolBlockService;
import com.crypto.laplace.service.StartupMarketUniverseService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaplacePaperExecutionService {
    public static final String STRATEGY = "LAPLACE_KERNEL_REGRESSION_30M";
    public static final String VERSION = "1.0";
    private static final int SCALE = 12;

    private final LaplacePaperPositionRepository positions;
    private final LaplaceTradeEventRepository events;
    private final LaplaceExecutionPriceProvider prices;
    private final LaplacePnlCalculator pnl;
    private final LaplaceStrategyProperties properties;
    private final LaplaceTradeJsonlWriter writer;
    private final LaplaceSymbolBlockService blocks;
    private final StartupMarketUniverseService universe;

    @Transactional
    public LaplacePaperPositionEntity open(LaplaceSignalResult signal, PositionSide side, String reversalId,
                                           String positionBefore) {
        assertNoOpen(signal.symbol());
        MarketExecutionAction action = side == PositionSide.LONG ? MarketExecutionAction.LONG_OPEN : MarketExecutionAction.SHORT_OPEN;
        var quote = prices.quote(signal.symbol(), action);
        Execution execution = execution(quote, isBuy(action));
        Instant requested = Instant.now();
        BigDecimal notional = properties.getLaplace().getNotionalUsdt();
        int leverage = properties.getLaplace().getLeverage();
        BigDecimal margin = properties.getLaplace().getMarginPerPositionUsdt();
        BigDecimal quantity = notional.divide(execution.price(), SCALE, RoundingMode.DOWN);
        BigDecimal feeRate = properties.getLaplace().getTakerFeeRate();
        BigDecimal entryNotional = quantity.multiply(execution.price()).setScale(SCALE, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) throw new IllegalStateException("INVALID_QUANTITY");
        if (!hasAvailableBalance(margin.add(entryNotional.multiply(feeRate)))) {
            throw new IllegalStateException("INSUFFICIENT_PAPER_BALANCE");
        }
        BigDecimal fee = entryNotional.multiply(feeRate).setScale(SCALE, RoundingMode.HALF_UP);
        String signalId = entryKey(signal, side);
        Instant now = Instant.now();
        var position = LaplacePaperPositionEntity.builder()
                .id(UUID.randomUUID().toString()).strategy(STRATEGY).strategyVersion(VERSION)
                .symbol(signal.symbol()).side(side).status(LaplacePositionStatus.OPEN).entrySignalId(signalId)
                .entryRawSignal(signal.entrySignal().name()).signalInverted(true)
                .entryCandleCloseTime(signal.signalCandleCloseTime()).entryTime(now)
                .entrySignalClosePrice(BigDecimal.valueOf(signal.signalCandleClose()))
                .entryReferencePrice(execution.referencePrice()).entryExecutionPrice(execution.price())
                .entrySlippagePct(execution.slippagePct()).margin(margin).quantity(quantity).notional(notional)
                .leverage(leverage).entryFeeRate(feeRate).entryFee(fee).build();
        positions.saveAndFlush(position);
        log.info("LAPLACE_MARKET_ENTRY_EXECUTED strategy={} symbol={} side={} referencePrice={} executionPrice={} slippagePct={} notional={} leverage={}",
                STRATEGY, signal.symbol(), side, execution.referencePrice(), execution.price(), execution.slippagePct(), notional, leverage);
        Map<String, Object> payload = base(signal, "ENTRY", position, UUID.randomUUID().toString(), reversalId);
        payload.put("side", side); payload.put("rawEntrySignal", signal.entrySignal());
        payload.put("rawStrongReversalSignal", signal.strongReversalSignal()); payload.put("effectiveExecutionSide", side);
        payload.put("signalInverted", true); payload.put("initialCapitalUsdt", properties.getLaplace().getInitialCapitalUsdt());
        payload.put("positionBefore", positionBefore); payload.put("positionAfter", side);
        payload.put("entryReason", reversalId == null ? "CONFIRMED_LAPLACE_" + side : "CONFIRMED_LAPLACE_REVERSAL");
        payload.put("signalDetectedAt", requested); payload.put("executionRequestedAt", requested); payload.put("executionTime", now);
        payload.put("orderType", "MARKET"); payload.put("executionAction", action); payload.put("priceSource", quote.source());
        payload.put("bestBid", quote.bestBid()); payload.put("bestAsk", quote.bestAsk()); payload.put("executionPriceType", quote.executionPriceType());
        payload.put("referencePrice", execution.referencePrice()); payload.put("executionPrice", execution.price());
        payload.put("slippagePct", execution.slippagePct()); payload.put("entryReferencePrice", execution.referencePrice());
        payload.put("entryExecutionPrice", execution.price()); payload.put("entrySlippagePct", execution.slippagePct());
        payload.put("margin", margin); payload.put("leverage", leverage); payload.put("notional", notional); payload.put("quantity", quantity);
        payload.put("takerFeeRate", feeRate); payload.put("entryFeeRate", feeRate); payload.put("entryFee", fee); payload.put("status", position.getStatus());
        saveEvent(payload, "ENTRY", reversalId, position);
        return position;
    }

    @Transactional
    public ReversalOutcome reverse(LaplaceSignalResult signal, LaplacePaperPositionEntity expected, PositionSide target,
                                   boolean openTarget) {
        LaplacePaperPositionEntity position = singleOpenForUpdate(signal.symbol());
        if (!position.getId().equals(expected.getId())) throw new IllegalStateException("POSITION_STATE_CONFLICT");
        String exitSignalId = reversalKey(signal, target);
        if (position.getExitSignalId() != null) throw new IllegalStateException("DUPLICATE_EXECUTION_BLOCKED");
        String reversalId = UUID.randomUUID().toString();
        Instant requested = Instant.now();
        MarketExecutionAction action = closeAction(position.getSide());
        var quote = prices.quote(signal.symbol(), action);
        Execution execution = execution(quote, isBuy(action));
        Instant now = Instant.now();
        close(position, exitSignalId, openTarget ? "OPPOSITE_CONFIRMED_LAPLACE_SIGNAL" : "OPPOSITE_SIGNAL_OUTSIDE_ENTRY_UNIVERSE",
                now, execution);
        logExit(signal, position, target, requested, reversalId, action, quote, execution);
        LaplacePaperPositionEntity opened = null;
        if (openTarget) {
            try { opened = open(signal, target, reversalId, position.getSide().name()); }
            catch (RuntimeException failure) { throw new IllegalStateException("REVERSAL_OPEN_FAILED", failure); }
        }
        return new ReversalOutcome(position, opened, reversalId);
    }

    @Transactional
    public void stop(LaplacePaperPositionEntity expected, Instant windowStart, Instant windowEnd,
                     BigDecimal windowHigh, BigDecimal windowLow) {
        LaplacePaperPositionEntity position = singleOpenForUpdate(expected.getSymbol());
        if (!position.getId().equals(expected.getId())) throw new IllegalStateException("POSITION_STATE_CONFLICT");
        Instant now = Instant.now();
        MarketExecutionAction action = closeAction(position.getSide());
        var quote = prices.quote(position.getSymbol(), action);
        Execution execution = execution(quote, isBuy(action));
        close(position, "STOP:" + position.getId(), "STOP_LOSS", now, execution);
        blocks.blockForStop(position.getSymbol(), position.getId(), now);
        BigDecimal pct = properties.getLaplace().getStopLossPct();
        BigDecimal factor = position.getSide() == PositionSide.LONG ? BigDecimal.ONE.subtract(pct.movePointLeft(2)) : BigDecimal.ONE.add(pct.movePointLeft(2));
        BigDecimal stopPrice = position.getEntryExecutionPrice().multiply(factor);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "EXIT"); payload.put("eventId", UUID.randomUUID().toString()); payload.put("strategy", STRATEGY);
        payload.put("positionId", position.getId()); payload.put("symbol", position.getSymbol()); payload.put("exitReason", "STOP_LOSS");
        payload.put("configuredStopLossPct", pct); payload.put("configuredStopPrice", stopPrice); payload.put("checkedWindowStart", windowStart);
        payload.put("checkedWindowEnd", windowEnd); payload.put("checkedWindowHigh", windowHigh); payload.put("checkedWindowLow", windowLow);
        payload.put("stopTriggerTime", now); payload.put("referencePrice", execution.referencePrice()); payload.put("executionPrice", execution.price());
        payload.put("slippagePct", execution.slippagePct()); payload.put("exitReferencePrice", execution.referencePrice());
        payload.put("exitExecutionPrice", execution.price()); payload.put("exitSlippagePct", execution.slippagePct());
        payload.put("grossPnl", position.getGrossPnl()); payload.put("entryFee", position.getEntryFee()); payload.put("exitFee", position.getExitFee()); payload.put("netPnl", position.getNetPnl());
        saveEvent(payload, "EXIT", null, position);
    }

    private void close(LaplacePaperPositionEntity position, String exitSignalId, String reason, Instant now, Execution execution) {
        BigDecimal exitNotional = position.getQuantity().multiply(execution.price()).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal exitFee = exitNotional.multiply(position.getEntryFeeRate()).setScale(SCALE, RoundingMode.HALF_UP);
        var result = pnl.calculate(position.getSide(), position.getEntryExecutionPrice(), execution.price(), position.getQuantity(),
                position.getNotional(), position.getEntryFee(), exitFee);
        if (position.getLeverage() != 20 || position.getNotional().compareTo(new BigDecimal("20")) != 0) {
            log.warn("LAPLACE_LEGACY_POSITION_SIZING_PRESERVED symbol={} positionId={} notional={} leverage={}", position.getSymbol(), position.getId(), position.getNotional(), position.getLeverage());
        }
        position.setStatus(LaplacePositionStatus.CLOSED); position.setExitSignalId(exitSignalId); position.setExitTime(now);
        position.setExitReferencePrice(execution.referencePrice()); position.setExitExecutionPrice(execution.price()); position.setExitSlippagePct(execution.slippagePct());
        position.setExitFee(exitFee); position.setGrossPnl(result.gross()); position.setGrossPnlPct(result.grossPct());
        position.setNetPnl(result.net()); position.setNetPnlPct(result.netPct());
        position.setHoldingMinutes(Duration.between(position.getEntryTime(), now).toMinutes()); position.setExitReason(reason);
        positions.saveAndFlush(position);
    }

    private void logExit(LaplaceSignalResult signal, LaplacePaperPositionEntity position, PositionSide target, Instant requested,
                         String reversalId, MarketExecutionAction action, LaplaceExecutionPriceProvider.Price quote, Execution execution) {
        log.info("LAPLACE_MARKET_EXIT_EXECUTED strategy={} symbol={} side={} referencePrice={} executionPrice={} slippagePct={} entryNotional={} leverage={}",
                STRATEGY, signal.symbol(), position.getSide(), execution.referencePrice(), execution.price(), execution.slippagePct(), position.getNotional(), position.getLeverage());
        BigDecimal exitNotional = position.getQuantity().multiply(execution.price()).setScale(SCALE, RoundingMode.HALF_UP);
        Map<String, Object> payload = base(signal, "EXIT", position, UUID.randomUUID().toString(), reversalId);
        payload.put("side", position.getSide()); payload.put("closedSide", position.getSide()); payload.put("incomingSignal", target);
        payload.put("positionBefore", position.getSide()); payload.put("positionAfter", "FLAT"); payload.put("exitReason", position.getExitReason());
        payload.put("entryTime", position.getEntryTime()); payload.put("entrySignalClosePrice", position.getEntrySignalClosePrice());
        payload.put("entryReferencePrice", position.getEntryReferencePrice()); payload.put("entryExecutionPrice", position.getEntryExecutionPrice());
        payload.put("entrySlippagePct", position.getEntrySlippagePct()); payload.put("exitExecutionRequestedAt", requested); payload.put("exitTime", position.getExitTime());
        payload.put("orderType", "MARKET"); payload.put("executionAction", action); payload.put("priceSource", quote.source()); payload.put("bestBid", quote.bestBid());
        payload.put("bestAsk", quote.bestAsk()); payload.put("executionPriceType", quote.executionPriceType()); payload.put("referencePrice", execution.referencePrice());
        payload.put("executionPrice", execution.price()); payload.put("slippagePct", execution.slippagePct()); payload.put("exitReferencePrice", execution.referencePrice());
        payload.put("exitExecutionPrice", execution.price()); payload.put("exitSlippagePct", execution.slippagePct()); payload.put("entryNotional", position.getNotional());
        payload.put("exitNotional", exitNotional); payload.put("quantity", position.getQuantity()); payload.put("notional", position.getNotional()); payload.put("leverage", position.getLeverage());
        payload.put("grossPnl", position.getGrossPnl()); payload.put("grossPnlPct", position.getGrossPnlPct()); payload.put("entryFee", position.getEntryFee());
        payload.put("takerFeeRate", position.getEntryFeeRate()); payload.put("exitFeeRate", position.getEntryFeeRate()); payload.put("exitFee", position.getExitFee());
        payload.put("totalFee", position.getEntryFee().add(position.getExitFee())); payload.put("netPnl", position.getNetPnl()); payload.put("netPnlPct", position.getNetPnlPct());
        payload.put("holdingMinutes", position.getHoldingMinutes()); payload.put("status", position.getStatus()); saveEvent(payload, "EXIT", reversalId, position);
    }

    private Execution execution(LaplaceExecutionPriceProvider.Price quote, boolean buy) {
        BigDecimal slippagePct = properties.getLaplace().getMarketSlippagePct();
        BigDecimal factor = buy ? BigDecimal.ONE.add(slippagePct.movePointLeft(2)) : BigDecimal.ONE.subtract(slippagePct.movePointLeft(2));
        return new Execution(quote.value(), quote.value().multiply(factor).setScale(SCALE, RoundingMode.HALF_UP), slippagePct);
    }

    private boolean isBuy(MarketExecutionAction action) { return action == MarketExecutionAction.LONG_OPEN || action == MarketExecutionAction.SHORT_CLOSE; }
    private MarketExecutionAction closeAction(PositionSide side) { return side == PositionSide.LONG ? MarketExecutionAction.LONG_CLOSE : MarketExecutionAction.SHORT_CLOSE; }
    boolean hasAvailableBalance(BigDecimal required) { return availableBalance().compareTo(required) >= 0; }
    BigDecimal availableBalance() { BigDecimal realized = positions.findByStrategyAndStatus(STRATEGY, LaplacePositionStatus.CLOSED).stream().map(x -> x.getNetPnl() == null ? BigDecimal.ZERO : x.getNetPnl()).reduce(BigDecimal.ZERO, BigDecimal::add); List<LaplacePaperPositionEntity> open = positions.findByStrategyAndStatus(STRATEGY, LaplacePositionStatus.OPEN); BigDecimal locked = open.stream().map(LaplacePaperPositionEntity::getMargin).reduce(BigDecimal.ZERO, BigDecimal::add); BigDecimal openEntryFees = open.stream().map(x -> x.getEntryFee() == null ? BigDecimal.ZERO : x.getEntryFee()).reduce(BigDecimal.ZERO, BigDecimal::add); return properties.getLaplace().getInitialCapitalUsdt().add(realized).subtract(locked).subtract(openEntryFees); }
    private void assertNoOpen(String symbol) { if (!positions.findOpenForUpdate(STRATEGY, symbol, LaplacePositionStatus.OPEN).isEmpty()) throw new IllegalStateException("POSITION_STATE_CONFLICT"); }
    public LaplacePaperPositionEntity singleOpenForUpdate(String symbol) { List<LaplacePaperPositionEntity> positionsForSymbol = positions.findOpenForUpdate(STRATEGY, symbol, LaplacePositionStatus.OPEN); if (positionsForSymbol.size() != 1) throw new IllegalStateException("POSITION_STATE_CONFLICT"); return positionsForSymbol.getFirst(); }
    private void saveEvent(Map<String, Object> payload, String type, String reversal, LaplacePaperPositionEntity position) { String id = (String) payload.get("eventId"); writer.tryJson(payload).ifPresentOrElse(json -> { events.save(LaplaceTradeEventEntity.builder().eventId(id).eventType(type).reversalId(reversal).positionId(position.getId()).symbol(position.getSymbol()).payloadJson(json).jsonlWritten(false).createdAt(Instant.now()).build()); log.info("LAPLACE_TRADE_EVENT_PUBLISHED eventId={} eventType={} symbol={}", id, type, position.getSymbol()); }, () -> log.error("LAPLACE_TRADE_EVENT_SERIALIZATION_SKIPPED eventId={} eventType={} symbol={} positionId={}", id, type, position.getSymbol(), position.getId())); }
    private Map<String, Object> base(LaplaceSignalResult signal, String type, LaplacePaperPositionEntity position, String eventId, String reversal) { Map<String, Object> map = new LinkedHashMap<>(); map.put("eventType", type); map.put("eventId", eventId); map.put("reversalId", reversal); map.put("strategy", STRATEGY); map.put("strategyVersion", VERSION); map.put("positionId", position.getId()); map.put("symbol", signal.symbol()); map.put("timeframe", signal.timeframe()); map.put("kernel", signal.kernel()); map.put("bandwidth", signal.bandwidth()); map.put("source", signal.source()); map.put("repaint", signal.repaint()); map.put("startupUniverseSessionId", universe.sessionId()); map.put("startupVolumeValue", universe.startupVolume(signal.symbol())); map.put("minimumVolumeThreshold", universe.minimumVolumeThreshold()); map.put("inStartupEntryUniverse", universe.symbols().contains(signal.symbol())); map.put("signalCandleOpenTime", signal.signalCandleOpenTime()); map.put("signalCandleCloseTime", signal.signalCandleCloseTime()); map.put("signalCandleClose", signal.signalCandleClose()); map.put("regressionCurrent", signal.regressionCurrent()); map.put("regressionPrevious", signal.regressionPrevious()); map.put("regressionTwoBarsAgo", signal.regressionTwoBarsAgo()); map.put("currentSlope", signal.currentSlope()); map.put("previousSlope", signal.previousSlope()); map.put("currentAtr14", signal.currentAtr14()); map.put("previousAtr14", signal.previousAtr14()); map.put("currentNormalizedSlope", signal.currentNormalizedSlope()); map.put("previousNormalizedSlope", signal.previousNormalizedSlope()); map.put("entryThreshold", signal.entryThreshold()); map.put("reversalThreshold", signal.reversalThreshold()); map.put("confirmationBars", signal.confirmationBars()); map.put("entrySignal", signal.entrySignal()); map.put("strongReversalSignal", signal.strongReversalSignal()); map.put("rawEntrySignal", signal.entrySignal()); map.put("rawStrongReversalSignal", signal.strongReversalSignal()); map.put("signalInverted", true); return map; }
    public static String entryKey(LaplaceSignalResult signal, PositionSide side) { return STRATEGY + ":" + signal.symbol() + ":" + signal.signalCandleCloseTime() + ":ENTRY:" + side; }
    public static String reversalKey(LaplaceSignalResult signal, PositionSide side) { return STRATEGY + ":" + signal.symbol() + ":" + signal.signalCandleCloseTime() + ":REVERSAL:" + side; }
    private record Execution(BigDecimal referencePrice, BigDecimal price, BigDecimal slippagePct) {}
    public record ReversalOutcome(LaplacePaperPositionEntity closed, LaplacePaperPositionEntity opened, String reversalId) {}
}
