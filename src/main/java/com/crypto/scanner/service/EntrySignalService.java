package com.crypto.scanner.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.EntrySignal;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.BollingerScoreResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntrySignalService {
    private final ScannerProperties scannerProperties;
    private final EntryCandidateService entryCandidateService;
    private final BollingerScoreService bollingerScoreService;

    @Autowired(required = false)
    private BinanceFuturesClient binanceFuturesClient;
    @Autowired(required = false)
    private IndicatorService indicatorService;
    @Autowired(required = false)
    private PaperPositionRepository paperPositionRepository;
    @Autowired(required = false)
    private JsonlDecisionLogService jsonlDecisionLogService;

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
        applyTechnicalSnapshot(signal);
        applyBollingerScore(signal);
        if (entryScoreValue(signal) < intValue(config.getMinEnterScore(), 75)) {
            return blocked(signal, "ENTRY_SCORE_TOO_LOW");
        }
        if (isStrong(candidate.getSourceClassification())
                && entryScoreValue(signal) < intValue(config.getMinStrongEnterScore(), 80)) {
            return blocked(signal, "STRONG_SCORE_TOO_LOW");
        }
        if (candidate.getSourceClassification() == CoinClassification.WATCHLIST) {
            return blocked(signal, "WATCHLIST_NOT_ENTRY_ELIGIBLE");
        }
        if (candidate.getRiskLevel() == RiskLevel.HIGH && !booleanValue(config.getAllowHighRiskEntry(), false)) {
            return blocked(signal, "HIGH_RISK_BLOCKED");
        }
        if (candidate.getRiskLevel() == RiskLevel.MEDIUM && !booleanValue(config.getAllowMediumRiskEntry(), true)) {
            return blocked(signal, "MEDIUM_RISK_BLOCKED");
        }
        if (candidate.getValidUntilUtc() != null && !candidate.getValidUntilUtc().isAfter(Instant.now())) {
            return blocked(signal, "CANDIDATE_EXPIRED");
        }
        if (candidate.getMarketRegime() == MarketRegime.PANIC) {
            return blocked(signal, "MARKET_PANIC_BLOCKED");
        }
        if (paperPositionRepository != null && paperPositionRepository.existsBySymbolAndStatusIn(candidate.getSymbol(), List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED))) {
            return blocked(signal, "SYMBOL_ALREADY_OPEN");
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
            String triggerBlock = validateLongTrigger(signal);
            if (triggerBlock != null) {
                return blocked(signal, triggerBlock);
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
                signal.getWarnings().add(ReasonTag.SHORT_EXTREME_OVERSOLD_RISK);
            }
            if (hasWarning(candidate, ReasonTag.SHORT_CROWDED)) {
                return blocked(signal, "SHORT_CROWDED_BLOCKED");
            }
            String triggerBlock = validateShortTrigger(signal);
            if (triggerBlock != null) {
                return blocked(signal, triggerBlock);
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
                .baseEntryScore(BigDecimal.valueOf(score(candidate)))
                .bbScore(BigDecimal.ZERO)
                .finalEntryScore(BigDecimal.valueOf(score(candidate)))
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
                .reasons(new ArrayList<>(nullSafe(candidate.getReasons())))
                .warnings(new ArrayList<>(nullSafe(candidate.getWarnings())))
                .entryPriorityScore(candidate.getEntryPriorityScore())
                .scannerScore(candidate.getScore())
                .marketRegime(candidate.getMarketRegime())
                .entryTrigger(candidate.getCandidateReason())
                .signalTime(Instant.now())
                .build();
    }

    private EntrySignal ready(EntrySignal signal) {
        log.info(
                "ENTRY_SIGNAL_EVALUATED symbol={} side={} action={} trigger={} blockReason={} score={} baseEntryScore={} bbScore={} finalEntryScore={} bbPercentB={} bbReasons={} reason={}",
                signal.getSymbol(),
                signal.getSide(),
                signal.getAction(),
                signal.getEntryTrigger(),
                signal.getBlockReason(),
                signal.getScore(),
                signal.getBaseEntryScore(),
                signal.getBbScore(),
                signal.getFinalEntryScore(),
                signal.getBbPercentB(),
                signal.getBbReasons(),
                signal.getSignalReason()
        );
        writeSignalDecision(signal);
        return signal;
    }

    private EntrySignal blocked(EntrySignal signal, String blockReason) {
        signal.setAction(EntryAction.NO_ENTRY);
        signal.setBlockReason(blockReason);
        log.info(
                "ENTRY_SIGNAL_EVALUATED symbol={} side={} action={} trigger={} blockReason={} score={} baseEntryScore={} bbScore={} finalEntryScore={} bbPercentB={} bbReasons={}",
                signal.getSymbol(),
                signal.getSide(),
                signal.getAction(),
                signal.getEntryTrigger(),
                blockReason,
                signal.getScore(),
                signal.getBaseEntryScore(),
                signal.getBbScore(),
                signal.getFinalEntryScore(),
                signal.getBbPercentB(),
                signal.getBbReasons()
        );
        writeSignalDecision(signal);
        return signal;
    }

    private void writeSignalDecision(EntrySignal signal) {
        if (jsonlDecisionLogService != null && signal != null && signal.getSymbol() != null) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "ENTRY_SIGNAL_EVALUATED");
            event.put("symbol", signal.getSymbol());
            event.put("side", signal.getSide() == null ? "" : signal.getSide().name());
            event.put("action", signal.getAction() == null ? "" : signal.getAction().name());
            event.put("reason", signal.getSignalReason() == null ? "" : signal.getSignalReason());
            event.put("blockReason", signal.getBlockReason() == null ? "" : signal.getBlockReason());
            event.put("score", signal.getScore() == null ? 0 : signal.getScore());
            event.put("baseEntryScore", signal.getBaseEntryScore());
            event.put("bbScore", signal.getBbScore());
            event.put("finalEntryScore", signal.getFinalEntryScore());
            event.put("bbReasons", signal.getBbReasons());
            event.put("bbPercentB", signal.getBbPercentB());
            event.put("bbWidth", signal.getBbWidth());
            event.put("bbUpper", signal.getBbUpper());
            event.put("bbMiddle", signal.getBbMiddle());
            event.put("bbLower", signal.getBbLower());
            event.put("bbUpperTouched", signal.getBbUpperTouched());
            event.put("bbLowerTouched", signal.getBbLowerTouched());
            event.put("bbUpperClosedOutside", signal.getBbUpperClosedOutside());
            event.put("bbLowerClosedOutside", signal.getBbLowerClosedOutside());
            jsonlDecisionLogService.logEntry(event);
        }
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


    private void applyTechnicalSnapshot(EntrySignal signal) {
        if (binanceFuturesClient == null || indicatorService == null || signal == null || signal.getSymbol() == null) {
            return;
        }
        try {
            List<Kline> closed = nullSafeKlines(binanceFuturesClient.getKlines(signal.getSymbol(), "1h", 250)).stream()
                    .filter(k -> Boolean.TRUE.equals(k.getClosed()))
                    .toList();
            if (closed.size() < 220) {
                return;
            }
            TechnicalSnapshot snapshot = indicatorService.calculateOneHour(signal.getSymbol(), closed);
            signal.setClose1h(snapshot.getClose());
            signal.setPreviousClose1h(snapshot.getPreviousClose());
            signal.setPrevious1hHigh(snapshot.getPreviousHigh());
            signal.setPrevious1hLow(snapshot.getPreviousLow());
            signal.setEma20_1h(snapshot.getEma20());
            signal.setRsi14_1h(snapshot.getRsi14());
            signal.setPreviousRsi14_1h(snapshot.getPreviousRsi14());
            signal.setMacdHist_1h(snapshot.getMacdHist());
            signal.setPreviousMacdHist_1h(snapshot.getPreviousMacdHist());
            signal.setVolumeRatio_1h(snapshot.getVolumeRatio());
            signal.setAtr14_1h(snapshot.getAtr14());
            signal.setBbPercentB(snapshot.getBbPercentB());
            signal.setBbWidth(snapshot.getBbWidth());
            signal.setBbUpper(snapshot.getBbUpper());
            signal.setBbMiddle(snapshot.getBbMiddle());
            signal.setBbLower(snapshot.getBbLower());
            signal.setBbUpperTouched(snapshot.getBbUpperTouched());
            signal.setBbLowerTouched(snapshot.getBbLowerTouched());
            signal.setBbUpperClosedOutside(snapshot.getBbUpperClosedOutside());
            signal.setBbLowerClosedOutside(snapshot.getBbLowerClosedOutside());
        } catch (Exception exception) {
            log.warn("ENTRY_SIGNAL_TECHNICAL_UNAVAILABLE symbol={} reason={}", signal.getSymbol(), exception.getMessage());
        }
    }

    private void applyBollingerScore(EntrySignal signal) {
        if (signal == null) {
            return;
        }
        TechnicalSnapshot current = TechnicalSnapshot.builder()
                .close(signal.getClose1h())
                .ema20(signal.getEma20_1h())
                .rsi14(signal.getRsi14_1h())
                .macdHist(signal.getMacdHist_1h())
                .bbPercentB(signal.getBbPercentB())
                .bbWidth(signal.getBbWidth())
                .bbUpper(signal.getBbUpper())
                .bbMiddle(signal.getBbMiddle())
                .bbLower(signal.getBbLower())
                .bbUpperTouched(signal.getBbUpperTouched())
                .bbLowerTouched(signal.getBbLowerTouched())
                .bbUpperClosedOutside(signal.getBbUpperClosedOutside())
                .bbLowerClosedOutside(signal.getBbLowerClosedOutside())
                .build();
        TechnicalSnapshot previous = TechnicalSnapshot.builder()
                .rsi14(signal.getPreviousRsi14_1h())
                .macdHist(signal.getPreviousMacdHist_1h())
                .build();
        BollingerScoreResult bb = bollingerScoreService.calculate(signal.getSide(), signal.getMarketRegime(), current, previous);
        BigDecimal base = signal.getBaseEntryScore() == null ? BigDecimal.valueOf(signal.getScore() == null ? 0 : signal.getScore()) : signal.getBaseEntryScore();
        BigDecimal bbScore = bb.getBbScore() == null ? BigDecimal.ZERO : bb.getBbScore();
        BigDecimal finalScore = base.add(bbScore);
        signal.setBbScore(bbScore);
        signal.setFinalEntryScore(finalScore);
        signal.setScore(finalScore.intValue());
        signal.setBbReasons(new ArrayList<>(bb.getBbReasons() == null ? List.of() : bb.getBbReasons()));
        signal.setBbPercentB(bb.getBbPercentB());
        signal.setBbWidth(bb.getBbWidth());
        signal.setBbUpper(bb.getBbUpper());
        signal.setBbMiddle(bb.getBbMiddle());
        signal.setBbLower(bb.getBbLower());
        signal.setBbUpperTouched(bb.getBbUpperTouched());
        signal.setBbLowerTouched(bb.getBbLowerTouched());
        signal.setBbUpperClosedOutside(bb.getBbUpperClosedOutside());
        signal.setBbLowerClosedOutside(bb.getBbLowerClosedOutside());
    }

    private String validateLongTrigger(EntrySignal signal) {
        if (signal.getClose1h() == null) {
            return binanceFuturesClient == null ? null : "TECHNICAL_1H_NOT_READY";
        }
        if (!gt(signal.getClose1h(), signal.getEma20_1h())) return "LONG_CLOSE_BELOW_EMA20";
        if (!gt(signal.getMacdHist_1h(), BigDecimal.ZERO)) return "LONG_MACD_NOT_POSITIVE";
        if (lt(signal.getRsi14_1h(), new BigDecimal("45")) || gt(signal.getRsi14_1h(), new BigDecimal("68"))) return "LONG_RSI_OUT_OF_RANGE";
        if (!gt(signal.getVolumeRatio_1h(), BigDecimal.ONE)) return "VOLUME_NOT_CONFIRMED";
        boolean breakout = gt(signal.getClose1h(), signal.getPrevious1hHigh());
        boolean emaCross = lt(signal.getPreviousClose1h(), signal.getEma20_1h()) && gt(signal.getClose1h(), signal.getEma20_1h());
        boolean rsiCross = lt(signal.getPreviousRsi14_1h(), new BigDecimal("50")) && ge(signal.getRsi14_1h(), new BigDecimal("50"));
        boolean macdAccel = gt(signal.getMacdHist_1h(), signal.getPreviousMacdHist_1h()) && gt(signal.getMacdHist_1h(), BigDecimal.ZERO);
        if (breakout) signal.setEntryTrigger("LONG_BREAKOUT_PREVIOUS_HIGH");
        else if (emaCross) signal.setEntryTrigger("LONG_EMA20_CROSS");
        else if (rsiCross) signal.setEntryTrigger("LONG_RSI50_CROSS");
        else if (macdAccel) signal.setEntryTrigger("LONG_MACD_ACCELERATION");
        else return "NO_LONG_ENTRY_TRIGGER";
        return null;
    }

    private String validateShortTrigger(EntrySignal signal) {
        if (signal.getClose1h() == null) {
            return binanceFuturesClient == null ? null : "TECHNICAL_1H_NOT_READY";
        }
        if (!lt(signal.getClose1h(), signal.getEma20_1h())) return "SHORT_CLOSE_ABOVE_EMA20";
        if (!lt(signal.getMacdHist_1h(), BigDecimal.ZERO)) return "SHORT_MACD_NOT_NEGATIVE";
        boolean breakdown = lt(signal.getClose1h(), signal.getPrevious1hLow());
        boolean emaCross = gt(signal.getPreviousClose1h(), signal.getEma20_1h()) && lt(signal.getClose1h(), signal.getEma20_1h());
        boolean rsiCross = gt(signal.getPreviousRsi14_1h(), new BigDecimal("50")) && le(signal.getRsi14_1h(), new BigDecimal("50"));
        boolean macdAccel = lt(signal.getMacdHist_1h(), signal.getPreviousMacdHist_1h()) && lt(signal.getMacdHist_1h(), BigDecimal.ZERO);
        if (breakdown) signal.setEntryTrigger("SHORT_BREAKDOWN_PREVIOUS_LOW");
        else if (emaCross) signal.setEntryTrigger("SHORT_EMA20_CROSS");
        else if (rsiCross) signal.setEntryTrigger("SHORT_RSI50_CROSS");
        else if (macdAccel) signal.setEntryTrigger("SHORT_MACD_ACCELERATION");
        else return "NO_SHORT_ENTRY_TRIGGER";
        return null;
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

    private boolean gt(BigDecimal value, BigDecimal threshold) { return value != null && threshold != null && value.compareTo(threshold) > 0; }
    private boolean ge(BigDecimal value, BigDecimal threshold) { return value != null && threshold != null && value.compareTo(threshold) >= 0; }
    private boolean lt(BigDecimal value, BigDecimal threshold) { return value != null && threshold != null && value.compareTo(threshold) < 0; }
    private boolean le(BigDecimal value, BigDecimal threshold) { return value != null && threshold != null && value.compareTo(threshold) <= 0; }
    private List<Kline> nullSafeKlines(List<Kline> values) { return values == null ? List.of() : values; }

    private boolean greaterThan(BigDecimal value, BigDecimal threshold) {
        return value != null && threshold != null && value.compareTo(threshold) > 0;
    }

    private boolean lessThan(BigDecimal value, BigDecimal threshold) {
        return value != null && threshold != null && value.compareTo(threshold) < 0;
    }

    private int score(EntryCandidate candidate) {
        return intValue(candidate.getScore(), 0);
    }

    private int entryScoreValue(EntrySignal signal) {
        return signal == null || signal.getFinalEntryScore() == null ? 0 : signal.getFinalEntryScore().intValue();
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
