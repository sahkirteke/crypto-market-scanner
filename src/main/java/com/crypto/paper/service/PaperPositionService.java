package com.crypto.paper.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.common.time.IstanbulTimeUtil;
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

    @Autowired(required = false)
    private BinanceFuturesClient binanceFuturesClient;
    @Autowired(required = false)
    private PaperPositionEventRepository eventRepository;
    @Autowired(required = false)
    private EntryCandidateRepository entryCandidateRepository;
    @Autowired(required = false)
    private JsonlDecisionLogService jsonlDecisionLogService;

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
        if (openPositions.size() >= intValue(config.getMaxOpenPositions(), 5)) {
            return reject(signal, "MAX_OPEN_POSITIONS_REACHED");
        }
        long openSideCount = openPositions.stream()
                .filter(position -> position.getSide() == signal.getSide())
                .count();
        if (signal.getSide() == PositionSide.LONG
                && openSideCount >= intValue(config.getMaxOpenLongPositions(), 3)) {
            return reject(signal, "MAX_OPEN_LONG_REACHED");
        }
        if (signal.getSide() == PositionSide.SHORT
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
        RiskLevels risk = calculateRiskLevels(signal.getSide(), entryPrice, atr, riskConfig);
        BigDecimal notionalUsdt = bigDecimalValue(config.getDefaultNotionalUsdt(), "100");
        BigDecimal quantity = notionalUsdt.divide(entryPrice, QUANTITY_SCALE, RoundingMode.DOWN);
        Instant openedAt = Instant.now();
        PaperPositionEntity position = PaperPositionEntity.builder()
                .symbol(signal.getSymbol())
                .side(signal.getSide())
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
                .entrySignalScore(signal.getScore())
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
                .entryPriceAdjusted(adjustedEntry(signal.getSide(), entryPrice, costConfig))
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

        PaperPositionEntity saved = paperPositionRepository.save(position);
        writeOpenedEvent(saved);
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
                    .build());
        }
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logPaper(Map.of("event", "PAPER_POSITION_OPENED", "time", position.getOpenedAt(), "symbol", position.getSymbol(), "side", position.getSide().name(), "positionId", position.getId() == null ? "" : position.getId(), "entryPrice", position.getEntryPrice()));
        }
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
