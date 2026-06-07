package com.crypto.paper.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public List<PaperPositionEntity> openPositionsFromLatestSignals() {
        List<EntrySignal> latestSignals = entrySignalService.generateSignalsFromLatestScan();
        List<EntrySignal> signals = (latestSignals == null ? List.<EntrySignal>of() : latestSignals).stream()
                .filter(this::isStrongSignal)
                .toList();
        return openPositions(signals);
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

        ScannerProperties.PaperExit exitConfig = scannerProperties.getPaperExit() == null
                ? new ScannerProperties.PaperExit()
                : scannerProperties.getPaperExit();
        BigDecimal notionalUsdt = bigDecimalValue(config.getDefaultNotionalUsdt(), "100");
        BigDecimal quantity = notionalUsdt.divide(signal.getEntryPrice(), QUANTITY_SCALE, RoundingMode.DOWN);
        Instant openedAt = Instant.now();
        PaperPositionEntity position = PaperPositionEntity.builder()
                .symbol(signal.getSymbol())
                .side(signal.getSide())
                .status(PaperPositionStatus.OPEN)
                .entryAction(signal.getAction())
                .entryPrice(signal.getEntryPrice())
                .quantity(quantity)
                .notionalUsdt(notionalUsdt)
                .leverage(intValue(config.getLeverage(), 3))
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
                .currentPrice(signal.getEntryPrice())
                .highestPrice(signal.getEntryPrice())
                .lowestPrice(signal.getEntryPrice())
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
        log.info(
                "PAPER_POSITION_OPENED id={} symbol={} side={} entryPrice={} quantity={} notionalUsdt={} leverage={}",
                saved.getId(),
                saved.getSymbol(),
                saved.getSide(),
                saved.getEntryPrice(),
                saved.getQuantity(),
                saved.getNotionalUsdt(),
                saved.getLeverage()
        );
        return saved;
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
