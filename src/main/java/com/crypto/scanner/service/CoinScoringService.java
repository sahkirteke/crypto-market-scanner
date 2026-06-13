package com.crypto.scanner.service;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.CoinScoringInput;
import com.crypto.scanner.model.MarketRegimeResult;
import com.crypto.scanner.model.V20SignalResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinScoringService {
    private static final BigDecimal RSI_LONG_MIN = BigDecimal.valueOf(45);
    private static final BigDecimal RSI_LONG_MAX = BigDecimal.valueOf(68);
    private static final BigDecimal RSI_SHORT_MIN = BigDecimal.valueOf(32);
    private static final BigDecimal RSI_SHORT_MAX = BigDecimal.valueOf(55);
    private static final BigDecimal RSI_OVERBOUGHT = BigDecimal.valueOf(75);
    private static final BigDecimal RSI_SHORT_EXTREME_OVERSOLD = BigDecimal.valueOf(28);
    private static final BigDecimal RSI_SHORT_OVERSOLD = BigDecimal.valueOf(32);
    private static final BigDecimal VOLUME_STRONG = new BigDecimal("1.2");
    private static final BigDecimal VOLUME_CONFIRMED = BigDecimal.ONE;
    private static final BigDecimal LATE_LONG_CHANGE_PCT = BigDecimal.valueOf(15);
    private static final BigDecimal LATE_SHORT_CHANGE_PCT = BigDecimal.valueOf(-12);
    private static final BigDecimal EXTREME_SHORT_CHANGE_PCT = BigDecimal.valueOf(-18);

    private final ScannerProperties scannerProperties;

    public CoinScanResult score(CoinScoringInput input) {
        if (input == null) {
            CoinScanResult result = dataNotReadyResult(null);
            logScored(result);
            return result;
        }

        if (input.getOneHour() == null || input.getFourHour() == null) {
            CoinScanResult result = dataNotReadyResult(input.getSymbol());
            enrichMarketData(result, input);
            logScored(result);
            return result;
        }

        if (isV20Enabled()) {
            CoinScanResult result = scoreV20(input);
            logScored(result);
            return result;
        }

        List<ReasonTag> reasons = new ArrayList<>();
        List<ReasonTag> warnings = new ArrayList<>();
        addMarketReason(input.getMarketRegimeResult(), reasons, warnings);

        int longScore = calculateLongScore(input, reasons, warnings);
        int shortScore = calculateShortScore(input, reasons, warnings);
        DirectionBias directionBias = determineDirectionBias(longScore, shortScore, input.getMarketRegimeResult());
        int effectiveScore = effectiveScore(longScore, shortScore, directionBias);
        CoinClassification classification = determineClassification(effectiveScore, directionBias, input.getMarketRegimeResult());
        EliminationReason eliminatedReason = determineEliminatedReason(classification, input.getMarketRegimeResult());
        RiskLevel riskLevel = determineRiskLevel(warnings, input.getMarketRegimeResult());

        CoinScanResult result = CoinScanResult.builder()
                .symbol(input.getSymbol())
                .directionBias(directionBias)
                .classification(classification)
                .score(effectiveScore)
                .longScore(longScore)
                .shortScore(shortScore)
                .riskLevel(riskLevel)
                .eliminatedReason(eliminatedReason)
                .reasons(reasons)
                .warnings(warnings)
                .scanTime(Instant.now())
                .build();
        enrichMarketData(result, input);
        logScored(result);
        return result;
    }


    private CoinScanResult scoreV20(CoinScoringInput input) {
        List<ReasonTag> reasons = new ArrayList<>();
        List<ReasonTag> warnings = new ArrayList<>();
        addMarketReason(input.getMarketRegimeResult(), reasons, warnings);
        if (marketRegime(input.getMarketRegimeResult()) == MarketRegime.PANIC) {
            return CoinScanResult.builder()
                    .symbol(input.getSymbol())
                    .directionBias(DirectionBias.NEUTRAL)
                    .classification(CoinClassification.ELIMINATED)
                    .score(0).longScore(0).shortScore(0)
                    .riskLevel(RiskLevel.HIGH)
                    .eliminatedReason(EliminationReason.MARKET_PANIC_NO_NEW_ENTRY)
                    .reasons(reasons)
                    .warnings(warnings)
                    .scanTime(Instant.now())
                    .build();
        }
        V20SignalService v20SignalService = new V20SignalService(scannerProperties);
        List<BigDecimal> fundingRates = input.getFuturesSnapshot() == null ? List.of() : input.getFuturesSnapshot().getFundingRates();
        BigDecimal fundingRate = fundingRate(input.getFuturesSnapshot());
        V20SignalResult longResult = v20SignalService.evaluateLong(input.getFourHour(), input.getOneHour(), fundingRate, fundingRates, false, false);
        V20SignalResult shortResult = v20SignalService.evaluateShort(input.getFourHour(), input.getOneHour(), fundingRate, fundingRates, false, false);
        boolean longPass = pass(longResult);
        boolean shortPass = pass(shortResult);
        DirectionBias directionBias = DirectionBias.NEUTRAL;
        CoinClassification classification = CoinClassification.WATCHLIST;
        int effectiveScore = Math.max(score(longResult), score(shortResult));
        if (longPass && shortPass) {
            int diff = Math.abs(score(longResult) - score(shortResult));
            if (diff <= 1) {
                classification = CoinClassification.ELIMINATED;
                reasons.add(ReasonTag.DATA_NOT_READY);
            } else if (score(longResult) > score(shortResult)) {
                directionBias = DirectionBias.LONG;
                classification = CoinClassification.STRONG_LONG;
                effectiveScore = score(longResult);
            } else {
                directionBias = DirectionBias.SHORT;
                classification = CoinClassification.STRONG_SHORT;
                effectiveScore = score(shortResult);
            }
        } else if (longPass) {
            directionBias = DirectionBias.LONG;
            classification = CoinClassification.STRONG_LONG;
            effectiveScore = score(longResult);
        } else if (shortPass) {
            directionBias = DirectionBias.SHORT;
            classification = CoinClassification.STRONG_SHORT;
            effectiveScore = score(shortResult);
        }
        if (classification == CoinClassification.WATCHLIST) {
            reasons.add(ReasonTag.DATA_NOT_READY);
        }
        CoinScanResult result = CoinScanResult.builder()
                .symbol(input.getSymbol())
                .directionBias(directionBias)
                .classification(classification)
                .score(effectiveScore)
                .longScore(score(longResult))
                .shortScore(score(shortResult))
                .riskLevel(determineRiskLevel(warnings, input.getMarketRegimeResult()))
                .eliminatedReason(classification == CoinClassification.ELIMINATED ? EliminationReason.DATA_NOT_READY : null)
                .reasons(reasons)
                .warnings(warnings)
                .scanTime(Instant.now())
                .build();
        enrichMarketData(result, input);
        return result;
    }

    private boolean pass(V20SignalResult result) {
        return result != null && result.isBaseSignalPass() && result.isEntryFiltersPass() && result.isScorePass();
    }

    private int score(V20SignalResult result) { return result == null ? 0 : result.getSignalScore(); }

    private boolean isV20Enabled() { return scannerProperties.getV20() != null && Boolean.TRUE.equals(scannerProperties.getV20().getEnabled()); }

    public int calculateLongScore(CoinScoringInput input, List<ReasonTag> reasons, List<ReasonTag> warnings) {
        int trendScore = 0;
        TechnicalSnapshot fourHour = input.getFourHour();
        if (gt(fourHour.getClose(), fourHour.getEma20())) {
            trendScore += 10;
        }
        if (gt(fourHour.getEma20(), fourHour.getEma50())) {
            trendScore += 10;
        }
        if (gt(fourHour.getEma50(), fourHour.getEma200())) {
            trendScore += 10;
        }
        if (trendScore > 0) {
            addUnique(reasons, ReasonTag.TREND_4H_POSITIVE);
        }

        int momentumScore = 0;
        TechnicalSnapshot oneHour = input.getOneHour();
        if (gt(oneHour.getClose(), oneHour.getEma20())) {
            momentumScore += 5;
        }
        if (gt(oneHour.getMacdHist(), BigDecimal.ZERO)) {
            momentumScore += 7;
            addUnique(reasons, ReasonTag.MOMENTUM_1H_POSITIVE);
        }
        if (betweenInclusive(oneHour.getRsi14(), RSI_LONG_MIN, RSI_LONG_MAX)) {
            momentumScore += 8;
            addUnique(reasons, ReasonTag.RSI_IDEAL_LONG);
        }

        int volumeScore = calculateVolumeScore(oneHour, reasons, warnings);
        int futuresScore = calculateLongFuturesScore(input.getFuturesSnapshot(), warnings);
        int marketScore = calculateLongMarketScore(input.getMarketRegimeResult(), warnings);
        int riskScore = calculateLongRiskScore(oneHour, input.getTicker24h(), input.getFuturesSnapshot(), warnings);
        return Math.min(100, trendScore + momentumScore + volumeScore + futuresScore + marketScore + riskScore);
    }

    public int calculateShortScore(CoinScoringInput input, List<ReasonTag> reasons, List<ReasonTag> warnings) {
        int trendScore = 0;
        TechnicalSnapshot fourHour = input.getFourHour();
        if (lt(fourHour.getClose(), fourHour.getEma20())) {
            trendScore += 10;
        }
        if (lt(fourHour.getEma20(), fourHour.getEma50())) {
            trendScore += 10;
        }
        if (lt(fourHour.getEma50(), fourHour.getEma200())) {
            trendScore += 10;
        }
        if (trendScore > 0) {
            addUnique(reasons, ReasonTag.TREND_4H_NEGATIVE);
        }

        int momentumScore = 0;
        TechnicalSnapshot oneHour = input.getOneHour();
        if (lt(oneHour.getClose(), oneHour.getEma20())) {
            momentumScore += 5;
        }
        if (lt(oneHour.getMacdHist(), BigDecimal.ZERO)) {
            momentumScore += 7;
            addUnique(reasons, ReasonTag.MOMENTUM_1H_NEGATIVE);
        }
        if (betweenInclusive(oneHour.getRsi14(), RSI_SHORT_MIN, RSI_SHORT_MAX)) {
            momentumScore += 8;
            addUnique(reasons, ReasonTag.RSI_IDEAL_SHORT);
        }

        int volumeScore = calculateVolumeScore(oneHour, reasons, warnings);
        int futuresScore = calculateShortFuturesScore(input.getFuturesSnapshot(), warnings);
        int marketScore = calculateShortMarketScore(input.getMarketRegimeResult(), warnings);
        int riskScore = calculateShortRiskScore(oneHour, input.getTicker24h(), input.getFuturesSnapshot(), warnings);
        return Math.min(100, trendScore + momentumScore + volumeScore + futuresScore + marketScore + riskScore);
    }

    public DirectionBias determineDirectionBias(int longScore, int shortScore, MarketRegimeResult marketRegimeResult) {
        if (marketRegime(marketRegimeResult) == MarketRegime.PANIC) {
            return DirectionBias.NEUTRAL;
        }
        int threshold = valueOrDefault(scoring().getDirectionDifferenceThreshold(), 10);
        if (longScore >= shortScore + threshold) {
            return DirectionBias.LONG;
        }
        if (shortScore >= longScore + threshold) {
            return DirectionBias.SHORT;
        }
        return DirectionBias.NEUTRAL;
    }

    public CoinClassification determineClassification(
            int effectiveScore,
            DirectionBias directionBias,
            MarketRegimeResult marketRegimeResult
    ) {
        MarketRegime marketRegime = marketRegime(marketRegimeResult);
        if (marketRegime == MarketRegime.PANIC) {
            return CoinClassification.ELIMINATED;
        }

        int strongThreshold = valueOrDefault(scoring().getStrongThreshold(), 80);
        if (directionBias == DirectionBias.LONG) {
            int longStrongThreshold = marketRegime == MarketRegime.RISK_OFF ? strongThreshold + 10 : strongThreshold;
            if (effectiveScore >= longStrongThreshold) {
                return CoinClassification.STRONG_LONG;
            }
        }
        if (directionBias == DirectionBias.SHORT) {
            int shortStrongThreshold = marketRegime == MarketRegime.RISK_ON ? strongThreshold + 10 : strongThreshold;
            if (effectiveScore >= shortStrongThreshold) {
                return CoinClassification.STRONG_SHORT;
            }
        }
        return effectiveScore >= valueOrDefault(scoring().getWatchlistThreshold(), 65)
                ? CoinClassification.WATCHLIST
                : CoinClassification.ELIMINATED;
    }

    public RiskLevel determineRiskLevel(List<ReasonTag> warnings, MarketRegimeResult marketRegimeResult) {
        if (marketRegime(marketRegimeResult) == MarketRegime.PANIC) {
            return RiskLevel.HIGH;
        }
        List<ReasonTag> safeWarnings = warnings == null ? List.of() : warnings;
        EnumSet<ReasonTag> highRiskWarnings = EnumSet.of(
                ReasonTag.LONG_CROWDED,
                ReasonTag.SHORT_CROWDED,
                ReasonTag.RSI_OVERBOUGHT,
                ReasonTag.SHORT_EXTREME_OVERSOLD_RISK,
                ReasonTag.SHORT_EXTREME_LATE_DUMP_RISK);
        if (safeWarnings.stream().anyMatch(highRiskWarnings::contains)) {
            return RiskLevel.HIGH;
        }

        EnumSet<ReasonTag> mediumRiskWarnings = EnumSet.of(
                ReasonTag.FUNDING_SLIGHTLY_HIGH,
                ReasonTag.FUNDING_SLIGHTLY_NEGATIVE,
                ReasonTag.LATE_LONG_RISK,
                ReasonTag.LATE_SHORT_WARNING,
                ReasonTag.SHORT_OVERSOLD_WARNING,
                ReasonTag.PUMPED_TOO_MUCH_WARNING,
                ReasonTag.DUMPED_TOO_MUCH_WARNING,
                ReasonTag.VOLUME_WEAK);
        return safeWarnings.stream().anyMatch(mediumRiskWarnings::contains) ? RiskLevel.MEDIUM : RiskLevel.LOW;
    }

    private int calculateVolumeScore(TechnicalSnapshot oneHour, List<ReasonTag> reasons, List<ReasonTag> warnings) {
        BigDecimal volumeRatio = oneHour.getVolumeRatio();
        if (volumeRatio == null) {
            return 0;
        }
        if (volumeRatio.compareTo(VOLUME_STRONG) > 0) {
            addUnique(reasons, ReasonTag.VOLUME_CONFIRMED);
            return 15;
        }
        if (volumeRatio.compareTo(VOLUME_CONFIRMED) > 0) {
            addUnique(reasons, ReasonTag.VOLUME_CONFIRMED);
            return 8;
        }
        addUnique(warnings, ReasonTag.VOLUME_WEAK);
        return 0;
    }

    private int calculateLongFuturesScore(FuturesSnapshot futuresSnapshot, List<ReasonTag> warnings) {
        BigDecimal fundingRate = fundingRate(futuresSnapshot);
        if (fundingRate == null) {
            return 0;
        }
        ScannerProperties.Funding funding = funding();
        if (fundingRate.compareTo(funding.getWarningNegative()) > 0
                && fundingRate.compareTo(funding.getWarningPositive()) < 0) {
            return 10;
        }
        if (fundingRate.compareTo(funding.getWarningPositive()) >= 0
                && fundingRate.compareTo(funding.getDangerPositive()) < 0) {
            addUnique(warnings, ReasonTag.FUNDING_SLIGHTLY_HIGH);
            return 5;
        }
        if (fundingRate.compareTo(funding.getDangerPositive()) >= 0) {
            addUnique(warnings, ReasonTag.LONG_CROWDED);
        }
        return 0;
    }

    private int calculateShortFuturesScore(FuturesSnapshot futuresSnapshot, List<ReasonTag> warnings) {
        BigDecimal fundingRate = fundingRate(futuresSnapshot);
        if (fundingRate == null) {
            return 0;
        }
        ScannerProperties.Funding funding = funding();
        if (fundingRate.compareTo(funding.getWarningNegative()) > 0
                && fundingRate.compareTo(funding.getWarningPositive()) < 0) {
            return 10;
        }
        if (fundingRate.compareTo(funding.getDangerNegative()) > 0
                && fundingRate.compareTo(funding.getWarningNegative()) <= 0) {
            addUnique(warnings, ReasonTag.FUNDING_SLIGHTLY_NEGATIVE);
            return 5;
        }
        if (fundingRate.compareTo(funding.getDangerNegative()) <= 0) {
            addUnique(warnings, ReasonTag.SHORT_CROWDED);
        }
        return 0;
    }

    private int calculateLongMarketScore(MarketRegimeResult marketRegimeResult, List<ReasonTag> warnings) {
        MarketRegime marketRegime = marketRegime(marketRegimeResult);
        return switch (marketRegime) {
            case RISK_ON -> 15;
            case CHOP -> 5;
            case PANIC -> {
                addUnique(warnings, ReasonTag.MARKET_PANIC);
                yield 0;
            }
            case RISK_OFF -> 0;
        };
    }

    private int calculateShortMarketScore(MarketRegimeResult marketRegimeResult, List<ReasonTag> warnings) {
        MarketRegime marketRegime = marketRegime(marketRegimeResult);
        return switch (marketRegime) {
            case RISK_OFF -> 15;
            case CHOP -> 5;
            case PANIC -> {
                addUnique(warnings, ReasonTag.MARKET_PANIC);
                yield 0;
            }
            case RISK_ON -> 0;
        };
    }

    private int calculateLongRiskScore(
            TechnicalSnapshot oneHour,
            Ticker24h ticker24h,
            FuturesSnapshot futuresSnapshot,
            List<ReasonTag> warnings
    ) {
        int riskScore = 10;
        if (oneHour.getRsi14() != null && oneHour.getRsi14().compareTo(RSI_OVERBOUGHT) > 0) {
            addUnique(warnings, ReasonTag.RSI_OVERBOUGHT);
            riskScore = Math.min(riskScore, 0);
        }
        BigDecimal priceChangePercent = priceChangePercent(ticker24h);
        BigDecimal maxPump24hPct = liquidity().getMaxPump24hPct();
        if (priceChangePercent != null && maxPump24hPct != null
                && priceChangePercent.compareTo(maxPump24hPct) > 0) {
            addUnique(warnings, ReasonTag.PUMPED_TOO_MUCH_WARNING);
            riskScore = Math.min(riskScore, 2);
        } else if (priceChangePercent != null
                && priceChangePercent.compareTo(LATE_LONG_CHANGE_PCT) > 0) {
            addUnique(warnings, ReasonTag.LATE_LONG_RISK);
            riskScore = Math.min(riskScore, 5);
        }
        BigDecimal fundingRate = fundingRate(futuresSnapshot);
        if (fundingRate != null && fundingRate.compareTo(funding().getDangerPositive()) >= 0) {
            addUnique(warnings, ReasonTag.LONG_CROWDED);
            riskScore = Math.min(riskScore, 0);
        }
        return riskScore;
    }

    private int calculateShortRiskScore(
            TechnicalSnapshot oneHour,
            Ticker24h ticker24h,
            FuturesSnapshot futuresSnapshot,
            List<ReasonTag> warnings
    ) {
        int riskScore = 10;
        if (oneHour.getRsi14() != null && oneHour.getRsi14().compareTo(RSI_SHORT_EXTREME_OVERSOLD) < 0) {
            addUnique(warnings, ReasonTag.SHORT_EXTREME_OVERSOLD_RISK);
            riskScore = Math.min(riskScore, 2);
        } else if (oneHour.getRsi14() != null && oneHour.getRsi14().compareTo(RSI_SHORT_OVERSOLD) < 0) {
            addUnique(warnings, ReasonTag.SHORT_OVERSOLD_WARNING);
            riskScore = Math.min(riskScore, 5);
        }

        BigDecimal priceChangePercent = priceChangePercent(ticker24h);
        BigDecimal maxDump24hPct = liquidity().getMaxDump24hPct();
        if (priceChangePercent != null && maxDump24hPct != null
                && priceChangePercent.compareTo(maxDump24hPct) < 0) {
            addUnique(warnings, ReasonTag.DUMPED_TOO_MUCH_WARNING);
            riskScore = Math.min(riskScore, 2);
        }
        if (priceChangePercent != null && priceChangePercent.compareTo(EXTREME_SHORT_CHANGE_PCT) < 0) {
            addUnique(warnings, ReasonTag.SHORT_EXTREME_LATE_DUMP_RISK);
            riskScore = Math.min(riskScore, 2);
        } else if (priceChangePercent != null && priceChangePercent.compareTo(LATE_SHORT_CHANGE_PCT) < 0) {
            addUnique(warnings, ReasonTag.LATE_SHORT_WARNING);
            riskScore = Math.min(riskScore, 5);
        }

        BigDecimal fundingRate = fundingRate(futuresSnapshot);
        if (fundingRate != null && fundingRate.compareTo(funding().getDangerNegative()) <= 0) {
            addUnique(warnings, ReasonTag.SHORT_CROWDED);
            riskScore = Math.min(riskScore, 0);
        }
        return riskScore;
    }

    private void addMarketReason(MarketRegimeResult marketRegimeResult, List<ReasonTag> reasons, List<ReasonTag> warnings) {
        MarketRegime marketRegime = marketRegime(marketRegimeResult);
        switch (marketRegime) {
            case RISK_ON -> addUnique(reasons, ReasonTag.MARKET_RISK_ON);
            case RISK_OFF -> addUnique(reasons, ReasonTag.MARKET_RISK_OFF);
            case CHOP -> {
                addUnique(reasons, ReasonTag.MARKET_CHOP);
                addUnique(warnings, ReasonTag.MARKET_CHOP);
            }
            case PANIC -> addUnique(warnings, ReasonTag.MARKET_PANIC);
        }
    }

    private int effectiveScore(int longScore, int shortScore, DirectionBias directionBias) {
        return switch (directionBias) {
            case LONG -> longScore;
            case SHORT -> shortScore;
            case NEUTRAL -> Math.max(longScore, shortScore);
        };
    }

    private EliminationReason determineEliminatedReason(
            CoinClassification classification,
            MarketRegimeResult marketRegimeResult
    ) {
        if (classification != CoinClassification.ELIMINATED) {
            return EliminationReason.NONE;
        }
        if (marketRegime(marketRegimeResult) == MarketRegime.PANIC) {
            return EliminationReason.MARKET_PANIC_NO_NEW_ENTRY;
        }
        return EliminationReason.SCORE_BELOW_THRESHOLD;
    }

    private CoinScanResult dataNotReadyResult(String symbol) {
        List<ReasonTag> reasons = new ArrayList<>();
        reasons.add(ReasonTag.DATA_NOT_READY);
        return CoinScanResult.builder()
                .symbol(symbol)
                .directionBias(DirectionBias.NEUTRAL)
                .classification(CoinClassification.ELIMINATED)
                .score(0)
                .longScore(0)
                .shortScore(0)
                .riskLevel(RiskLevel.HIGH)
                .eliminatedReason(EliminationReason.DATA_NOT_READY)
                .reasons(reasons)
                .warnings(new ArrayList<>())
                .scanTime(Instant.now())
                .build();
    }

    private void enrichMarketData(CoinScanResult result, CoinScoringInput input) {
        if (input == null) {
            return;
        }
        if (result.getSymbol() == null) {
            result.setSymbol(input.getSymbol());
        }
        if (input.getTicker24h() != null) {
            result.setLastPrice(input.getTicker24h().getLastPrice());
            result.setPriceChange24hPct(input.getTicker24h().getPriceChangePercent());
            result.setQuoteVolume24h(input.getTicker24h().getQuoteVolume());
        }
        if (input.getBookTicker() != null) {
            result.setSpreadPct(input.getBookTicker().getSpreadPct());
        }
        if (input.getFuturesSnapshot() != null) {
            result.setFundingRate(input.getFuturesSnapshot().getFundingRate());
            result.setOpenInterest(input.getFuturesSnapshot().getOpenInterest());
        }
        if (input.getMarketRegimeResult() != null) {
            result.setMarketBreadthPct(input.getMarketRegimeResult().getMarketBreadthPct());
        }
    }

    private void logScored(CoinScanResult result) {
        log.info("COIN_SCORED symbol={} longScore={} shortScore={} directionBias={} classification={} riskLevel={}",
                result.getSymbol(), result.getLongScore(), result.getShortScore(), result.getDirectionBias(),
                result.getClassification(), result.getRiskLevel());
        if (result.getClassification() == CoinClassification.ELIMINATED) {
            log.info("COIN_ELIMINATED_BY_SCORE symbol={} reason={} longScore={} shortScore={}",
                    result.getSymbol(), result.getEliminatedReason(), result.getLongScore(), result.getShortScore());
        }
    }

    private MarketRegime marketRegime(MarketRegimeResult marketRegimeResult) {
        if (marketRegimeResult == null || marketRegimeResult.getMarketRegime() == null) {
            return MarketRegime.CHOP;
        }
        return marketRegimeResult.getMarketRegime();
    }

    private BigDecimal fundingRate(FuturesSnapshot futuresSnapshot) {
        return futuresSnapshot == null ? null : futuresSnapshot.getFundingRate();
    }

    private BigDecimal priceChangePercent(Ticker24h ticker24h) {
        return ticker24h == null ? null : ticker24h.getPriceChangePercent();
    }

    private boolean gt(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) > 0;
    }

    private boolean lt(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) < 0;
    }

    private boolean betweenInclusive(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private void addUnique(List<ReasonTag> tags, ReasonTag tag) {
        if (tags != null && tag != null && !tags.contains(tag)) {
            tags.add(tag);
        }
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private ScannerProperties.Scoring scoring() {
        if (scannerProperties.getScoring() == null) {
            scannerProperties.setScoring(new ScannerProperties.Scoring());
        }
        return scannerProperties.getScoring();
    }

    private ScannerProperties.Funding funding() {
        if (scannerProperties.getFunding() == null) {
            scannerProperties.setFunding(new ScannerProperties.Funding());
        }
        return scannerProperties.getFunding();
    }

    private ScannerProperties.Liquidity liquidity() {
        if (scannerProperties.getLiquidity() == null) {
            scannerProperties.setLiquidity(new ScannerProperties.Liquidity());
        }
        return scannerProperties.getLiquidity();
    }
}
