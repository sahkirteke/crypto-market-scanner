package com.crypto.paper.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.Kline;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.paper.log.SymbolTradeJsonlLogService;
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
    private static final String SHORT_SIGNAL_INVERSION_MODE = "SHORT_SIGNAL_INVERSION_MODE";
    private static final String SHORT_SIGNAL_INVERTED_TO_LONG = "SHORT_SIGNAL_INVERTED_TO_LONG";

    private final PaperPositionRepository paperPositionRepository;
    private final EntrySignalService entrySignalService;
    private final ScannerProperties scannerProperties;
    private final JsonTextMapper jsonTextMapper;

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
        if (!isStrongSignal(signal)) {
            return reject(signal, "SOURCE_CLASSIFICATION_NOT_STRONG");
        }
        if (!isEnterAction(signal.getAction())) {
            return reject(signal, "ACTION_NOT_ENTER");
        }
        if (signal.getSide() == null) {
            return reject(signal, "SIDE_MISSING");
        }
        if (signal.getEntryPrice() == null || signal.getEntryPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return reject(signal, "INVALID_ENTRY_PRICE");
        }
        if (signal.getScanRunId() == null) {
            return reject(signal, "SCAN_RUN_ID_MISSING");
        }
        if (signal.getSourceScanType() == null || signal.getSourceClassification() == null || signal.getCandidateId() == null) {
            return reject(signal, "SOURCE_METADATA_MISSING");
        }
        if (!hasRequiredEntryIndicators(signal)) {
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

        SignalExecution execution = resolveSignalExecution(signal);

        List<PaperPositionEntity> openPositions = getOpenPositionsForValidation();
        if (openPositions.size() >= intValue(config.getMaxOpenPositions(), 5)) {
            return reject(signal, "MAX_OPEN_POSITIONS_REACHED");
        }
        long openSideCount = openPositions.stream()
                .filter(position -> effectiveExecutionSide(position) == execution.executionSide())
                .count();
        if (execution.executionSide() == PositionSide.LONG
                && openSideCount >= intValue(config.getMaxOpenLongPositions(), 3)) {
            return reject(signal, "MAX_OPEN_LONG_REACHED");
        }
        if (execution.executionSide() == PositionSide.SHORT
                && openSideCount >= intValue(config.getMaxOpenShortPositions(), 3)) {
            return reject(signal, "MAX_OPEN_SHORT_REACHED");
        }

        BookTicker bookTicker = resolveBookTicker(signal);
        if (bookTicker == null && binanceFuturesClient != null) {
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
        RiskLevels risk = calculateRiskLevels(PositionSide.LONG, entryPrice, atr, riskConfig);
        EntryFilterResult entryFilters = evaluateEntryFilters(signal, entryPrice);
        if (!entryFilters.passed()) {
            return reject(signal, entryFilters.rejectReason());
        }
        BigDecimal notionalUsdt = bigDecimalValue(config.getDefaultNotionalUsdt(), "100");
        BigDecimal quantity = notionalUsdt.divide(entryPrice, QUANTITY_SCALE, RoundingMode.DOWN);
        BigDecimal prev3_5mReturn = resolvePrev3_5mReturn(signal);
        Instant openedAt = Instant.now();
        PaperPositionEntity position = PaperPositionEntity.builder()
                .sourceScanRunId(signal.getScanRunId())
                .sourceScanType(signal.getSourceScanType())
                .sourceCandidateId(signal.getCandidateId())
                .entryClose1h(signal.getClose1h())
                .entryEma20_1h(signal.getEma20_1h())
                .entryRsi14_1h(signal.getRsi14_1h())
                .entryMacdHist_1h(signal.getMacdHist_1h())
                .entryAtr14_1h(signal.getAtr14_1h())
                .entryVolumeRatio_1h(signal.getVolumeRatio_1h())
                .symbol(signal.getSymbol())
                .side(execution.executionSide())
                .sourceSignalSide(execution.signalSide())
                .executionSide(execution.executionSide())
                .signalInverted(execution.signalInverted())
                .inversionReason(execution.inversionReason())
                .status(PaperPositionStatus.OPEN)
                .entryAction(signal.getAction())
                .entryPrice(entryPrice)
                .bidPrice(bookTicker == null ? null : bookTicker.getBidPrice())
                .askPrice(bookTicker == null ? null : bookTicker.getAskPrice())
                .midPrice(entryPrice)
                .quantity(quantity)
                .notionalUsdt(notionalUsdt)
                .leverage(intValue(costConfig.getLeverage(), intValue(config.getLeverage(), 3)))
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
                .openedAt(openedAt)
                .currentPrice(entryPrice)
                .highestPrice(entryPrice)
                .lowestPrice(entryPrice)
                .initialStop(risk.initialStop())
                .currentStop(risk.initialStop())
                .riskPerUnit(risk.riskPerUnit())
                .tp1(risk.tp1())
                .tp2(risk.tp2())
                .tp1Hit(false)
                .tp2Hit(false)
                .trailingActive(false)
                .remainingPositionPct(new BigDecimal("100"))
                .highestPriceSinceEntry(entryPrice)
                .lowestPriceSinceEntry(entryPrice)
                .barsInPosition(0)
                .entryPriceAdjusted(adjustedEntry(execution.executionSide(), entryPrice, costConfig))
                .totalFeePct(costConfig.getTakerFeePct().multiply(BigDecimal.valueOf(200)))
                .totalSlippagePct(costConfig.getSlippagePct().multiply(BigDecimal.valueOf(200)))
                .maxFavorableMovePct(BigDecimal.ZERO)
                .maxAdverseMovePct(BigDecimal.ZERO)
                .barsHeld(0)
                .minutesHeld(0)
                .lastCheckedAt(openedAt)
                .prev3_5mReturn(prev3_5mReturn)
                .earlyExitTriggered(false)
                .earlyExitRuleA(false)
                .earlyExitRuleB(false)
                .takeProfitPct(bigDecimalValue(exitConfig.getTakeProfitPct(), "1.0"))
                .stopLossPct(bigDecimalValue(exitConfig.getStopLossPct(), "0.6"))
                .timeStopMinutes(intValue(exitConfig.getTimeStopMinutes(), 240))
                .build();

        PaperPositionEntity saved = paperPositionRepository.save(position);
        writeOpenedEvent(saved);
        writeSymbolTradeEntry(saved, signal);
        markCandidateUsed(saved.getSymbol());
        log.info(
                "PAPER_POSITION_OPENED openedAt={} symbol={} signalSide={} executionSide={} signalInverted={} mode={} entry={} stop={} tp1={} tp2={} id={} quantity={} notionalUsdt={} leverage={}",
                IstanbulTimeUtil.format(saved.getOpenedAt()),
                saved.getSymbol(),
                saved.getSourceSignalSide(),
                effectiveExecutionSide(saved),
                saved.getSignalInverted(),
                SHORT_SIGNAL_INVERSION_MODE,
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


    private SignalExecution resolveSignalExecution(EntrySignal signal) {
        if (signal.getAction() == EntryAction.ENTER_SHORT) {
            return new SignalExecution(PositionSide.SHORT, PositionSide.LONG, true, SHORT_SIGNAL_INVERTED_TO_LONG);
        }
        return new SignalExecution(PositionSide.LONG, PositionSide.LONG, false, null);
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private PositionSide effectiveExecutionSide(PaperPositionEntity position) {
        return position.getExecutionSide() == null ? position.getSide() : position.getExecutionSide();
    }

    public RiskLevels calculateRiskLevels(PositionSide side, BigDecimal entryPrice, BigDecimal atr14, ScannerProperties.PaperRisk config) {
        BigDecimal raw = atr14.multiply(config.getAtrStopMultiplier());
        BigDecimal min = entryPrice.multiply(config.getMinStopDistancePct());
        BigDecimal max = entryPrice.multiply(config.getMaxStopDistancePct());
        BigDecimal distance = raw.max(min).min(max);
        ScannerProperties.Strategy.Entry entry = scannerProperties.getStrategy().getEntry();
        BigDecimal slPctFinal = distance.divide(entryPrice, 12, RoundingMode.HALF_UP).min(entry.getSlMaxPct());
        BigDecimal tp1PctFinal = slPctFinal.multiply(config.getTp1RMultiple()).min(entry.getTp1MaxPct());
        BigDecimal tp2PctFinal = slPctFinal.multiply(config.getTp2RMultiple()).min(entry.getTp2MaxPct());
        BigDecimal initialStop = entryPrice.multiply(BigDecimal.ONE.subtract(slPctFinal));
        BigDecimal riskPerUnit = entryPrice.subtract(initialStop);
        BigDecimal tp1 = entryPrice.multiply(BigDecimal.ONE.add(tp1PctFinal));
        BigDecimal tp2 = entryPrice.multiply(BigDecimal.ONE.add(tp2PctFinal));
        return new RiskLevels(initialStop, riskPerUnit, tp1, tp2);
    }

    private EntryFilterResult evaluateEntryFilters(EntrySignal signal, BigDecimal entryPrice) {
        ScannerProperties.Strategy.Entry cfg = scannerProperties.getStrategy().getEntry();
        BigDecimal bbWidth = signal.getBbWidth();
        if (bbWidth == null || !isFinite(bbWidth) || bbWidth.compareTo(cfg.getBbWidthMax()) >= 0) {
            return new EntryFilterResult(false, null, false, false, "BB_WIDTH_FILTER_FAILED");
        }
        BigDecimal low = signal.getPrevious1hLow();
        BigDecimal high = signal.getPrevious1hHigh();
        if (low == null || high == null || high.compareTo(low) == 0) {
            return new EntryFilterResult(false, null, false, true, "RANGE_POS1H_DATA_NOT_READY");
        }
        BigDecimal rangePos = entryPrice.subtract(low).divide(high.subtract(low), 12, RoundingMode.HALF_UP);
        boolean rangePassed = rangePos.compareTo(cfg.getRangePos1hMinExclusive()) > 0
                && rangePos.compareTo(cfg.getRangePos1hMaxInclusive()) <= 0;
        if (!rangePassed) {
            return new EntryFilterResult(false, rangePos, false, true, "RANGE_POS1H_FILTER_FAILED");
        }
        return new EntryFilterResult(true, rangePos, true, true, null);
    }

    private boolean isFinite(BigDecimal value) {
        return value != null;
    }

    public record RiskLevels(BigDecimal initialStop, BigDecimal riskPerUnit, BigDecimal tp1, BigDecimal tp2) {}

    private record EntryFilterResult(boolean passed, BigDecimal rangePos1h, boolean rangePos1hPassed, boolean bbWidthPassed, String rejectReason) {}

    private record SignalExecution(
            PositionSide signalSide,
            PositionSide executionSide,
            boolean signalInverted,
            String inversionReason
    ) {}

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


    private BigDecimal resolvePrev3_5mReturn(EntrySignal signal) {
        if (binanceFuturesClient == null || signal == null || signal.getSymbol() == null) {
            return null;
        }
        try {
            List<Kline> rawKlines = binanceFuturesClient.getKlines(signal.getSymbol(), "5m", 4);
            List<Kline> closed = (rawKlines == null ? List.<Kline>of() : rawKlines).stream()
                    .filter(kline -> kline != null && Boolean.TRUE.equals(kline.getClosed()))
                    .toList();
            if (closed.size() < 3) {
                return null;
            }
            List<Kline> prev3 = closed.subList(closed.size() - 3, closed.size());
            BigDecimal prev3Open = prev3.get(0).getOpen();
            BigDecimal prev3Close = prev3.get(2).getClose();
            if (prev3Open == null || prev3Close == null || prev3Open.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return prev3Close.divide(prev3Open, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        } catch (Exception exception) {
            log.warn("PAPER_ENTRY_PREV3_5M_RETURN_UNAVAILABLE symbol={} reason={}", signal.getSymbol(), exception.getMessage());
            return null;
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
        return entryPrice.multiply(BigDecimal.ONE.add(slip));
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
                Map.entry("signalSide", enumName(position.getSourceSignalSide() == null ? position.getSide() : position.getSourceSignalSide())),
                Map.entry("executionSide", enumName(effectiveExecutionSide(position))),
                Map.entry("signalInverted", Boolean.TRUE.equals(position.getSignalInverted())),
                Map.entry("inversionReason", position.getInversionReason() == null ? "" : position.getInversionReason()),
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
                Map.entry("bbWidthPassed", position.getEntryBbWidth() != null && position.getEntryBbWidth().compareTo(scannerProperties.getStrategy().getEntry().getBbWidthMax()) < 0),
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
        payload.put("signalSide", enumName(position.getSourceSignalSide() == null ? position.getSide() : position.getSourceSignalSide()));
        payload.put("executionSide", enumName(effectiveExecutionSide(position)));
        payload.put("signalInverted", Boolean.TRUE.equals(position.getSignalInverted()));
        payload.put("inversionReason", position.getInversionReason() == null ? "" : position.getInversionReason());
        payload.put("entryPrice", position.getEntryPrice());
        payload.put("qty", position.getQuantity());
        payload.put("tp1", position.getTp1());
        payload.put("tp2", position.getTp2());
        payload.put("slPrice", position.getInitialStop());
        putFinalRiskFields(payload, position);
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
        return payload;
    }

    private void putFinalRiskFields(Map<String, Object> payload, PaperPositionEntity position) {
        if (position.getEntryPrice() == null) return;
        BigDecimal entry = position.getEntryPrice();
        payload.put("tp1PctFinal", position.getTp1() == null ? null : position.getTp1().divide(entry, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
        payload.put("tp2PctFinal", position.getTp2() == null ? null : position.getTp2().divide(entry, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
        payload.put("slPctFinal", position.getInitialStop() == null ? null : BigDecimal.ONE.subtract(position.getInitialStop().divide(entry, 12, RoundingMode.HALF_UP)));
        payload.put("tp1PriceFinal", position.getTp1());
        payload.put("tp2PriceFinal", position.getTp2());
        payload.put("slPriceFinal", position.getInitialStop());
        payload.put("bbWidth", position.getEntryBbWidth());
        payload.put("bbWidthPassed", position.getEntryBbWidth() != null && position.getEntryBbWidth().compareTo(scannerProperties.getStrategy().getEntry().getBbWidthMax()) < 0);
    }

    private boolean hasRequiredEntryIndicators(EntrySignal signal) {
        return signal.getClose1h() != null
                && signal.getEma20_1h() != null
                && signal.getRsi14_1h() != null
                && signal.getMacdHist_1h() != null
                && signal.getAtr14_1h() != null
                && signal.getVolumeRatio_1h() != null;
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
