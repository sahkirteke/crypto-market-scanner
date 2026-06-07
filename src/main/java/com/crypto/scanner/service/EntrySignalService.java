package com.crypto.scanner.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.EntrySignal;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntrySignalService {
    private final ScannerProperties scannerProperties;
    private final EntryCandidateService entryCandidateService;

    public List<EntrySignal> generateSignals(List<EntryCandidate> candidates) {
        List<EntrySignal> signals = nullSafeCandidates(candidates).stream()
                .map(this::generateSignal)
                .sorted(signalComparator())
                .toList();

        long enterLongCount = signals.stream().filter(signal -> signal.getAction() == EntryAction.ENTER_LONG).count();
        long enterShortCount = signals.stream().filter(signal -> signal.getAction() == EntryAction.ENTER_SHORT).count();
        long noEntryCount = signals.stream().filter(signal -> signal.getAction() == EntryAction.NO_ENTRY).count();
        log.info(
                "ENTRY_SIGNALS_READY total={} enterLong={} enterShort={} noEntry={}",
                signals.size(),
                enterLongCount,
                enterShortCount,
                noEntryCount
        );
        return signals;
    }

    public List<EntrySignal> generateSignalsFromLatestScan() {
        List<EntryCandidate> candidates = entryCandidateService.selectCandidatesFromLatestScan();
        return generateSignals(candidates);
    }

    public List<EntrySignal> generateSignalsFromScanRun(Long scanRunId) {
        List<EntryCandidate> candidates = entryCandidateService.selectCandidatesFromScanRun(scanRunId);
        return generateSignals(candidates);
    }

    public EntrySignal generateSignal(EntryCandidate candidate) {
        EntrySignal signal = baseSignal(candidate);
        if (candidate == null) {
            return blocked(signal, "CANDIDATE_NULL");
        }

        ScannerProperties.EntrySignal config = scannerProperties.getEntrySignal();
        if (!booleanValue(config.getEnabled(), true)) {
            return blocked(signal, "ENTRY_SIGNAL_DISABLED");
        }
        if (candidate.getSide() == null) {
            return blocked(signal, "SIDE_MISSING");
        }
        if (score(candidate) < intValue(config.getMinEnterScore(), 75)) {
            return blocked(signal, "ENTRY_SCORE_TOO_LOW");
        }
        if (isStrong(candidate.getSourceClassification())
                && score(candidate) < intValue(config.getMinStrongEnterScore(), 80)) {
            return blocked(signal, "STRONG_SCORE_TOO_LOW");
        }
        if (candidate.getSourceClassification() == CoinClassification.WATCHLIST
                && !booleanValue(config.getAllowWatchlistEntry(), true)) {
            return blocked(signal, "WATCHLIST_ENTRY_DISABLED");
        }
        if (candidate.getRiskLevel() == RiskLevel.HIGH && !booleanValue(config.getAllowHighRiskEntry(), false)) {
            return blocked(signal, "HIGH_RISK_BLOCKED");
        }
        if (candidate.getRiskLevel() == RiskLevel.MEDIUM && !booleanValue(config.getAllowMediumRiskEntry(), true)) {
            return blocked(signal, "MEDIUM_RISK_BLOCKED");
        }
        if (greaterThan(candidate.getSpreadPct(), bigDecimalValue(config.getMaxSpreadPct(), "0.08"))) {
            return blocked(signal, "SPREAD_TOO_HIGH");
        }
        if (lessThan(candidate.getQuoteVolume24h(), bigDecimalValue(config.getMinQuoteVolume24h(), "30000000"))) {
            return blocked(signal, "VOLUME_TOO_LOW");
        }
        if (booleanValue(config.getBlockMarketChop(), false) && hasTag(candidate, ReasonTag.MARKET_CHOP)) {
            return blocked(signal, "MARKET_CHOP_BLOCKED");
        }
        if (booleanValue(config.getRequireVolumeConfirmed(), false)
                && !nullSafe(candidate.getReasons()).contains(ReasonTag.VOLUME_CONFIRMED)) {
            return blocked(signal, "VOLUME_NOT_CONFIRMED");
        }

        if (candidate.getSide() == PositionSide.LONG) {
            if (greaterThan(candidate.getPriceChange24hPct(), bigDecimalValue(config.getMaxLong24hChangePct(), "18"))) {
                return blocked(signal, "LONG_TOO_PUMPED");
            }
            if (hasWarning(candidate, ReasonTag.RSI_OVERBOUGHT)) {
                return blocked(signal, "RSI_OVERBOUGHT_LONG_BLOCKED");
            }
            if (hasWarning(candidate, ReasonTag.LONG_CROWDED)) {
                return blocked(signal, "LONG_CROWDED_BLOCKED");
            }
            signal.setAction(EntryAction.ENTER_LONG);
            signal.setSignalReason(resolveSignalReason(candidate));
            return ready(signal);
        }

        if (candidate.getSide() == PositionSide.SHORT) {
            if (lessThan(candidate.getPriceChange24hPct(), bigDecimalValue(config.getMaxShort24hDumpPct(), "-18"))) {
                return blocked(signal, "SHORT_TOO_DUMPED");
            }
            if (hasWarning(candidate, ReasonTag.SHORT_EXTREME_OVERSOLD_RISK)) {
                return blocked(signal, "EXTREME_OVERSOLD_SHORT_BLOCKED");
            }
            if (hasWarning(candidate, ReasonTag.SHORT_CROWDED)) {
                return blocked(signal, "SHORT_CROWDED_BLOCKED");
            }
            signal.setAction(EntryAction.ENTER_SHORT);
            signal.setSignalReason(resolveSignalReason(candidate));
            return ready(signal);
        }

        return blocked(signal, "SIDE_MISSING");
    }

    private EntrySignal baseSignal(EntryCandidate candidate) {
        if (candidate == null) {
            return EntrySignal.builder()
                    .action(EntryAction.NO_ENTRY)
                    .signalTime(Instant.now())
                    .build();
        }
        return EntrySignal.builder()
                .symbol(candidate.getSymbol())
                .side(candidate.getSide())
                .action(EntryAction.NO_ENTRY)
                .score(candidate.getScore())
                .longScore(candidate.getLongScore())
                .shortScore(candidate.getShortScore())
                .sourceClassification(candidate.getSourceClassification())
                .directionBias(candidate.getDirectionBias())
                .riskLevel(candidate.getRiskLevel())
                .entryPrice(candidate.getLastPrice())
                .lastPrice(candidate.getLastPrice())
                .spreadPct(candidate.getSpreadPct())
                .priceChange24hPct(candidate.getPriceChange24hPct())
                .quoteVolume24h(candidate.getQuoteVolume24h())
                .fundingRate(candidate.getFundingRate())
                .openInterest(candidate.getOpenInterest())
                .marketBreadthPct(candidate.getMarketBreadthPct())
                .reasons(nullSafe(candidate.getReasons()))
                .warnings(nullSafe(candidate.getWarnings()))
                .entryPriorityScore(candidate.getEntryPriorityScore())
                .scannerScore(candidate.getScore())
                .marketRegime(candidate.getMarketRegime())
                .signalTime(Instant.now())
                .build();
    }

    private EntrySignal ready(EntrySignal signal) {
        log.info(
                "ENTRY_SIGNAL_READY symbol={} side={} action={} score={} reason={} blockReason={}",
                signal.getSymbol(),
                signal.getSide(),
                signal.getAction(),
                signal.getScore(),
                signal.getSignalReason(),
                signal.getBlockReason()
        );
        return signal;
    }

    private EntrySignal blocked(EntrySignal signal, String blockReason) {
        signal.setAction(EntryAction.NO_ENTRY);
        signal.setBlockReason(blockReason);
        log.info(
                "ENTRY_SIGNAL_BLOCKED symbol={} side={} score={} blockReason={}",
                signal.getSymbol(),
                signal.getSide(),
                signal.getScore(),
                blockReason
        );
        return signal;
    }

    private String resolveSignalReason(EntryCandidate candidate) {
        if (candidate.getSourceClassification() == CoinClassification.STRONG_LONG) {
            return "STRONG_LONG_ENTRY";
        }
        if (candidate.getSourceClassification() == CoinClassification.STRONG_SHORT) {
            return "STRONG_SHORT_ENTRY";
        }
        if (candidate.getSide() == PositionSide.LONG) {
            return "WATCHLIST_LONG_ENTRY";
        }
        return "WATCHLIST_SHORT_ENTRY";
    }

    private Comparator<EntrySignal> signalComparator() {
        return Comparator.comparingInt((EntrySignal signal) -> actionRank(signal.getAction()))
                .thenComparing(EntrySignal::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(signal -> riskRank(signal.getRiskLevel()));
    }

    private int actionRank(EntryAction action) {
        return action == EntryAction.NO_ENTRY ? 1 : 0;
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

    private boolean isStrong(CoinClassification classification) {
        return classification == CoinClassification.STRONG_LONG || classification == CoinClassification.STRONG_SHORT;
    }

    private boolean hasTag(EntryCandidate candidate, ReasonTag tag) {
        return nullSafe(candidate.getReasons()).contains(tag) || nullSafe(candidate.getWarnings()).contains(tag);
    }

    private boolean hasWarning(EntryCandidate candidate, ReasonTag tag) {
        return nullSafe(candidate.getWarnings()).contains(tag);
    }

    private boolean greaterThan(BigDecimal value, BigDecimal threshold) {
        return value != null && threshold != null && value.compareTo(threshold) > 0;
    }

    private boolean lessThan(BigDecimal value, BigDecimal threshold) {
        return value != null && threshold != null && value.compareTo(threshold) < 0;
    }

    private int score(EntryCandidate candidate) {
        return intValue(candidate.getScore(), 0);
    }

    private int intValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private BigDecimal bigDecimalValue(BigDecimal value, String defaultValue) {
        return value == null ? new BigDecimal(defaultValue) : value;
    }

    private boolean booleanValue(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private List<EntryCandidate> nullSafeCandidates(List<EntryCandidate> values) {
        return values == null ? List.of() : values;
    }

    private List<ReasonTag> nullSafe(List<ReasonTag> values) {
        return values == null ? List.of() : values;
    }
}
