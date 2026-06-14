package com.crypto.paper.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.ScanType;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.paper.log.SymbolTradeJsonlLogService;
import com.crypto.paper.log.V20PaperJsonlLogService;
import com.crypto.paper.model.PaperPositionEventType;
import com.crypto.persistence.entity.PaperPositionEventEntity;
import com.crypto.persistence.repository.PaperPositionEventRepository;
import com.crypto.persistence.repository.EntryCandidateRepository;
import com.crypto.scanner.model.EntryCandidateStatus;
import com.crypto.domain.model.EntrySignal;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.service.EntrySignalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
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
public class PaperPositionService {
    private static final int QUANTITY_SCALE = 12;

    private final PaperPositionRepository paperPositionRepository;
    private final EntrySignalService entrySignalService;
    private final ScannerProperties scannerProperties;
    private final JsonTextMapper jsonTextMapper;
    private final V20PnlCalculator v20PnlCalculator;

    @Autowired(required = false)
    private BinanceFuturesClient binanceFuturesClient;
    @Autowired(required = false)
    private PaperPositionEventRepository eventRepository;
    @Autowired(required = false)
    private EntryCandidateRepository entryCandidateRepository;
    @Autowired(required = false)
    private JsonlDecisionLogService jsonlDecisionLogService;
    @Autowired(required = false)
    private SymbolTradeJsonlLogService symbolTradeJsonlLogService;
    @Autowired(required = false)
    private V20PaperJsonlLogService v20PaperJsonlLogService;


    public PaperPositionService(PaperPositionRepository paperPositionRepository, EntrySignalService entrySignalService,
                                ScannerProperties scannerProperties, JsonTextMapper jsonTextMapper) {
        this(paperPositionRepository, entrySignalService, scannerProperties, jsonTextMapper, new V20PnlCalculator(scannerProperties));
    }

    public List<PaperPositionEntity> openPositionsFromLatestSignals() {
        return openPositionsFromSignals(entrySignalService.generateSignalsFromLatestScan()).openedPositions();
    }

    public PaperOpenSummary openPositionsFromScanRun(Long scanRunId) {
        return openPositionsFromSignals(entrySignalService.generateSignalsFromScanRun(scanRunId));
    }

    private PaperOpenSummary openPositionsFromSignals(List<EntrySignal> generatedSignals) {
        List<EntrySignal> safeGeneratedSignals = generatedSignals == null ? List.of() : generatedSignals;
        List<EntrySignal> strongSignals = safeGeneratedSignals.stream()
                .filter(this::isStrongSignal)
                .filter(this::isV20EntryScanAllowed)
                .toList();
        List<PaperPositionEntity> opened = openPositions(strongSignals);
        int openPositionsAfter = getOpenPositionsForValidation().size();
        return PaperOpenSummary.from(safeGeneratedSignals, strongSignals, opened, openPositionsAfter);
    }

    public List<PaperPositionEntity> openPositions(List<EntrySignal> signals) {
        List<EntrySignal> safeSignals = signals == null ? List.of() : signals;
        List<PaperPositionEntity> opened = safeSignals.stream()
                .filter(this::isEnterSignal)
                .map(this::openPosition)
                .filter(Objects::nonNull)
                .toList();
        log.info("PAPER_POSITIONS_OPEN_DONE requestedSignals={} opened={}", safeSignals.size(), opened.size());
        return opened;
    }

    public PaperPositionEntity openPosition(EntrySignal signal) {
        ScannerProperties.Paper config = scannerProperties.getPaper();
        if (!booleanValue(config.getEnabled(), true)) {
            return reject(signal, "PAPER_DISABLED");
        }
        if (signal == null) {
            return reject(null, "SIGNAL_NULL");
        }
        if (signal.getAction() == EntryAction.NO_ENTRY) {
            return reject(signal, "SIGNAL_NO_ENTRY");
        }
        if (!isV20Enabled() && !isStrongSignal(signal)) {
            return reject(signal, "SOURCE_CLASSIFICATION_NOT_STRONG");
        }
        if (!isEnterAction(signal.getAction())) {
            return reject(signal, "ACTION_NOT_ENTER");
        }
        if (signal.getSide() == null) {
            return reject(signal, "SIDE_MISSING");
        }
        if (!isV20Enabled() && (signal.getEntryPrice() == null || signal.getEntryPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            return reject(signal, "INVALID_ENTRY_PRICE");
        }
        if (signal.getScanRunId() == null) {
            return reject(signal, "SCAN_RUN_ID_MISSING");
        }
        if (signal.getSourceScanType() == null || signal.getSourceClassification() == null || signal.getCandidateId() == null) {
            return reject(signal, "SOURCE_METADATA_MISSING");
        }
        if (isV20Enabled() && signal.getSourceScanType() != ScanType.FOUR_HOUR) {
            return reject(signal, "V20_ONLY_FOUR_HOUR_SCAN_ENTRY");
        }
        if (isV20Enabled()) {
            if (!hasRequiredV20EntryData(signal)) {
                return reject(signal, "V20_DATA_NOT_READY");
            }
        } else if (!hasRequiredEntryIndicators(signal)) {
            return reject(signal, "DATA_NOT_READY");
        }
        if (signal.getRiskLevel() == RiskLevel.HIGH && !booleanValue(config.getAllowHighRisk(), false)) {
            return reject(signal, "HIGH_RISK_BLOCKED");
        }
        if (signal.getRiskLevel() == RiskLevel.MEDIUM && !booleanValue(config.getAllowMediumRisk(), true)) {
            return reject(signal, "MEDIUM_RISK_BLOCKED");
        }
        if (!booleanValue(config.getAllowMultipleOpenSameSymbol(), false)
                && paperPositionRepository.existsBySymbolAndStatusIn(signal.getSymbol(), activeStatuses())) {
            return reject(signal, "SYMBOL_ALREADY_OPEN");
        }

        List<PaperPositionEntity> openPositions = getOpenPositionsForValidation();
        if (!isV20Enabled() && openPositions.size() >= intValue(config.getMaxOpenPositions(), 5)) {
            return reject(signal, "MAX_OPEN_POSITIONS_REACHED");
        }
        long openSideCount = openPositions.stream()
                .filter(position -> position.getSide() == signal.getSide())
                .count();
        if (!isV20Enabled() && signal.getSide() == PositionSide.LONG
                && openSideCount >= intValue(config.getMaxOpenLongPositions(), 3)) {
            return reject(signal, "MAX_OPEN_LONG_REACHED");
        }
        if (!isV20Enabled() && signal.getSide() == PositionSide.SHORT
                && openSideCount >= intValue(config.getMaxOpenShortPositions(), 3)) {
            return reject(signal, "MAX_OPEN_SHORT_REACHED");
        }

        BookTicker bookTicker = resolveBookTicker(signal);
        if (bookTicker == null && (isV20Enabled() || binanceFuturesClient != null)) {
            return reject(signal, "MISSING_BOOK_TICKER");
        }
        BigDecimal entryPrice = bookTicker == null ? signal.getEntryPrice() : bookTicker.getMidPrice();
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return reject(signal, "INVALID_ENTRY_PRICE");
        }
        if (bookTicker != null) {
            log.info("PAPER_ENTRY_PRICE_READY symbol={} bid={} ask={} mid={}", signal.getSymbol(), bookTicker.getBidPrice(), bookTicker.getAskPrice(), entryPrice);
        }
        ScannerProperties.PaperExit exitConfig = scannerProperties.getPaperExit() == null
                ? new ScannerProperties.PaperExit()
                : scannerProperties.getPaperExit();
        ScannerProperties.PaperRisk riskConfig = scannerProperties.getPaperRisk() == null ? new ScannerProperties.PaperRisk() : scannerProperties.getPaperRisk();
        ScannerProperties.PaperCost costConfig = scannerProperties.getPaperCost() == null ? new ScannerProperties.PaperCost() : scannerProperties.getPaperCost();
        BigDecimal atr = signal.getAtr14_1h() == null ? entryPrice.multiply(new BigDecimal("0.012")) : signal.getAtr14_1h();
        RiskLevels risk = calculateRiskLevels(signal.getSide(), entryPrice, atr, riskConfig);
        BigDecimal v20TakeProfit = v20TakeProfit(signal.getSide(), entryPrice);
        BigDecimal v20StopLoss = v20StopLoss(signal.getSide(), entryPrice);
        BigDecimal marginUsdt = config.getMarginUsdt() == null ? new BigDecimal("100") : config.getMarginUsdt();
        int paperLeverage = intValue(config.getLeverage(), 5);
        BigDecimal leveragedNotionalUsdt = marginUsdt.multiply(BigDecimal.valueOf(paperLeverage));
        BigDecimal notionalUsdt = isV20Enabled() ? leveragedNotionalUsdt : bigDecimalValue(config.getDefaultNotionalUsdt(), "100");
        BigDecimal quantity = notionalUsdt.divide(entryPrice, QUANTITY_SCALE, RoundingMode.DOWN);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return reject(signal, "INVALID_QUANTITY");
        }
        Instant openedAt = Instant.now();
        PaperPositionEntity position = PaperPositionEntity.builder()
                .sourceScanRunId(signal.getScanRunId())
                .sourceScanType(signal.getSourceScanType())
                .sourceCandidateId(signal.getCandidateId())
                .strategyVersion(isV20Enabled() ? "V20" : null)
                .entryClose1h(signal.getClose1h())
                .entryEma20_1h(signal.getEma20_1h())
                .entryRsi14_1h(signal.getRsi14_1h())
                .entryMacdHist_1h(signal.getMacdHist_1h())
                .entryAtr14_1h(signal.getAtr14_1h())
                .entryVolumeRatio_1h(signal.getVolumeRatio_1h())
                .symbol(signal.getSymbol())
                .side(signal.getSide())
                .status(PaperPositionStatus.OPEN)
                .entryAction(signal.getAction())
                .entryPrice(entryPrice)
                .bidPrice(bookTicker == null ? null : bookTicker.getBidPrice())
                .askPrice(bookTicker == null ? null : bookTicker.getAskPrice())
                .midPrice(entryPrice)
                .quantity(quantity)
                .marginUsdt(isV20Enabled() ? marginUsdt : null)
                .unleveragedNotionalUsdt(isV20Enabled() ? marginUsdt : null)
                .leveragedNotionalUsdt(isV20Enabled() ? leveragedNotionalUsdt : null)
                .notionalUsdt(notionalUsdt)
                .feeMode(isV20Enabled() && config.getFee() != null ? config.getFee().getMode() : null)
                .feeRate(isV20Enabled() && config.getFee() != null ? ("TAKER".equalsIgnoreCase(config.getFee().getMode()) ? config.getFee().getTakerFeePct() : config.getFee().getMakerFeePct()) : null)
                .slippagePct(isV20Enabled() && config.getFee() != null ? config.getFee().getSlippagePct() : null)
                .leverage(isV20Enabled() ? paperLeverage : intValue(costConfig.getLeverage(), intValue(config.getLeverage(), 3)))
                .entryScore(signal.getScore())
                .marketRegime(signal.getMarketRegime())
                .entrySignalScore(signal.getScore())
                .entryBbScore(signal.getBbScore())
                .entryBbPercentB(signal.getBbPercentB())
                .entryBbWidth(signal.getBbWidth())
                .entryBbUpper(signal.getBbUpper())
                .entryBbMiddle(signal.getBbMiddle())
                .entryBbLower(signal.getBbLower())
                .entryBbUpperTouched(signal.getBbUpperTouched())
                .entryBbLowerTouched(signal.getBbLowerTouched())
                .entryBbUpperClosedOutside(signal.getBbUpperClosedOutside())
                .entryBbLowerClosedOutside(signal.getBbLowerClosedOutside())
                .entryBbReasonsJson(jsonTextMapper.toJson(signal.getBbReasons()))
                .longScore(signal.getLongScore())
                .shortScore(signal.getShortScore())
                .sourceClassification(signal.getSourceClassification())
                .directionBias(signal.getDirectionBias())
                .riskLevel(signal.getRiskLevel())
                .fundingRate(signal.getFundingRate())
                .openInterest(signal.getOpenInterest())
                .marketBreadthPct(signal.getMarketBreadthPct())
                .priceChange24hPct(signal.getPriceChange24hPct())
                .spreadPct(signal.getSpreadPct())
                .entryReason(signal.getSignalReason())
                .signalReason(signal.getSignalReason())
                .reasonsJson(jsonTextMapper.toJson(signal.getReasons()))
                .warningsJson(jsonTextMapper.toJson(signal.getWarnings()))
                .qualityStatus(isV20Enabled() ? "INSUFFICIENT_HISTORY" : null)
                .qualityScore(isV20Enabled() ? BigDecimal.ZERO : null)
                .openedAt(openedAt)
                .currentPrice(entryPrice)
                .highestPrice(entryPrice)
                .lowestPrice(entryPrice)
                .initialStop(isV20Enabled() ? null : risk.initialStop())
                .currentStop(isV20Enabled() ? null : risk.initialStop())
                .riskPerUnit(isV20Enabled() ? null : risk.riskPerUnit())
                .takeProfitPrice(isV20Enabled() ? v20TakeProfit : null)
                .stopLossPrice(isV20Enabled() ? v20StopLoss : null)
                .tp1(isV20Enabled() ? null : risk.tp1())
                .tp2(isV20Enabled() ? null : risk.tp2())
                .tp1Hit(false)
                .tp2Hit(false)
                .trailingActive(false)
                .remainingPositionPct(new BigDecimal("100"))
                .highestPriceSinceEntry(entryPrice)
                .lowestPriceSinceEntry(entryPrice)
                .barsInPosition(0)
                .entryPriceAdjusted(isV20Enabled() ? v20PnlCalculator.calculate(signal.getSide(), entryPrice, entryPrice).entryPriceAdjusted() : adjustedEntry(signal.getSide(), entryPrice, costConfig))
                .totalFeePct(costConfig.getTakerFeePct().multiply(BigDecimal.valueOf(200)))
                .totalSlippagePct(costConfig.getSlippagePct().multiply(BigDecimal.valueOf(200)))
                .maxFavorableMovePct(BigDecimal.ZERO)
                .maxAdverseMovePct(BigDecimal.ZERO)
                .barsHeld(0)
                .minutesHeld(0)
                .lastCheckedAt(openedAt)
                .takeProfitPct(bigDecimalValue(exitConfig.getTakeProfitPct(), "1.0"))
                .stopLossPct(bigDecimalValue(exitConfig.getStopLossPct(), "0.6"))
                .timeStopMinutes(intValue(exitConfig.getTimeStopMinutes(), 240))
                .build();
        if (isV20Enabled()) {
            position.setV20SignalSnapshotJson(v20SignalSnapshotJson(position, signal));
        }

        PaperPositionEntity saved = paperPositionRepository.save(position);
        writeOpenedEvent(saved);
        writeV20OpenedLog(saved, signal);
        writeSymbolTradeEntry(saved, signal);
        markCandidateUsed(saved.getSymbol());
        log.info(
                "PAPER_POSITION_OPENED openedAt={} symbol={} side={} entry={} stop={} tp1={} tp2={} id={} quantity={} notionalUsdt={} leverage={}",
                IstanbulTimeUtil.format(saved.getOpenedAt()),
                saved.getSymbol(),
                saved.getSide(),
                saved.getEntryPrice(),
                saved.getInitialStop(),
                saved.getTp1(),
                saved.getTp2(),
                saved.getId(),
                saved.getQuantity(),
                saved.getNotionalUsdt(),
                saved.getLeverage()
        );
        return saved;
    }




    private String v20SignalSnapshotJson(PaperPositionEntity position, EntrySignal signal) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("signalScore", position.getEntrySignalScore());
        snapshot.put("signalScoreReasons", signal == null ? List.of() : signal.getBbReasons());
        snapshot.put("fundingRate", position.getFundingRate());
        snapshot.put("fundingMa3", position.getFundingMa3());
        snapshot.put("oneHourTakerBuyRatio", position.getOneHourTakerBuyRatio());
        snapshot.put("fourHourTakerBuyRatio", position.getFourHourTakerBuyRatio());
        snapshot.put("distFromLow20Pct", position.getDistFromLow20Pct());
        snapshot.put("distFromHigh20Pct", position.getDistFromHigh20Pct());
        snapshot.put("diDiff", position.getDiDiff());
        snapshot.put("rsi14", position.getRsi14());
        snapshot.put("adx14", position.getAdx14());
        snapshot.put("atrPct", position.getAtrPct());
        snapshot.put("ema20Ema50CompPct", position.getEma20Ema50CompPct());
        snapshot.put("closeEma20DistPct", position.getCloseEma20DistPct());
        snapshot.put("bbPosition", position.getBbPosition());
        snapshot.put("closePosition", position.getClosePosition());
        snapshot.put("volumeRatio20", position.getVolumeRatio20());
        snapshot.put("rangePct", position.getRangePct());
        snapshot.put("qualityStatus", position.getQualityStatus());
        snapshot.put("qualityScore", position.getQualityScore());
        snapshot.put("reasons", signal == null ? List.of() : signal.getReasons());
        snapshot.put("warnings", signal == null ? List.of() : signal.getWarnings());
        return jsonTextMapper.toJson(snapshot);
    }

    private BigDecimal v20TakeProfit(PositionSide side, BigDecimal entryPrice) {
        ScannerProperties.SingleTpSl single = scannerProperties.getSingleTpSl() == null ? new ScannerProperties.SingleTpSl() : scannerProperties.getSingleTpSl();
        BigDecimal pct = side == PositionSide.SHORT ? single.getShortTpPct() : single.getLongTpPct();
        return side == PositionSide.SHORT ? entryPrice.multiply(BigDecimal.ONE.subtract(pct)) : entryPrice.multiply(BigDecimal.ONE.add(pct));
    }

    private BigDecimal v20StopLoss(PositionSide side, BigDecimal entryPrice) {
        ScannerProperties.SingleTpSl single = scannerProperties.getSingleTpSl() == null ? new ScannerProperties.SingleTpSl() : scannerProperties.getSingleTpSl();
        BigDecimal pct = side == PositionSide.SHORT ? single.getShortSlPct() : single.getLongSlPct();
        return side == PositionSide.SHORT ? entryPrice.multiply(BigDecimal.ONE.add(pct)) : entryPrice.multiply(BigDecimal.ONE.subtract(pct));
    }

    private void writeV20OpenedLog(PaperPositionEntity position, EntrySignal signal) {
        if (!isV20Enabled() || v20PaperJsonlLogService == null || position == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "POSITION_OPENED");
        payload.put("strategyVersion", "V20");
        payload.put("positionId", position.getId());
        payload.put("timeTr", v20PaperJsonlLogService.formatTr(position.getOpenedAt()));
        payload.put("symbol", position.getSymbol());
        payload.put("side", position.getSide().name());
        payload.put("status", position.getStatus().name());
        payload.put("entryPrice", position.getEntryPrice());
        payload.put("entryPriceAdjusted", position.getEntryPriceAdjusted());
        payload.put("marginUsdt", position.getMarginUsdt());
        payload.put("leverage", position.getLeverage());
        payload.put("leveragedNotionalUsdt", position.getLeveragedNotionalUsdt());
        payload.put("unleveragedNotionalUsdt", position.getUnleveragedNotionalUsdt());
        payload.put("quantity", position.getQuantity());
        payload.put("takeProfitPrice", position.getTakeProfitPrice());
        payload.put("stopLossPrice", position.getStopLossPrice());
        payload.put("feeMode", position.getFeeMode());
        payload.put("feeRate", position.getFeeRate());
        payload.put("slippagePct", position.getSlippagePct());
        payload.put("signalScore", position.getEntrySignalScore());
        payload.put("signalScoreReasons", signal == null ? List.of() : signal.getBbReasons());
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
        payload.put("ema20Ema50CompPct", position.getEma20Ema50CompPct());
        payload.put("closeEma20DistPct", position.getCloseEma20DistPct());
        payload.put("bbPosition", position.getBbPosition());
        payload.put("closePosition", position.getClosePosition());
        payload.put("volumeRatio20", position.getVolumeRatio20());
        payload.put("rangePct", position.getRangePct());
        payload.put("reasons", jsonTextMapper.toStringList(position.getReasonsJson()));
        payload.put("warnings", jsonTextMapper.toStringList(position.getWarningsJson()));
        payload.put("qualityStatus", position.getQualityStatus());
        payload.put("qualityScore", position.getQualityScore());
        payload.put("v20SignalSnapshot", position.getV20SignalSnapshotJson());
        v20PaperJsonlLogService.log(payload);
    }

    public RiskLevels calculateRiskLevels(PositionSide side, BigDecimal entryPrice, BigDecimal atr14, ScannerProperties.PaperRisk config) {
        BigDecimal raw = atr14.multiply(config.getAtrStopMultiplier());
        BigDecimal min = entryPrice.multiply(config.getMinStopDistancePct());
        BigDecimal max = entryPrice.multiply(config.getMaxStopDistancePct());
        BigDecimal distance = raw.max(min).min(max);
        BigDecimal initialStop = side == PositionSide.SHORT ? entryPrice.add(distance) : entryPrice.subtract(distance);
        BigDecimal riskPerUnit = side == PositionSide.SHORT ? initialStop.subtract(entryPrice) : entryPrice.subtract(initialStop);
        BigDecimal tp1 = side == PositionSide.SHORT ? entryPrice.subtract(riskPerUnit.multiply(config.getTp1RMultiple())) : entryPrice.add(riskPerUnit.multiply(config.getTp1RMultiple()));
        BigDecimal tp2 = side == PositionSide.SHORT ? entryPrice.subtract(riskPerUnit.multiply(config.getTp2RMultiple())) : entryPrice.add(riskPerUnit.multiply(config.getTp2RMultiple()));
        return new RiskLevels(initialStop, riskPerUnit, tp1, tp2);
    }

    public record RiskLevels(BigDecimal initialStop, BigDecimal riskPerUnit, BigDecimal tp1, BigDecimal tp2) {}

    public record PaperOpenSummary(
            int candidateCount,
            int signalCount,
            long enterLongCount,
            long enterShortCount,
            long noEntryCount,
            int openedCount,
            int skippedCount,
            int openPositionsAfter,
            List<PaperPositionEntity> openedPositions
    ) {
        private static PaperOpenSummary from(
                List<EntrySignal> generatedSignals,
                List<EntrySignal> strongSignals,
                List<PaperPositionEntity> openedPositions,
                int openPositionsAfter
        ) {
            List<EntrySignal> safeGeneratedSignals = generatedSignals == null ? List.of() : generatedSignals;
            List<EntrySignal> safeStrongSignals = strongSignals == null ? List.of() : strongSignals;
            List<PaperPositionEntity> safeOpenedPositions = openedPositions == null ? List.of() : openedPositions;
            long enterLongCount = safeGeneratedSignals.stream()
                    .filter(signal -> signal.getAction() == EntryAction.ENTER_LONG)
                    .count();
            long enterShortCount = safeGeneratedSignals.stream()
                    .filter(signal -> signal.getAction() == EntryAction.ENTER_SHORT)
                    .count();
            long noEntryCount = safeGeneratedSignals.stream()
                    .filter(signal -> signal.getAction() == EntryAction.NO_ENTRY)
                    .count();
            return new PaperOpenSummary(
                    safeGeneratedSignals.size(),
                    safeStrongSignals.size(),
                    enterLongCount,
                    enterShortCount,
                    noEntryCount,
                    safeOpenedPositions.size(),
                    Math.max(0, safeStrongSignals.size() - safeOpenedPositions.size()),
                    openPositionsAfter,
                    safeOpenedPositions
            );
        }
    }


    private BookTicker resolveBookTicker(EntrySignal signal) {
        if (binanceFuturesClient == null || signal == null || signal.getSymbol() == null) {
            return null;
        }
        List<BookTicker> tickers = binanceFuturesClient.getAllBookTickers();
        return (tickers == null ? List.<BookTicker>of() : tickers).stream()
                .filter(ticker -> signal.getSymbol().equals(ticker.getSymbol()))
                .filter(ticker -> ticker.getBidPrice() != null && ticker.getAskPrice() != null && ticker.getMidPrice() != null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal adjustedEntry(PositionSide side, BigDecimal entryPrice, ScannerProperties.PaperCost cost) {
        BigDecimal slip = cost.getSlippagePct();
        return side == PositionSide.SHORT ? entryPrice.multiply(BigDecimal.ONE.subtract(slip)) : entryPrice.multiply(BigDecimal.ONE.add(slip));
    }

    private void writeOpenedEvent(PaperPositionEntity position) {
        if (eventRepository != null) {
            eventRepository.save(PaperPositionEventEntity.builder()
                    .position(position)
                    .eventTimeUtc(position.getOpenedAt())
                    .eventType(PaperPositionEventType.OPENED)
                    .price(position.getEntryPrice())
                    .adjustedPrice(position.getEntryPriceAdjusted())
                    .leverage(position.getLeverage())
                    .reason("PAPER_POSITION_OPENED")
                    .detailsJson(openedDetailsJson(position))
                    .build());
        }
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logPaper(openedDetails(position));
            jsonlDecisionLogService.logPaperTrade(entryTradeLog(position));
        }
    }

    private void writeSymbolTradeEntry(PaperPositionEntity position, EntrySignal signal) {
        if (symbolTradeJsonlLogService == null || Boolean.TRUE.equals(position.getSymbolTradeEntryLogged())) {
            return;
        }
        if (symbolTradeJsonlLogService.logEntry(position, signal)) {
            position.setSymbolTradeEntryLogged(true);
            paperPositionRepository.save(position);
        }
    }

    private String openedDetailsJson(PaperPositionEntity position) {
        return jsonTextMapper.toJson(openedDetails(position));
    }

    private Map<String, Object> openedDetails(PaperPositionEntity position) {
        return Map.ofEntries(
                Map.entry("event", "PAPER_POSITION_OPENED"),
                Map.entry("time", position.getOpenedAt()),
                Map.entry("symbol", position.getSymbol()),
                Map.entry("side", position.getSide().name()),
                Map.entry("positionId", position.getId() == null ? "" : position.getId()),
                Map.entry("scanRunId", position.getSourceScanRunId()),
                Map.entry("sourceScanType", position.getSourceScanType() == null ? "" : position.getSourceScanType().name()),
                Map.entry("sourceClassification", position.getSourceClassification() == null ? "" : position.getSourceClassification().name()),
                Map.entry("candidateId", position.getSourceCandidateId()),
                Map.entry("entryPrice", position.getEntryPrice()),
                Map.entry("baseEntryScore", nullToEmpty(position.getEntryScore() == null || position.getEntryBbScore() == null ? position.getEntryScore() : BigDecimal.valueOf(position.getEntryScore()).subtract(position.getEntryBbScore()))),
                Map.entry("bbScore", nullToEmpty(position.getEntryBbScore())),
                Map.entry("finalEntryScore", nullToEmpty(position.getEntryScore())),
                Map.entry("bbReasons", jsonTextMapper.toStringList(position.getEntryBbReasonsJson())),
                Map.entry("bbPercentB", nullToEmpty(position.getEntryBbPercentB())),
                Map.entry("bbWidth", nullToEmpty(position.getEntryBbWidth())),
                Map.entry("bbUpper", nullToEmpty(position.getEntryBbUpper())),
                Map.entry("bbMiddle", nullToEmpty(position.getEntryBbMiddle())),
                Map.entry("bbLower", nullToEmpty(position.getEntryBbLower())),
                Map.entry("bbUpperTouched", nullToEmpty(position.getEntryBbUpperTouched())),
                Map.entry("bbLowerTouched", nullToEmpty(position.getEntryBbLowerTouched())),
                Map.entry("bbUpperClosedOutside", nullToEmpty(position.getEntryBbUpperClosedOutside())),
                Map.entry("bbLowerClosedOutside", nullToEmpty(position.getEntryBbLowerClosedOutside()))
        );
    }


    private Map<String, Object> entryTradeLog(PaperPositionEntity position) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ENTRY");
        payload.put("positionId", position.getId() == null ? "" : position.getId());
        payload.put("scanRunId", position.getSourceScanRunId());
        payload.put("sourceScanType", position.getSourceScanType() == null ? "" : position.getSourceScanType().name());
        payload.put("sourceClassification", position.getSourceClassification() == null ? "" : position.getSourceClassification().name());
        payload.put("candidateId", position.getSourceCandidateId());
        payload.put("symbol", position.getSymbol());
        payload.put("time", position.getOpenedAt());
        payload.put("side", position.getSide().name());
        payload.put("entryPrice", position.getEntryPrice());
        payload.put("qty", position.getQuantity());
        payload.put("tp1", position.getTp1());
        payload.put("tp2", position.getTp2());
        payload.put("slPrice", position.getInitialStop());
        payload.put("initialStop", position.getInitialStop());
        payload.put("currentStop", position.getInitialStop());
        payload.put("tp1Hit", false);
        payload.put("tp2Hit", false);
        payload.put("trailingActive", false);
        payload.put("remainingPositionPct", new BigDecimal("100"));
        payload.put("close1h", position.getEntryClose1h());
        payload.put("ema20_1h", position.getEntryEma20_1h());
        payload.put("rsi14_1h", position.getEntryRsi14_1h());
        payload.put("macdHist_1h", position.getEntryMacdHist_1h());
        payload.put("atr14_1h", position.getEntryAtr14_1h());
        payload.put("volumeRatio_1h", position.getEntryVolumeRatio_1h());
        payload.put("matchedSetup", nullToEmpty(position.getEntryReason()));
        payload.put("entryReason", nullToEmpty(position.getEntryReason()));
        payload.put("marketRegime", "");
        payload.put("entryScore", nullToEmpty(position.getEntryScore()));
        payload.put("longScore", nullToEmpty(position.getLongScore()));
        payload.put("shortScore", nullToEmpty(position.getShortScore()));
        payload.put("riskLevel", position.getRiskLevel() == null ? "" : position.getRiskLevel().name());
        payload.put("leverage", nullToEmpty(position.getLeverage()));
        payload.put("notionalUsdt", nullToEmpty(position.getNotionalUsdt()));
        payload.put("reasons", jsonTextMapper.toStringList(position.getReasonsJson()));
        payload.put("warnings", jsonTextMapper.toStringList(position.getWarningsJson()));
        payload.put("qualityStatus", position.getQualityStatus());
        payload.put("qualityScore", position.getQualityScore());
        payload.put("v20SignalSnapshot", position.getV20SignalSnapshotJson());
        return payload;
    }

    private boolean hasRequiredEntryIndicators(EntrySignal signal) {
        return signal.getClose1h() != null
                && signal.getEma20_1h() != null
                && signal.getRsi14_1h() != null
                && signal.getMacdHist_1h() != null
                && signal.getAtr14_1h() != null
                && signal.getVolumeRatio_1h() != null;
    }

    private boolean hasRequiredV20EntryData(EntrySignal signal) {
        return signal != null
                && isEnterAction(signal.getAction())
                && signal.getSymbol() != null
                && !signal.getSymbol().isBlank()
                && signal.getSide() != null
                && isStrongSignal(signal)
                && signal.getScore() != null;
    }

    private boolean isV20EntryScanAllowed(EntrySignal signal) {
        return !isV20Enabled() || (signal != null && signal.getSourceScanType() == ScanType.FOUR_HOUR);
    }

    private Object nullToEmpty(Object value) {
        return value == null ? "" : value;
    }

    private void markCandidateUsed(String symbol) {
        if (entryCandidateRepository == null || symbol == null) { return; }
        entryCandidateRepository.findFirstBySymbolAndStatusOrderByCreatedAtDesc(symbol, EntryCandidateStatus.ACTIVE).ifPresent(candidate -> {
            candidate.setStatus(EntryCandidateStatus.USED);
            entryCandidateRepository.save(candidate);
        });
    }

    private BigDecimal defaultBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    public List<PaperPositionEntity> getOpenPositions() {
        List<PaperPositionEntity> openPositions = paperPositionRepository.findByStatusOrderByOpenedAtDesc(
                PaperPositionStatus.OPEN
        );
        log.info("PAPER_OPEN_POSITIONS_READY count={}", openPositions.size());
        return openPositions;
    }

    private List<PaperPositionEntity> getOpenPositionsForValidation() {
        return paperPositionRepository.findByStatusInOrderByOpenedAtDesc(activeStatuses());
    }

    private List<PaperPositionStatus> activeStatuses() {
        return List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED);
    }

    private boolean isV20Enabled() {
        return scannerProperties.getV20() != null && Boolean.TRUE.equals(scannerProperties.getV20().getEnabled());
    }

    private boolean isStrongSignal(EntrySignal signal) {
        return signal != null
                && (signal.getSourceClassification() == CoinClassification.STRONG_LONG
                || signal.getSourceClassification() == CoinClassification.STRONG_SHORT);
    }

    private boolean isEnterSignal(EntrySignal signal) {
        return signal != null && isEnterAction(signal.getAction());
    }

    private boolean isEnterAction(EntryAction action) {
        return action == EntryAction.ENTER_LONG || action == EntryAction.ENTER_SHORT;
    }

    private PaperPositionEntity reject(EntrySignal signal, String reason) {
        log.info("PAPER_POSITION_REJECTED symbol={} reason={}", signal == null ? null : signal.getSymbol(), reason);
        return null;
    }

    private boolean booleanValue(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int intValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private BigDecimal bigDecimalValue(BigDecimal value, String defaultValue) {
        return value == null ? new BigDecimal(defaultValue) : value;
    }
}
