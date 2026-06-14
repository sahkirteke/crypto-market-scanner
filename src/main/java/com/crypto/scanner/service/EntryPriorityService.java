package com.crypto.scanner.service;

import com.crypto.common.enums.FourHourAlignment;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.EntrySignal;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntryPriorityService {
    private final ScannerProperties scannerProperties;

    public PriorityBreakdown calculate(PriorityInput input) {
        if (input == null) {
            return PriorityBreakdown.empty();
        }
        ScannerProperties.EntryPriority cfg = entryPriority();
        int scannerScore = intValue(input.scannerScore(), 0);
        int riskPenalty = switch (input.riskLevel() == null ? RiskLevel.MEDIUM : input.riskLevel()) {
            case LOW -> -intValue(cfg.getRiskPenaltyLow(), 0);
            case MEDIUM -> -intValue(cfg.getRiskPenaltyMedium(), 5);
            case HIGH -> -intValue(cfg.getRiskPenaltyHigh(), 15);
        };
        int spreadPenalty = spreadPenalty(input.spreadPct(), cfg);
        int fundingPenalty = fundingPenalty(input.side(), input.fundingRate(), cfg);
        int sidePenalty = sidePenalty(input, cfg);
        int fourHourPenalty = fourHourPenalty(input, cfg);
        int symbolCooldownPenalty = input.symbolCooldownPenaltyApplied() ? -intValue(cfg.getSymbolRecentStopPenalty(), 25) : 0;
        int volumeBonus = volumeBonus(input.volumeRatio1h(), input.volumeRatio4h(), cfg);
        int regimeBonus = marketRegimeBonus(input.side(), input.marketRegime(), cfg);
        int fourHourBonus = fourHourBonus(input, cfg);
        int finalScore = scannerScore + riskPenalty + spreadPenalty + fundingPenalty + sidePenalty + fourHourPenalty
                + symbolCooldownPenalty + volumeBonus + regimeBonus + fourHourBonus;
        return new PriorityBreakdown(
                finalScore,
                riskPenalty,
                spreadPenalty,
                fundingPenalty,
                sidePenalty,
                fourHourPenalty,
                symbolCooldownPenalty,
                volumeBonus,
                regimeBonus,
                fourHourBonus,
                fourHourAlignment(input.side(), input.close4h(), input.ema20_4h(), input.ema50_4h(), input.rsi14_4h(), input.macdHist_4h())
        );
    }

    public PriorityBreakdown calculate(EntryCandidate candidate, TechnicalSnapshot fourHour, TechnicalSnapshot btcFourHour, int recentStops) {
        return calculate(PriorityInput.from(candidate, fourHour, btcFourHour, recentStops >= intValue(entryPriority().getSymbolRecentStopCountThreshold(), 2)));
    }

    public PriorityBreakdown calculate(EntrySignal signal, TechnicalSnapshot btcFourHour, boolean cooldownPenaltyApplied) {
        return calculate(PriorityInput.from(signal, btcFourHour, cooldownPenaltyApplied));
    }

    public RiskLevel adjustedRiskLevel(PriorityInput input, PriorityBreakdown breakdown) {
        if (input == null) return RiskLevel.HIGH;
        RiskLevel base = input.riskLevel() == null ? RiskLevel.MEDIUM : input.riskLevel();
        if (input.marketRegime() == MarketRegime.PANIC) return RiskLevel.HIGH;
        boolean lowAllowed = true;
        if (input.side() == PositionSide.LONG) {
            lowAllowed = gt(input.close4h(), input.ema20_4h()) && ge(input.macdHist_4h(), BigDecimal.ZERO)
                    && (input.marketRegime() == MarketRegime.RISK_ON || input.marketRegime() == MarketRegime.CHOP);
        } else if (input.side() == PositionSide.SHORT) {
            lowAllowed = lt(input.close4h(), input.ema20_4h()) && le(input.macdHist_4h(), BigDecimal.ZERO)
                    && input.marketRegime() == MarketRegime.RISK_OFF && !gt(input.btcClose4h(), input.btcEma20_4h());
        } else {
            lowAllowed = false;
        }
        lowAllowed = lowAllowed && gt(input.volumeRatio1h(), new BigDecimal("1.1"))
                && breakdown != null && breakdown.finalEntryPriorityScore() >= 75;
        if (lowAllowed && base == RiskLevel.LOW) return RiskLevel.LOW;
        if (base == RiskLevel.HIGH || (breakdown != null && breakdown.finalEntryPriorityScore() < 55)) return RiskLevel.HIGH;
        return RiskLevel.MEDIUM;
    }

    public Map<String, Object> configSnapshot() {
        ScannerProperties.EntryPriority c = entryPriority();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("minEntryPriorityScore", scannerProperties.getPaper().getMinEntryPriorityScore());
        m.put("riskPenaltyMedium", c.getRiskPenaltyMedium());
        m.put("riskPenaltyHigh", c.getRiskPenaltyHigh());
        m.put("symbolRecentStopPenalty", c.getSymbolRecentStopPenalty());
        return m;
    }

    public FourHourAlignment fourHourAlignment(PositionSide side, BigDecimal close, BigDecimal ema20, BigDecimal ema50, BigDecimal rsi, BigDecimal macd) {
        if (side == PositionSide.LONG) {
            if (lt(close, ema20)) return FourHourAlignment.AGAINST_4H_TREND;
            if (gt(close, ema20) && gt(ema20, ema50) && gt(macd, BigDecimal.ZERO) && between(rsi, 50, 70)) return FourHourAlignment.STRONG_ALIGNED;
            if (gt(close, ema20) && gt(macd, BigDecimal.ZERO)) return FourHourAlignment.ALIGNED;
        }
        if (side == PositionSide.SHORT) {
            if (gt(close, ema20)) return FourHourAlignment.AGAINST_4H_TREND;
            if (lt(close, ema20) && lt(ema20, ema50) && lt(macd, BigDecimal.ZERO) && between(rsi, 30, 50)) return FourHourAlignment.STRONG_ALIGNED;
            if (lt(close, ema20) && lt(macd, BigDecimal.ZERO)) return FourHourAlignment.ALIGNED;
        }
        return FourHourAlignment.NEUTRAL_4H;
    }

    private int spreadPenalty(BigDecimal spreadPct, ScannerProperties.EntryPriority cfg) {
        if (spreadPct == null || le(spreadPct, cfg.getSpreadPenaltyLowThreshold())) return 0;
        if (le(spreadPct, cfg.getSpreadPenaltyMidThreshold())) return -intValue(cfg.getSpreadPenaltyMid(), 3);
        return -intValue(cfg.getSpreadPenaltyHigh(), 6);
    }

    private int fundingPenalty(PositionSide side, BigDecimal rate, ScannerProperties.EntryPriority cfg) {
        if (side == null || rate == null) return 0;
        ScannerProperties.Funding f = scannerProperties.getFunding();
        if (side == PositionSide.LONG && ge(rate, f.getDangerPositive())) return -intValue(cfg.getFundingDangerPenalty(), 10);
        if (side == PositionSide.LONG && ge(rate, f.getWarningPositive())) return -intValue(cfg.getFundingWarningPenalty(), 5);
        if (side == PositionSide.SHORT && le(rate, f.getDangerNegative())) return -intValue(cfg.getFundingDangerPenalty(), 10);
        if (side == PositionSide.SHORT && le(rate, f.getWarningNegative())) return -intValue(cfg.getFundingWarningPenalty(), 5);
        return 0;
    }

    private int volumeBonus(BigDecimal v1, BigDecimal v4, ScannerProperties.EntryPriority cfg) {
        BigDecimal strong = cfg.getVolumeBonusStrongThreshold();
        if (gt(v1, strong) && gt(v4, BigDecimal.ONE)) return intValue(cfg.getVolumeBonusStrongWith4hConfirmation(), 8);
        if (gt(v1, strong)) return intValue(cfg.getVolumeBonusStrong(), 5);
        if (gt(v1, BigDecimal.ONE)) return intValue(cfg.getVolumeBonusNormal(), 2);
        return 0;
    }

    private int marketRegimeBonus(PositionSide side, MarketRegime regime, ScannerProperties.EntryPriority cfg) {
        if (side == null || regime == null) return 0;
        if (regime == MarketRegime.CHOP) return -intValue(cfg.getChopPenalty(), 5);
        if (side == PositionSide.LONG && regime == MarketRegime.RISK_ON) return intValue(cfg.getRegimeAlignmentBonus(), 5);
        if (side == PositionSide.LONG && regime == MarketRegime.RISK_OFF) return -intValue(cfg.getRegimeOppositePenalty(), 10);
        if (side == PositionSide.SHORT && regime == MarketRegime.RISK_OFF) return intValue(cfg.getRegimeAlignmentBonus(), 5);
        if (side == PositionSide.SHORT && regime == MarketRegime.RISK_ON) return -20;
        return 0;
    }

    private int fourHourBonus(PriorityInput i, ScannerProperties.EntryPriority cfg) {
        int bonus = 0;
        if (i.side() == PositionSide.LONG) {
            if (gt(i.close4h(), i.ema20_4h())) bonus += 5;
            if (gt(i.ema20_4h(), i.ema50_4h())) bonus += 5;
            if (gt(i.ema50_4h(), i.ema200_4h())) bonus += 5;
            if (gt(i.macdHist_4h(), BigDecimal.ZERO)) bonus += 5;
            if (between(i.rsi14_4h(), 50, 70)) bonus += 5;
        } else if (i.side() == PositionSide.SHORT) {
            if (lt(i.close4h(), i.ema20_4h())) bonus += 5;
            if (lt(i.ema20_4h(), i.ema50_4h())) bonus += 5;
            if (lt(i.ema50_4h(), i.ema200_4h())) bonus += 5;
            if (lt(i.macdHist_4h(), BigDecimal.ZERO)) bonus += 5;
            if (between(i.rsi14_4h(), 30, 50)) bonus += 5;
        }
        return bonus;
    }

    private int fourHourPenalty(PriorityInput i, ScannerProperties.EntryPriority cfg) {
        int penalty = 0;
        if (i.side() == PositionSide.LONG) {
            if (lt(i.close4h(), i.ema20_4h())) penalty -= intValue(cfg.getFourHourAgainstTrendPenaltyLong(), 15);
            if (lt(i.macdHist_4h(), BigDecimal.ZERO)) penalty -= intValue(cfg.getFourHourMacdOppositePenalty(), 10);
            if (gt(i.rsi14_4h(), new BigDecimal("75"))) penalty -= intValue(cfg.getFourHourRsiLateLongPenalty(), 10);
            if (lt(i.rsi14_4h(), new BigDecimal("45"))) penalty -= 5;
        } else if (i.side() == PositionSide.SHORT) {
            if (gt(i.close4h(), i.ema20_4h())) penalty -= intValue(cfg.getFourHourAgainstTrendPenaltyShort(), 20);
            if (gt(i.macdHist_4h(), BigDecimal.ZERO)) penalty -= intValue(cfg.getFourHourMacdOppositePenalty(), 10);
            if (lt(i.rsi14_4h(), new BigDecimal("30"))) penalty -= intValue(cfg.getFourHourRsiLateShortPenalty(), 15);
            if (gt(i.rsi14_4h(), new BigDecimal("55"))) penalty -= 5;
        }
        return penalty;
    }

    private int sidePenalty(PriorityInput i, ScannerProperties.EntryPriority cfg) {
        if (i.side() != PositionSide.SHORT) return 0;
        int penalty = 0;
        ScannerProperties.Funding f = scannerProperties.getFunding();
        if (i.marketRegime() != MarketRegime.RISK_OFF) penalty -= intValue(cfg.getShortNotRiskOffPenalty(), 20);
        if (gt(i.btcClose4h(), i.btcEma20_4h())) penalty -= intValue(cfg.getShortBtc4hUpPenalty(), 15);
        if (gt(i.close4h(), i.ema20_4h())) penalty -= intValue(cfg.getShortSymbol4hUpPenalty(), 20);
        if (gt(i.macdHist_4h(), BigDecimal.ZERO)) penalty -= intValue(cfg.getShortSymbolMacdPositivePenalty(), 10);
        if (lt(i.rsi14_1h(), new BigDecimal("35"))) penalty -= intValue(cfg.getShortRsiLowPenalty(), 10);
        if (lt(i.priceChange24hPct(), new BigDecimal("-10"))) penalty -= intValue(cfg.getShortPriceDumpPenalty(), 10);
        if (lt(i.fundingRate(), f.getWarningNegative())) penalty -= intValue(cfg.getShortNegativeFundingPenalty(), 10);
        return penalty;
    }

    private ScannerProperties.EntryPriority entryPriority() { return scannerProperties.getEntryPriority(); }
    private boolean gt(BigDecimal l, BigDecimal r) { return l != null && r != null && l.compareTo(r) > 0; }
    private boolean ge(BigDecimal l, BigDecimal r) { return l != null && r != null && l.compareTo(r) >= 0; }
    private boolean lt(BigDecimal l, BigDecimal r) { return l != null && r != null && l.compareTo(r) < 0; }
    private boolean le(BigDecimal l, BigDecimal r) { return l != null && r != null && l.compareTo(r) <= 0; }
    private boolean between(BigDecimal v, int min, int max) { return v != null && v.compareTo(BigDecimal.valueOf(min)) >= 0 && v.compareTo(BigDecimal.valueOf(max)) <= 0; }
    private int intValue(Integer value, int fallback) { return value == null ? fallback : value; }

    public record PriorityInput(PositionSide side, Integer scannerScore, RiskLevel riskLevel, MarketRegime marketRegime,
                                BigDecimal spreadPct, BigDecimal fundingRate, BigDecimal priceChange24hPct,
                                BigDecimal rsi14_1h, BigDecimal volumeRatio1h,
                                BigDecimal close4h, BigDecimal ema20_4h, BigDecimal ema50_4h, BigDecimal ema200_4h,
                                BigDecimal rsi14_4h, BigDecimal macdHist_4h, BigDecimal volumeRatio4h,
                                BigDecimal btcClose4h, BigDecimal btcEma20_4h, boolean symbolCooldownPenaltyApplied) {
        static PriorityInput from(EntryCandidate c, TechnicalSnapshot four, TechnicalSnapshot btc, boolean cooldown) {
            return new PriorityInput(c.getSide(), c.getScore(), c.getRiskLevel(), c.getMarketRegime(), c.getSpreadPct(), c.getFundingRate(), c.getPriceChange24hPct(),
                    c.getRsi14_1h(), c.getVolumeRatio_1h(), val(four, "close"), val(four, "ema20"), val(four, "ema50"), val(four, "ema200"), val(four, "rsi"), val(four, "macd"), val(four, "volume"), val(btc, "close"), val(btc, "ema20"), cooldown);
        }
        static PriorityInput from(EntrySignal s, TechnicalSnapshot btc, boolean cooldown) {
            return new PriorityInput(s.getSide(), s.getScannerScore(), s.getRiskLevel(), s.getMarketRegime(), s.getSpreadPct(), s.getFundingRate(), s.getPriceChange24hPct(),
                    s.getRsi14_1h(), s.getVolumeRatio_1h(), s.getClose4h(), s.getEma20_4h(), s.getEma50_4h(), s.getEma200_4h(), s.getRsi14_4h(), s.getMacdHist_4h(), s.getVolumeRatio_4h(), val(btc, "close"), val(btc, "ema20"), cooldown);
        }
        private static BigDecimal val(TechnicalSnapshot t, String f) {
            if (t == null) return null;
            return switch (f) { case "close" -> t.getClose(); case "ema20" -> t.getEma20(); case "ema50" -> t.getEma50(); case "ema200" -> t.getEma200(); case "rsi" -> t.getRsi14(); case "macd" -> t.getMacdHist(); case "volume" -> t.getVolumeRatio(); default -> null; };
        }
    }

    public record PriorityBreakdown(int finalEntryPriorityScore, int riskPenalty, int spreadPenalty, int fundingPenalty,
                                    int sidePenalty, int fourHourPenalty, int symbolCooldownPenalty,
                                    int volumeConfirmationBonus, int marketRegimeAlignmentBonus,
                                    int fourHourAlignmentBonus, FourHourAlignment fourHourAlignment) {
        static PriorityBreakdown empty() { return new PriorityBreakdown(0,0,0,0,0,0,0,0,0,0, FourHourAlignment.NEUTRAL_4H); }
        public List<String> warnings() {
            return List.of();
        }
    }
}
