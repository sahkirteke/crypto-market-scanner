package com.crypto.scanner.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.EntryCandidateEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.EntryCandidateRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.EntryCandidateStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryCandidateService {
    private static final String COMPLETED_STATUS = "COMPLETED";

    private final ScannerProperties scannerProperties;
    private final MarketScanRunRepository marketScanRunRepository;
    private final CoinScanResultRepository coinScanResultRepository;
    private final JsonTextMapper jsonTextMapper;

    @Autowired(required = false)
    private EntryCandidateRepository entryCandidateRepository;
    @Autowired(required = false)
    private JsonlDecisionLogService jsonlDecisionLogService;

    public List<EntryCandidate> selectCandidates(MarketScanResult scanResult) {
        if (scanResult == null) {
            return finish(List.of());
        }

        List<CoinScanResult> scanResults = new ArrayList<>();
        scanResults.addAll(nullSafe(scanResult.getStrongLong()));
        scanResults.addAll(nullSafe(scanResult.getStrongShort()));
        // WATCHLIST is not entry eligible in V1.2.3.

        List<EntryCandidate> eligibleCandidates = scanResults.stream()
                .map(this::toEligibleCandidate)
                .flatMap(List::stream)
                .peek(candidate -> candidate.setSourceScanType(scanResult.getScanType()))
                .toList();

        return applyLimits(eligibleCandidates);
    }

    public List<EntryCandidate> selectCandidatesFromLatestScan() {
        return marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc(COMPLETED_STATUS)
                .map(MarketScanRunEntity::getId)
                .map(this::selectCandidatesFromScanRun)
                .orElseGet(() -> finish(List.of()));
    }

    public List<EntryCandidate> selectCandidatesFromScanRun(Long scanRunId) {
        if (scanRunId == null) {
            return finish(List.of());
        }

        MarketScanRunEntity scanRun = marketScanRunRepository.findById(scanRunId).orElse(null);
        if (scanRun == null) {
            log.warn("ENTRY_CANDIDATES_SCAN_RUN_NOT_FOUND scanRunId={}", scanRunId);
            return finish(List.of());
        }

        MarketRegime marketRegime = scanRun.getMarketRegime();
        BigDecimal marketBreadthPct = scanRun.getMarketBreadthPct();
        Instant scanTime = scanRun.getScanTimeUtc();

        List<CoinScanResultEntity> entities = coinScanResultRepository.findByScanRunIdWithScanRun(scanRunId);

        List<EntryCandidate> eligibleCandidates = entities.stream()
                .filter(entity -> entity.getClassification() == CoinClassification.STRONG_LONG
                        || entity.getClassification() == CoinClassification.STRONG_SHORT)
                .map(entity -> toCoinScanResult(entity, scanRunId, marketRegime, marketBreadthPct, scanTime))
                .map(this::toEligibleCandidate)
                .flatMap(List::stream)
                .peek(candidate -> candidate.setSourceScanType(scanRun.getScanType()))
                .toList();

        return applyLimits(eligibleCandidates);
    }

    private List<EntryCandidate> toEligibleCandidate(CoinScanResult result) {
        if (result == null) {
            return List.of();
        }

        ScannerProperties.EntryCandidate config = scannerProperties.getEntryCandidate();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            logRejected(result, "ENTRY_CANDIDATE_DISABLED");
            return List.of();
        }

        if (!isV20Enabled() && score(result) < valueOrDefault(config.getMinScore(), 70)) {
            logRejected(result, "SCORE_TOO_LOW");
            return List.of();
        }

        PositionSide side = resolveSide(result, config);
        if (side == null) {
            return List.of();
        }

        if (result.getRiskLevel() == RiskLevel.HIGH && !Boolean.TRUE.equals(config.getAllowHighRisk())) {
            logRejected(result, "HIGH_RISK_BLOCKED");
            return List.of();
        }

        if (hasTag(result, ReasonTag.MARKET_PANIC)) {
            logRejected(result, "MARKET_PANIC_BLOCKED");
            return List.of();
        }

        if (side == PositionSide.LONG && hasTag(result, ReasonTag.LONG_CROWDED)) {
            logRejected(result, "LONG_CROWDED_BLOCKED");
            return List.of();
        }

        if (side == PositionSide.SHORT && hasTag(result, ReasonTag.SHORT_CROWDED)) {
            logRejected(result, "SHORT_CROWDED_BLOCKED");
            return List.of();
        }

        if (side == PositionSide.LONG && hasTag(result, ReasonTag.RSI_OVERBOUGHT)) {
            logRejected(result, "RSI_OVERBOUGHT_LONG_BLOCKED");
            return List.of();
        }

        if (side == PositionSide.SHORT && hasTag(result, ReasonTag.SHORT_EXTREME_OVERSOLD_RISK)) {
            logRejected(result, "EXTREME_OVERSOLD_SHORT_BLOCKED");
            return List.of();
        }

        EntryCandidate candidate = toEntryCandidate(result, side);
        return List.of(candidate);
    }

    private PositionSide resolveSide(CoinScanResult result, ScannerProperties.EntryCandidate config) {
        CoinClassification classification = result.getClassification();
        DirectionBias directionBias = result.getDirectionBias();

        if (classification == CoinClassification.STRONG_LONG) {
            if (directionBias == DirectionBias.LONG) {
                return PositionSide.LONG;
            }
            logRejected(result, directionBias == DirectionBias.NEUTRAL ? "NEUTRAL_DIRECTION" : "CLASSIFICATION_NOT_ALLOWED");
            return null;
        }

        if (classification == CoinClassification.STRONG_SHORT) {
            if (directionBias == DirectionBias.SHORT) {
                return PositionSide.SHORT;
            }
            logRejected(result, directionBias == DirectionBias.NEUTRAL ? "NEUTRAL_DIRECTION" : "CLASSIFICATION_NOT_ALLOWED");
            return null;
        }

        if (classification == CoinClassification.WATCHLIST) {
            logRejected(result, "WATCHLIST_NOT_ENTRY_ELIGIBLE");
            return null;
        }

        logRejected(result, "CLASSIFICATION_NOT_ALLOWED");
        return null;
    }

    private List<EntryCandidate> applyLimits(List<EntryCandidate> candidates) {
        ScannerProperties.EntryCandidate config = scannerProperties.getEntryCandidate();
        List<EntryCandidate> sortedCandidates = candidates.stream()
                .sorted(candidateComparator())
                .toList();

        if (isV20Enabled()) {
            sortedCandidates.forEach(candidate -> {
                persistCandidate(candidate);
                log.info("ENTRY_CANDIDATE_SELECTED symbol={} side={} score={} reason={}",
                        candidate.getSymbol(), candidate.getSide(), candidate.getScore(), candidate.getCandidateReason());
            });
            return finish(sortedCandidates);
        }

        int maxLongCandidates = valueOrDefault(config.getMaxLongCandidates(), 5);
        int maxShortCandidates = valueOrDefault(config.getMaxShortCandidates(), 5);
        int maxCandidates = valueOrDefault(config.getMaxCandidates(), 10);
        List<EntryCandidate> sideLimitedCandidates = new ArrayList<>();
        int longCount = 0;
        int shortCount = 0;

        for (EntryCandidate candidate : sortedCandidates) {
            if (candidate.getSide() == PositionSide.LONG) {
                if (longCount >= maxLongCandidates) {
                    logRejected(candidate, "SIDE_LIMIT_REACHED");
                    continue;
                }
                longCount++;
            } else if (candidate.getSide() == PositionSide.SHORT) {
                if (shortCount >= maxShortCandidates) {
                    logRejected(candidate, "SIDE_LIMIT_REACHED");
                    continue;
                }
                shortCount++;
            }
            sideLimitedCandidates.add(candidate);
        }

        List<EntryCandidate> selectedCandidates = new ArrayList<>();
        for (EntryCandidate candidate : sideLimitedCandidates) {
            if (selectedCandidates.size() >= maxCandidates) {
                logRejected(candidate, "TOTAL_LIMIT_REACHED");
                continue;
            }
            selectedCandidates.add(candidate);
            persistCandidate(candidate);
            log.info(
                    "ENTRY_CANDIDATE_SELECTED symbol={} side={} score={} reason={}",
                    candidate.getSymbol(),
                    candidate.getSide(),
                    candidate.getScore(),
                    candidate.getCandidateReason()
            );
        }

        return finish(selectedCandidates);
    }

    private Comparator<EntryCandidate> candidateComparator() {
        return Comparator.comparing(EntryCandidate::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> riskRank(candidate.getRiskLevel()))
                .thenComparing(EntryCandidate::getQuoteVolume24h, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private EntryCandidate toEntryCandidate(CoinScanResult result, PositionSide side) {
        return EntryCandidate.builder()
                .scanRunId(result.getScanRunId())
                .symbol(result.getSymbol())
                .side(side)
                .score(result.getScore())
                .longScore(result.getLongScore())
                .shortScore(result.getShortScore())
                .sourceClassification(result.getClassification())
                .directionBias(result.getDirectionBias())
                .riskLevel(result.getRiskLevel())
                .lastPrice(result.getLastPrice())
                .priceChange24hPct(result.getPriceChange24hPct())
                .quoteVolume24h(result.getQuoteVolume24h())
                .spreadPct(result.getSpreadPct())
                .fundingRate(result.getFundingRate())
                .openInterest(result.getOpenInterest())
                .marketBreadthPct(result.getMarketBreadthPct())
                .marketRegime(result.getMarketRegime())
                .reasons(nullSafe(result.getReasons()))
                .warnings(nullSafe(result.getWarnings()))
                .candidateReason(candidateReason(result, side))
                .createdAt(Instant.now())
                .validFromUtc(result.getScanTime() == null ? Instant.now() : result.getScanTime())
                .validUntilUtc((result.getScanTime() == null ? Instant.now() : result.getScanTime()).plusSeconds(3600))
                .build();
    }


    private void persistCandidate(EntryCandidate candidate) {
        if (entryCandidateRepository == null || candidate == null || candidate.getScanRunId() == null) {
            return;
        }
        MarketScanRunEntity run = marketScanRunRepository.findById(candidate.getScanRunId()).orElse(null);
        if (run == null) {
            return;
        }
        Instant validFrom = run.getScanTimeUtc() == null ? Instant.now() : run.getScanTimeUtc();
        Instant validUntil = validFrom.plusSeconds(run.getScanType() == com.crypto.common.enums.ScanType.FOUR_HOUR ? 4 * 3600L : 3600L);
        candidate.setValidFromUtc(validFrom);
        candidate.setValidUntilUtc(validUntil);
        EntryCandidateEntity entity = EntryCandidateEntity.builder()
                .scanRun(run)
                .symbol(candidate.getSymbol())
                .side(candidate.getSide())
                .score(candidate.getScore())
                .entryPriorityScore(candidate.getEntryPriorityScore())
                .riskLevel(candidate.getRiskLevel())
                .marketRegime(candidate.getMarketRegime())
                .marketBreadthPct(candidate.getMarketBreadthPct())
                .validFromUtc(validFrom)
                .validUntilUtc(validUntil)
                .reasonsJson(jsonTextMapper.toJson(candidate.getReasons()))
                .warningsJson(jsonTextMapper.toJson(candidate.getWarnings()))
                .status(EntryCandidateStatus.ACTIVE)
                .build();
        EntryCandidateEntity saved = entryCandidateRepository.save(entity);
        candidate.setId(saved.getId());
        if (jsonlDecisionLogService != null) {
            jsonlDecisionLogService.logEntry(Map.of("event", "ENTRY_CANDIDATE_CREATED", "symbol", candidate.getSymbol(), "side", candidate.getSide().name(), "scanRunId", candidate.getScanRunId(), "score", candidate.getScore() == null ? 0 : candidate.getScore(), "reason", candidate.getCandidateReason()));
        }
        log.info("ENTRY_CANDIDATE_CREATED symbol={} side={} scanRunId={} validUntil={}", candidate.getSymbol(), candidate.getSide(), candidate.getScanRunId(), validUntil);
    }

    private CoinScanResult toCoinScanResult(
            CoinScanResultEntity entity,
            Long scanRunId,
            MarketRegime marketRegime,
            BigDecimal marketBreadthPct,
            Instant scanTime
    ) {
        return CoinScanResult.builder()
                .scanRunId(scanRunId)
                .symbol(entity.getSymbol())
                .directionBias(entity.getDirectionBias())
                .classification(entity.getClassification())
                .score(entity.getScore())
                .longScore(entity.getLongScore())
                .shortScore(entity.getShortScore())
                .riskLevel(entity.getRiskLevel())
                .lastPrice(entity.getLastPrice())
                .priceChange24hPct(entity.getPriceChange24hPct())
                .quoteVolume24h(entity.getQuoteVolume24h())
                .spreadPct(entity.getSpreadPct())
                .fundingRate(entity.getFundingRate())
                .openInterest(entity.getOpenInterest())
                .marketBreadthPct(entity.getMarketBreadthPct() == null ? marketBreadthPct : entity.getMarketBreadthPct())
                .marketRegime(marketRegime)
                .eliminatedReason(entity.getEliminatedReason())
                .reasons(parseReasonTags(entity.getReasonsJson(), entity.getSymbol(), "reasons"))
                .warnings(parseReasonTags(entity.getWarningsJson(), entity.getSymbol(), "warnings"))
                .scanTime(scanTime == null ? entity.getCreatedAt() : scanTime)
                .build();
    }

    private List<ReasonTag> parseReasonTags(String json, String symbol, String fieldName) {
        return jsonTextMapper.toStringList(json).stream()
                .map(tag -> parseReasonTag(tag, symbol, fieldName))
                .flatMap(List::stream)
                .toList();
    }

    private List<ReasonTag> parseReasonTag(String tag, String symbol, String fieldName) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        try {
            return List.of(ReasonTag.valueOf(tag.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            log.warn("ENTRY_CANDIDATE_REASON_TAG_PARSE_SKIPPED symbol={} field={} tag={}", symbol, fieldName, tag);
            return List.of();
        }
    }

    private String candidateReason(CoinScanResult result, PositionSide side) {
        if (result.getClassification() == CoinClassification.STRONG_LONG) {
            return "STRONG_LONG_CANDIDATE";
        }
        if (result.getClassification() == CoinClassification.STRONG_SHORT) {
            return "STRONG_SHORT_CANDIDATE";
        }
        return side == PositionSide.LONG ? "STRONG_LONG_CANDIDATE" : "STRONG_SHORT_CANDIDATE";
    }

    private boolean hasTag(CoinScanResult result, ReasonTag tag) {
        return nullSafe(result.getReasons()).contains(tag) || nullSafe(result.getWarnings()).contains(tag);
    }

    private void logRejected(CoinScanResult result, String reason) {
        log.info(
                "ENTRY_CANDIDATE_REJECTED symbol={} reason={} classification={} directionBias={} score={}",
                result.getSymbol(),
                reason,
                result.getClassification(),
                result.getDirectionBias(),
                result.getScore()
        );
    }

    private boolean isV20Enabled() {
        return scannerProperties.getV20() != null && Boolean.TRUE.equals(scannerProperties.getV20().getEnabled());
    }

    private void logRejected(EntryCandidate candidate, String reason) {
        log.info(
                "ENTRY_CANDIDATE_REJECTED symbol={} reason={} classification={} directionBias={} score={}",
                candidate.getSymbol(),
                reason,
                candidate.getSourceClassification(),
                candidate.getDirectionBias(),
                candidate.getScore()
        );
    }

    private List<EntryCandidate> finish(List<EntryCandidate> candidates) {
        long longCount = candidates.stream().filter(candidate -> candidate.getSide() == PositionSide.LONG).count();
        long shortCount = candidates.stream().filter(candidate -> candidate.getSide() == PositionSide.SHORT).count();
        log.info("ENTRY_CANDIDATES_READY total={} long={} short={}", candidates.size(), longCount, shortCount);
        return candidates;
    }

    private int score(CoinScanResult result) {
        return valueOrDefault(result.getScore(), 0);
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int riskRank(RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.LOW) {
            return 0;
        }
        if (riskLevel == RiskLevel.MEDIUM) {
            return 1;
        }
        if (riskLevel == RiskLevel.HIGH) {
            return 2;
        }
        return 3;
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
