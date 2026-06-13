package com.crypto.scanner.service;

import com.crypto.common.enums.EntryAction;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.paper.log.V20PaperJsonlLogService;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.paper.model.V20PnlResult;
import com.crypto.paper.service.V20PnlCalculator;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.V20ScanEntrySummary;
import com.crypto.scanner.model.V20ScanInput;
import com.crypto.scanner.model.V20SignalResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class V20ScanEntryService {
    private static final BigDecimal LONG_TP = new BigDecimal("1.0200");
    private static final BigDecimal LONG_SL = new BigDecimal("0.9860");
    private static final BigDecimal SHORT_TP = new BigDecimal("0.9800");
    private static final BigDecimal SHORT_SL = new BigDecimal("1.0140");
    private static final int PRICE_SCALE = 12;

    private final ScannerProperties scannerProperties;
    private final V20SignalService v20SignalService;
    private final V20PnlCalculator v20PnlCalculator;

    @Autowired(required = false)
    private JsonlDecisionLogService jsonlDecisionLogService;
    @Autowired(required = false)
    private V20PaperJsonlLogService v20PaperJsonlLogService;

    public V20ScanEntrySummary evaluate(List<V20ScanInput> inputs, Set<String> alreadyOpenSymbols) {
        List<V20ScanInput> safeInputs = inputs == null ? List.of() : inputs;
        Set<String> openSymbols = alreadyOpenSymbols == null ? new HashSet<>() : new HashSet<>(alreadyOpenSymbols);
        List<PaperPositionEntity> opened = new ArrayList<>();
        int longCandidates = 0, shortCandidates = 0, skippedOpen = 0, skippedInvalidQty = 0, skippedData = 0, skippedPanic = 0;

        for (V20ScanInput input : safeInputs) {
            if (input == null || input.getSymbol() == null) { skippedData++; continue; }
            if (!Boolean.TRUE.equals(scannerProperties.getV20().getEnabled())) { continue; }
            if (openSymbols.contains(input.getSymbol())) { skippedOpen++; logEntry(input, null, null, "SYMBOL_ALREADY_OPEN", null); continue; }
            if (input.getMarketRegime() == MarketRegime.PANIC) { skippedPanic++; logEntry(input, null, null, "NO_ENTRY_MARKET_PANIC", null); continue; }
            if (input.getBookTicker() == null || midPrice(input.getBookTicker()) == null) { skippedData++; logEntry(input, null, null, "NO_ENTRY_MISSING_BOOK_TICKER", null); continue; }

            V20SignalResult longResult = v20SignalService.evaluateLong(input.getFourHour(), input.getOneHour(), input.getFundingRate(), input.getFundingRates(), input.getQualityPass(), input.getQualityFail());
            V20SignalResult shortResult = v20SignalService.evaluateShort(input.getFourHour(), input.getOneHour(), input.getFundingRate(), input.getFundingRates(), input.getQualityPass(), input.getQualityFail());
            boolean longPass = longResult.isBaseSignalPass() && longResult.isEntryFiltersPass();
            boolean shortPass = shortResult.isBaseSignalPass() && shortResult.isEntryFiltersPass();
            if (longPass) longCandidates++;
            if (shortPass) shortCandidates++;
            PositionSide chosenSide = chooseSide(input, longResult, shortResult, longPass, shortPass);
            V20SignalResult chosenResult = chosenSide == PositionSide.SHORT ? shortResult : longResult;
            if (chosenSide == null) { if (longResult.getReasons().contains("DATA_NOT_READY") || shortResult.getReasons().contains("DATA_NOT_READY")) skippedData++; continue; }
            BigDecimal entryPrice = midPrice(input.getBookTicker());
            BigDecimal leveragedNotional = v20PnlCalculator.leveragedNotionalUsdt();
            BigDecimal quantity = normalizeQuantity(input.getSymbolInfo(), leveragedNotional, entryPrice);
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) { skippedInvalidQty++; logEntry(input, chosenSide, chosenResult, "INVALID_QUANTITY", entryPrice); continue; }
            PaperPositionEntity position = buildPosition(input, chosenSide, chosenResult, entryPrice, quantity);
            opened.add(position);
            openSymbols.add(input.getSymbol());
            logEntry(input, chosenSide, chosenResult, null, entryPrice);
            logOpened(position, chosenResult);
        }

        int openedLong = (int) opened.stream().filter(p -> p.getSide() == PositionSide.LONG).count();
        int openedShort = (int) opened.stream().filter(p -> p.getSide() == PositionSide.SHORT).count();
        log.info("V20_SCAN_SUMMARY scanType=V20 scannedSymbolCount={} longCandidateCount={} shortCandidateCount={} openedLongCount={} openedShortCount={} skippedOpenPositionCount={} skippedInvalidQuantityCount={} skippedDataNotReadyCount={} skippedPanicCount={}",
                safeInputs.size(), longCandidates, shortCandidates, openedLong, openedShort, skippedOpen, skippedInvalidQty, skippedData, skippedPanic);
        return V20ScanEntrySummary.builder().scannedSymbolCount(safeInputs.size()).longCandidateCount(longCandidates).shortCandidateCount(shortCandidates)
                .openedLongCount(openedLong).openedShortCount(openedShort).skippedOpenPositionCount(skippedOpen).skippedInvalidQuantityCount(skippedInvalidQty)
                .skippedDataNotReadyCount(skippedData).skippedPanicCount(skippedPanic).openedPositions(opened).build();
    }

    private PositionSide chooseSide(V20ScanInput input, V20SignalResult longResult, V20SignalResult shortResult, boolean longPass, boolean shortPass) {
        if (longPass && !shortPass) return PositionSide.LONG;
        if (shortPass && !longPass) return PositionSide.SHORT;
        if (!longPass) { logEntry(input, null, longResult, "NO_ENTRY", null); return null; }
        int diff = longResult.getSignalScore() - shortResult.getSignalScore();
        if (Math.abs(diff) <= 1) { logEntry(input, null, longResult, "CANDIDATE_CONFLICT_SKIPPED", null); return null; }
        return diff > 0 ? PositionSide.LONG : PositionSide.SHORT;
    }

    private PaperPositionEntity buildPosition(V20ScanInput input, PositionSide side, V20SignalResult result, BigDecimal entryPrice, BigDecimal quantity) {
        BigDecimal margin = v20PnlCalculator.marginUsdt();
        BigDecimal leveragedNotional = v20PnlCalculator.leveragedNotionalUsdt();
        TechnicalSnapshot fourHour = input.getFourHour();
        BigDecimal takeProfit = side == PositionSide.LONG ? entryPrice.multiply(LONG_TP) : entryPrice.multiply(SHORT_TP);
        BigDecimal stopLoss = side == PositionSide.LONG ? entryPrice.multiply(LONG_SL) : entryPrice.multiply(SHORT_SL);
        return PaperPositionEntity.builder()
                .sourceScanRunId(input.getScanRunId()).strategyVersion("V20").symbol(input.getSymbol()).side(side).status(PaperPositionStatus.OPEN)
                .entryAction(side == PositionSide.LONG ? EntryAction.ENTER_LONG : EntryAction.ENTER_SHORT)
                .entryPrice(entryPrice).bidPrice(input.getBookTicker().getBidPrice()).askPrice(input.getBookTicker().getAskPrice()).midPrice(entryPrice)
                .quantity(quantity).marginUsdt(margin).unleveragedNotionalUsdt(margin).leveragedNotionalUsdt(leveragedNotional).notionalUsdt(leveragedNotional).leverage(v20PnlCalculator.leverage())
                .feeMode(v20PnlCalculator.feeMode()).feeRate(v20PnlCalculator.feeRate()).slippagePct(v20PnlCalculator.slippagePct())
                .entryPriceAdjusted(v20PnlCalculator.calculate(side, entryPrice, entryPrice, margin.divide(entryPrice, 12, RoundingMode.DOWN), quantity).entryPriceAdjusted())
                .entryScore(result.getSignalScore()).entrySignalScore(result.getSignalScore())
                .fundingRate(input.getFundingRate()).fundingMa3(v20SignalService.fundingMa3(input.getFundingRates()))
                .oneHourTakerBuyRatio(input.getOneHour().getTakerBuyRatio()).fourHourTakerBuyRatio(fourHour.getTakerBuyRatio())
                .distFromLow20Pct(fourHour.getDistFromLow20Pct()).distFromHigh20Pct(fourHour.getDistFromHigh20Pct()).diDiff(fourHour.getDiDiff())
                .rsi14(fourHour.getRsi14()).adx14(fourHour.getAdx14()).atrPct(fourHour.getAtrPct()).ema20Ema50CompPct(fourHour.getEma20Ema50CompPct())
                .closeEma20DistPct(fourHour.getCloseEma20DistPct()).bbPosition(fourHour.getBbPosition()).closePosition(fourHour.getClosePosition())
                .volumeRatio20(fourHour.getVolumeRatio()).rangePct(fourHour.getRangePct()).openedAt(Instant.now()).currentPrice(entryPrice)
                .initialStop(null).currentStop(null)
                .takeProfitPrice(takeProfit.setScale(PRICE_SCALE, RoundingMode.HALF_UP)).stopLossPrice(stopLoss.setScale(PRICE_SCALE, RoundingMode.HALF_UP))
                .tp1(null).tp2(null).tp1Hit(false).tp2Hit(false).trailingActive(false)
                .remainingPositionPct(new BigDecimal("100")).highestPriceSinceEntry(entryPrice).lowestPriceSinceEntry(entryPrice).barsInPosition(0)
                .takeProfitPct(new BigDecimal("2.0")).stopLossPct(new BigDecimal("1.4")).build();
    }

    private BigDecimal normalizeQuantity(SymbolInfo symbolInfo, BigDecimal notional, BigDecimal entryPrice) {
        if (notional == null) notional = new BigDecimal("100");
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal raw = notional.divide(entryPrice, 12, RoundingMode.DOWN);
        BigDecimal step = symbolInfo == null ? null : symbolInfo.getStepSize();
        BigDecimal quantity = step == null || step.compareTo(BigDecimal.ZERO) <= 0 ? raw : raw.divide(step, 0, RoundingMode.DOWN).multiply(step);
        if (symbolInfo != null && symbolInfo.getMinQty() != null && quantity.compareTo(symbolInfo.getMinQty()) < 0) return null;
        BigDecimal effectiveNotional = quantity.multiply(entryPrice);
        if (symbolInfo != null && symbolInfo.getMinNotional() != null && effectiveNotional.compareTo(symbolInfo.getMinNotional()) < 0) return null;
        return quantity.stripTrailingZeros();
    }

    private BigDecimal midPrice(BookTicker bookTicker) {
        if (bookTicker == null || bookTicker.getBidPrice() == null || bookTicker.getAskPrice() == null) return null;
        return bookTicker.getBidPrice().add(bookTicker.getAskPrice()).divide(BigDecimal.valueOf(2), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private void logEntry(V20ScanInput input, PositionSide side, V20SignalResult result, String blockReason, BigDecimal entryPrice) {
        String action = side == null ? "NO_ENTRY" : (side == PositionSide.LONG ? "ENTER_LONG" : "ENTER_SHORT");
        log.info("ENTRY_SIGNAL_EVALUATED strategyVersion=V20 symbol={} side={} action={} blockReason={} signalScore={} entryPriceSource=BOOK_TICKER_MID entryPrice={} reasons={}",
                input.getSymbol(), side, action, blockReason, result == null ? 0 : result.getSignalScore(), entryPrice, result == null ? List.of() : result.getReasons());
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logEntry(entryPayload(input, side, action, blockReason, result, entryPrice));
        }
        if (v20PaperJsonlLogService != null && side != null && blockReason == null) {
            v20PaperJsonlLogService.log(entryPayload(input, side, action, blockReason, result, entryPrice));
        }
    }

    private void logOpened(PaperPositionEntity position, V20SignalResult result) {
        log.info("POSITION_OPENED strategyVersion=V20 symbol={} side={} entryPrice={} quantity={} notionalUsdt={} takeProfitPrice={} stopLossPrice={} signalScore={} signalScoreReasons={}",
                position.getSymbol(), position.getSide(), position.getEntryPrice(), position.getQuantity(), position.getNotionalUsdt(), position.getTakeProfitPrice(), position.getStopLossPrice(),
                position.getEntrySignalScore(), result.getReasons());
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logPaper(openedPayload(position, result));
        }
        if (v20PaperJsonlLogService != null) {
            v20PaperJsonlLogService.log(openedPayload(position, result));
        }
    }

    private LinkedHashMap<String, Object> entryPayload(V20ScanInput input, PositionSide side, String action, String blockReason, V20SignalResult result, BigDecimal entryPrice) {
        TechnicalSnapshot fourHour = input.getFourHour();
        TechnicalSnapshot oneHour = input.getOneHour();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "POSITION_OPENED");
        payload.put("event", "POSITION_OPENED");
        payload.put("timeTr", v20PaperJsonlLogService == null ? Instant.now() : v20PaperJsonlLogService.formatTr(Instant.now()));
        payload.put("strategyVersion", "V20");
        payload.put("scanRunId", input.getScanRunId());
        payload.put("symbol", input.getSymbol());
        payload.put("side", side == null ? "" : side.name());
        payload.put("action", action);
        payload.put("blockReason", blockReason == null ? "" : blockReason);
        payload.put("signalScore", result == null ? 0 : result.getSignalScore());
        payload.put("signalScoreReasons", result == null ? List.of() : result.getReasons());
        putTechnical(payload, fourHour, oneHour, input);
        payload.put("entryPriceSource", "BOOK_TICKER_MID");
        payload.put("entryPrice", entryPrice == null ? "" : entryPrice);
        payload.put("marginUsdt", v20PnlCalculator.marginUsdt());
        payload.put("leverage", v20PnlCalculator.leverage());
        payload.put("leveragedNotionalUsdt", v20PnlCalculator.leveragedNotionalUsdt());
        payload.put("unleveragedNotionalUsdt", v20PnlCalculator.marginUsdt());
        payload.put("feeMode", v20PnlCalculator.feeMode());
        payload.put("feeRate", v20PnlCalculator.feeRate());
        payload.put("slippagePct", v20PnlCalculator.slippagePct());
        payload.put("reasons", result == null ? List.of() : result.getReasons());
        payload.put("warnings", List.of());
        return payload;
    }

    private LinkedHashMap<String, Object> openedPayload(PaperPositionEntity position, V20SignalResult result) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "POSITION_OPENED");
        payload.put("event", "POSITION_OPENED");
        payload.put("timeTr", v20PaperJsonlLogService == null ? position.getOpenedAt() : v20PaperJsonlLogService.formatTr(position.getOpenedAt()));
        payload.put("strategyVersion", "V20");
        payload.put("positionId", position.getId());
        payload.put("symbol", position.getSymbol());
        payload.put("side", position.getSide().name());
        payload.put("entryPrice", position.getEntryPrice());
        payload.put("entryPriceAdjusted", position.getEntryPriceAdjusted());
        payload.put("marginUsdt", position.getMarginUsdt());
        payload.put("leverage", position.getLeverage());
        payload.put("leveragedNotionalUsdt", position.getLeveragedNotionalUsdt());
        payload.put("unleveragedNotionalUsdt", position.getUnleveragedNotionalUsdt());
        payload.put("quantity", position.getQuantity());
        payload.put("notionalUsdt", position.getNotionalUsdt());
        payload.put("feeMode", position.getFeeMode());
        payload.put("feeRate", position.getFeeRate());
        payload.put("slippagePct", position.getSlippagePct());
        payload.put("takeProfitPrice", position.getTakeProfitPrice());
        payload.put("stopLossPrice", position.getStopLossPrice());
        payload.put("signalScore", position.getEntrySignalScore());
        payload.put("signalScoreReasons", result.getReasons());
        payload.put("fundingRate", position.getFundingRate());
        payload.put("fundingMa3", position.getFundingMa3());
        payload.put("oneHourTakerBuyRatio", position.getOneHourTakerBuyRatio());
        payload.put("fourHourTakerBuyRatio", position.getFourHourTakerBuyRatio());
        payload.put("distFromLow20Pct", position.getDistFromLow20Pct());
        payload.put("distFromHigh20Pct", position.getDistFromHigh20Pct());
        payload.put("diDiff", position.getDiDiff());
        payload.put("rsi14", position.getRsi14());
        payload.put("adx14", position.getAdx14());
        payload.put("atrPct", position.getAtrPct());
        payload.put("bbPosition", position.getBbPosition());
        payload.put("closePosition", position.getClosePosition());
        payload.put("volumeRatio20", position.getVolumeRatio20());
        payload.put("rangePct", position.getRangePct());
        payload.put("reasons", result.getReasons());
        payload.put("warnings", List.of());
        return payload;
    }

    private void putTechnical(LinkedHashMap<String, Object> payload, TechnicalSnapshot fourHour, TechnicalSnapshot oneHour, V20ScanInput input) {
        payload.put("rsi14", fourHour == null ? "" : fourHour.getRsi14());
        payload.put("adx14", fourHour == null ? "" : fourHour.getAdx14());
        payload.put("atrPct", fourHour == null ? "" : fourHour.getAtrPct());
        payload.put("ema20Ema50CompPct", fourHour == null ? "" : fourHour.getEma20Ema50CompPct());
        payload.put("closeEma20DistPct", fourHour == null ? "" : fourHour.getCloseEma20DistPct());
        payload.put("distFromLow20Pct", fourHour == null ? "" : fourHour.getDistFromLow20Pct());
        payload.put("distFromHigh20Pct", fourHour == null ? "" : fourHour.getDistFromHigh20Pct());
        payload.put("diDiff", fourHour == null ? "" : fourHour.getDiDiff());
        payload.put("takerBuyRatio", fourHour == null ? "" : fourHour.getTakerBuyRatio());
        payload.put("oneHourTakerBuyRatio", oneHour == null ? "" : oneHour.getTakerBuyRatio());
        payload.put("fourHourTakerBuyRatio", fourHour == null ? "" : fourHour.getTakerBuyRatio());
        payload.put("closePosition", fourHour == null ? "" : fourHour.getClosePosition());
        payload.put("oneHourClosePosition", oneHour == null ? "" : oneHour.getClosePosition());
        payload.put("bbPosition", fourHour == null ? "" : fourHour.getBbPosition());
        payload.put("volumeRatio20", fourHour == null ? "" : fourHour.getVolumeRatio());
        payload.put("rangePct", fourHour == null ? "" : fourHour.getRangePct());
        payload.put("fundingRate", input.getFundingRate());
        payload.put("fundingMa3", v20SignalService.fundingMa3(input.getFundingRates()));
    }
}
