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
    private final EntryPriorityService entryPriorityService;

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
        if (candidate.getSourceClassification() == CoinClassification.WATCHLIST) {
            return blocked(signal, "WATCHLIST_NOT_ENTRY_ELIGIBLE");
        }
        applyTechnicalSnapshot(signal);
        applyBollingerScore(signal);
        applyEntryPriority(signal);

        if (candidate.getValidUntilUtc() != null && !candidate.getValidUntilUtc().isAfter(Instant.now())) {
            return blocked(signal, "CANDIDATE_EXPIRED");
        }
        if (candidate.getMarketRegime() == MarketRegime.PANIC) {
            return blocked(signal, "MARKET_PANIC_NO_NEW_ENTRY");
        }
        if (paperPositionRepository != null && paperPositionRepository.existsBySymbolAndStatusIn(candidate.getSymbol(), List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED))) {
            return blocked(signal, "IN_POSITION_NO_ENTRY");
        }
        if (greaterThan(candidate.getSpreadPct(), bigDecimalValue(config.getMaxSpreadPct(), "0.08"))) {
            return blocked(signal, "HIGH_SPREAD");
        }
        if (!hasRequiredTechnical(signal)) {
            return blocked(signal, "DATA_NOT_READY");
        }
        if (shouldBypassLongLateBbOutsideChase(signal, config)) {
            signal.getWarnings().add(ReasonTag.LONG_LATE_BB_OUTSIDE_CHASE_BYPASS);
            return blocked(signal, "LONG_BYPASS_LATE_BB_OUTSIDE_CHASE");
        }
        if (entryScoreValue(signal) < intValue(scannerProperties.getPaper().getMinEntryPriorityScore(), 65)) {
            signal.getWarnings().add(ReasonTag.ENTRY_PRIORITY_TOO_LOW);
            return blocked(signal, "ENTRY_PRIORITY_TOO_LOW");
        }

        if (candidate.getSide() == PositionSide.LONG) {
            addLongSoftWarnings(signal);
            String triggerBlock = validateLongTrigger(signal);
            if (triggerBlock != null) {
                return blocked(signal, triggerBlock);
            }
            signal.setAction(EntryAction.ENTER_LONG);
            signal.setSignalReason(resolveSignalReason(candidate));
            return ready(signal);
        }

        if (candidate.getSide() == PositionSide.SHORT) {
            addShortSoftWarnings(signal);
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
                .scanRunId(candidate.getScanRunId())
                .sourceScanType(candidate.getSourceScanType())
                .candidateId(candidate.getId())
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
                .close4h(candidate.getClose4h())
                .ema20_4h(candidate.getEma20_4h())
                .ema50_4h(candidate.getEma50_4h())
                .ema200_4h(candidate.getEma200_4h())
                .rsi14_4h(candidate.getRsi14_4h())
                .macdHist_4h(candidate.getMacdHist_4h())
                .atr14_4h(candidate.getAtr14_4h())
                .volumeRatio_4h(candidate.getVolumeRatio_4h())
                .fourHourAlignment(candidate.getFourHourAlignment())
                .riskPenalty(candidate.getRiskPenalty())
                .spreadPenalty(candidate.getSpreadPenalty())
                .fundingPenalty(candidate.getFundingPenalty())
                .sidePenalty(candidate.getSidePenalty())
                .fourHourPenalty(candidate.getFourHourPenalty())
                .symbolCooldownPenalty(candidate.getSymbolCooldownPenalty())
                .volumeConfirmationBonus(candidate.getVolumeConfirmationBonus())
                .marketRegimeAlignmentBonus(candidate.getMarketRegimeAlignmentBonus())
                .fourHourAlignmentBonus(candidate.getFourHourAlignmentBonus())
                .cooldownPenaltyApplied(candidate.getCooldownPenaltyApplied())
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
            event.put("time", signal.getSignalTime());
            event.put("event", "ENTRY_SIGNAL_EVALUATED");
            event.put("scanRunId", signal.getScanRunId());
            event.put("candidateId", signal.getCandidateId());
            event.put("symbol", signal.getSymbol());
            event.put("side", signal.getSide() == null ? "" : signal.getSide().name());
            event.put("action", signal.getAction() == null ? "" : signal.getAction().name());
            event.put("blockReason", signal.getBlockReason() == null ? "" : signal.getBlockReason());
            event.put("entryPriorityScore", signal.getEntryPriorityScore());
            event.put("scannerScore", signal.getScannerScore());
            event.put("riskLevel", signal.getRiskLevel() == null ? "" : signal.getRiskLevel().name());
            event.put("marketRegime", signal.getMarketRegime() == null ? "" : signal.getMarketRegime().name());
            event.put("marketBreadthPct", signal.getMarketBreadthPct());
            event.put("close1h", signal.getClose1h());
            event.put("previousClose1h", signal.getPreviousClose1h());
            event.put("previous1hHigh", signal.getPrevious1hHigh());
            event.put("previous1hLow", signal.getPrevious1hLow());
            event.put("ema20_1h", signal.getEma20_1h());
            event.put("ema50_1h", signal.getEma50_1h());
            event.put("ema200_1h", signal.getEma200_1h());
            event.put("rsi14_1h", signal.getRsi14_1h());
            event.put("previousRsi14_1h", signal.getPreviousRsi14_1h());
            event.put("macdHist_1h", signal.getMacdHist_1h());
            event.put("previousMacdHist_1h", signal.getPreviousMacdHist_1h());
            event.put("atr14_1h", signal.getAtr14_1h());
            event.put("volumeRatio_1h", signal.getVolumeRatio_1h());
            event.put("close4h", signal.getClose4h());
            event.put("ema20_4h", signal.getEma20_4h());
            event.put("ema50_4h", signal.getEma50_4h());
            event.put("ema200_4h", signal.getEma200_4h());
            event.put("rsi14_4h", signal.getRsi14_4h());
            event.put("macdHist_4h", signal.getMacdHist_4h());
            event.put("atr14_4h", signal.getAtr14_4h());
            event.put("volumeRatio_4h", signal.getVolumeRatio_4h());
            event.put("fourHourAlignment", signal.getFourHourAlignment() == null ? "" : signal.getFourHourAlignment().name());
            event.put("riskPenalty", signal.getRiskPenalty());
            event.put("spreadPenalty", signal.getSpreadPenalty());
            event.put("fundingPenalty", signal.getFundingPenalty());
            event.put("sidePenalty", signal.getSidePenalty());
            event.put("fourHourPenalty", signal.getFourHourPenalty());
            event.put("symbolCooldownPenalty", signal.getSymbolCooldownPenalty());
            event.put("volumeConfirmationBonus", signal.getVolumeConfirmationBonus());
            event.put("marketRegimeAlignmentBonus", signal.getMarketRegimeAlignmentBonus());
            event.put("fourHourAlignmentBonus", signal.getFourHourAlignmentBonus());
            event.put("finalEntryPriorityScore", signal.getFinalEntryScore());
            event.put("fundingRate", signal.getFundingRate());
            event.put("spreadPct", signal.getSpreadPct());
            event.put("priceChange24hPct", signal.getPriceChange24hPct());
            event.put("openPositionCount", signal.getOpenPositionCount());
            event.put("openShortPositionCount", signal.getOpenShortPositionCount());
            event.put("newEntriesInCurrentScan", signal.getNewEntriesInCurrentScan());
            event.put("reasons", signal.getReasons());
            event.put("warnings", signal.getWarnings());
            event.put("configSnapshot", entryPriorityService.configSnapshot());
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
            signal.setEma50_1h(snapshot.getEma50());
            signal.setEma200_1h(snapshot.getEma200());
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
            List<Kline> closed4h = nullSafeKlines(binanceFuturesClient.getKlines(signal.getSymbol(), "4h", 250)).stream()
                    .filter(k -> Boolean.TRUE.equals(k.getClosed()))
                    .toList();
            if (closed4h.size() >= 220) {
                TechnicalSnapshot four = indicatorService.calculateFourHour(signal.getSymbol(), closed4h);
                signal.setClose4h(four.getClose());
                signal.setEma20_4h(four.getEma20());
                signal.setEma50_4h(four.getEma50());
                signal.setEma200_4h(four.getEma200());
                signal.setRsi14_4h(four.getRsi14());
                signal.setMacdHist_4h(four.getMacdHist());
                signal.setAtr14_4h(four.getAtr14());
                signal.setVolumeRatio_4h(four.getVolumeRatio());
            }
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

    private boolean shouldBypassLongLateBbOutsideChase(EntrySignal signal, ScannerProperties.EntrySignal config) {
        if (signal == null || signal.getSide() != PositionSide.LONG || !booleanValue(config.getLongLateBbOutsideChaseBypassEnabled(), true)) {
            return false;
        }
        BigDecimal priceChange = signal.getPriceChange24hPct();
        if (priceChange == null || priceChange.compareTo(bigDecimalValue(config.getLongLateBbOutsidePriceChangeThresholdPct(), "5")) < 0) {
            return false;
        }
        BigDecimal percentBThreshold = bigDecimalValue(config.getLongLateBbOutsideBbPercentBThreshold(), "1.0");
        boolean percentBOutside = signal.getBbPercentB() != null && signal.getBbPercentB().compareTo(percentBThreshold) >= 0;
        boolean upperClosedOutside = Boolean.TRUE.equals(signal.getBbUpperClosedOutside());
        boolean outsideReason = signal.getBbReasons() != null && signal.getBbReasons().contains("LONG_BB_OUTSIDE_CHASE");
        return percentBOutside || upperClosedOutside || outsideReason;
    }

    private void applyEntryPriority(EntrySignal signal) {
        if (signal == null) return;
        List<com.crypto.persistence.entity.PaperPositionEntity> openPositions = paperPositionRepository == null
                ? List.of()
                : paperPositionRepository.findByStatusInOrderByOpenedAtDesc(List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED));
        signal.setOpenPositionCount(openPositions.size());
        signal.setOpenShortPositionCount((int) openPositions.stream().filter(p -> p.getSide() == PositionSide.SHORT).count());
        signal.setNewEntriesInCurrentScan(0);
        boolean cooldown = recentStopLossCount(signal) >= intValue(scannerProperties.getEntryPriority().getSymbolRecentStopCountThreshold(), 2);
        signal.setCooldownPenaltyApplied(cooldown);
        if (cooldown) {
            signal.getWarnings().add(ReasonTag.SYMBOL_RECENT_STOP_PENALTY);
            log.warn("SYMBOL_RECENT_STOP_PENALTY symbol={}", signal.getSymbol());
        }
        EntryPriorityService.PriorityBreakdown breakdown = entryPriorityService.calculate(signal, null, cooldown);
        signal.setEntryPriorityScore(breakdown.finalEntryPriorityScore());
        signal.setFinalEntryScore(BigDecimal.valueOf(breakdown.finalEntryPriorityScore()));
        signal.setRiskPenalty(breakdown.riskPenalty());
        signal.setSpreadPenalty(breakdown.spreadPenalty());
        signal.setFundingPenalty(breakdown.fundingPenalty());
        signal.setSidePenalty(breakdown.sidePenalty());
        signal.setFourHourPenalty(breakdown.fourHourPenalty());
        signal.setSymbolCooldownPenalty(breakdown.symbolCooldownPenalty());
        signal.setVolumeConfirmationBonus(breakdown.volumeConfirmationBonus());
        signal.setMarketRegimeAlignmentBonus(breakdown.marketRegimeAlignmentBonus());
        signal.setFourHourAlignmentBonus(breakdown.fourHourAlignmentBonus());
        signal.setFourHourAlignment(breakdown.fourHourAlignment());
        signal.setRiskLevel(entryPriorityService.adjustedRiskLevel(EntryPriorityService.PriorityInput.from(signal, null, cooldown), breakdown));
        if (breakdown.fourHourAlignment() == com.crypto.common.enums.FourHourAlignment.AGAINST_4H_TREND) {
            signal.getWarnings().add(ReasonTag.AGAINST_4H_TREND);
        }
    }

    private long recentStopLossCount(EntrySignal signal) {
        if (paperPositionRepository == null || signal == null || signal.getSymbol() == null) return 0;
        Instant since = Instant.now().minusSeconds(intValue(scannerProperties.getEntryPriority().getSymbolRecentStopCountWindowHours(), 24) * 3600L);
        return paperPositionRepository.findByStatusAndSymbolOrderByClosedAtDesc(PaperPositionStatus.CLOSED, signal.getSymbol(), org.springframework.data.domain.PageRequest.of(0, 20))
                .stream()
                .filter(p -> p.getClosedAt() != null && p.getClosedAt().isAfter(since))
                .filter(p -> "STOP_LOSS".equals(p.getExitReason()))
                .count();
    }

    private boolean hasRequiredTechnical(EntrySignal signal) {
        if (binanceFuturesClient == null) {
            return true;
        }
        return signal.getClose1h() != null && signal.getPreviousClose1h() != null && signal.getPrevious1hHigh() != null
                && signal.getPrevious1hLow() != null && signal.getEma20_1h() != null && signal.getEma50_1h() != null
                && signal.getEma200_1h() != null && signal.getRsi14_1h() != null && signal.getPreviousRsi14_1h() != null
                && signal.getMacdHist_1h() != null && signal.getPreviousMacdHist_1h() != null && signal.getAtr14_1h() != null
                && signal.getVolumeRatio_1h() != null && signal.getClose4h() != null && signal.getEma20_4h() != null
                && signal.getEma50_4h() != null && signal.getEma200_4h() != null && signal.getRsi14_4h() != null
                && signal.getMacdHist_4h() != null && signal.getAtr14_4h() != null && signal.getVolumeRatio_4h() != null;
    }

    private void addLongSoftWarnings(EntrySignal signal) {
        if (gt(signal.getRsi14_1h(), new BigDecimal("75"))) signal.getWarnings().add(ReasonTag.RSI_OVERBOUGHT);
        if (ge(signal.getFundingRate(), scannerProperties.getFunding().getDangerPositive())) signal.getWarnings().add(ReasonTag.LONG_CROWDED);
        if (gt(signal.getPriceChange24hPct(), new BigDecimal("15"))) signal.getWarnings().add(ReasonTag.LATE_LONG_RISK);
        if (lt(signal.getClose4h(), signal.getEma20_4h()) || lt(signal.getMacdHist_4h(), BigDecimal.ZERO)) signal.getWarnings().add(ReasonTag.AGAINST_4H_TREND);
    }

    private void addShortSoftWarnings(EntrySignal signal) {
        if (lt(signal.getRsi14_1h(), new BigDecimal("30")) || lt(signal.getRsi14_4h(), new BigDecimal("30"))) signal.getWarnings().add(ReasonTag.SHORT_OVERSOLD_WARNING);
        if (lt(signal.getPriceChange24hPct(), new BigDecimal("-15"))) signal.getWarnings().add(ReasonTag.SHORT_EXTREME_LATE_DUMP_RISK);
        if (le(signal.getFundingRate(), scannerProperties.getFunding().getDangerNegative())) signal.getWarnings().add(ReasonTag.SHORT_CROWDED);
        if (gt(signal.getClose4h(), signal.getEma20_4h()) || gt(signal.getMacdHist_4h(), BigDecimal.ZERO)) signal.getWarnings().add(ReasonTag.AGAINST_4H_TREND);
        if (signal.getMarketRegime() != MarketRegime.RISK_OFF) signal.getWarnings().add(ReasonTag.SHORT_MARKET_REGIME_MISMATCH);
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
