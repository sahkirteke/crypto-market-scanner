package com.crypto.scanner.service;

import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.V20SignalResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class V20SignalService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final ScannerProperties properties;

    public V20SignalService(ScannerProperties properties) { this.properties = properties; }

    public V20SignalResult evaluateLong(TechnicalSnapshot fourHour, TechnicalSnapshot oneHour, BigDecimal fundingRate,
                                        List<BigDecimal> fundingRates, Boolean qualityPass, Boolean qualityFail) {
        ScannerProperties.V20 v20 = properties.getV20();
        List<String> reasons = new ArrayList<>();
        if (!hasRequired(fourHour, oneHour, fundingRate, fundingRates, reasons)) return result(false, false, 0, reasons);
        BigDecimal fundingMa3 = fundingMa3(fundingRates);
        boolean base = between(fourHour.getRsi14(), v20.getLong().getRsiMin(), v20.getLong().getRsiMax())
                && between(fourHour.getAdx14(), v20.getLong().getAdxMin(), v20.getLong().getAdxMax())
                && between(fourHour.getAtrPct(), v20.getLong().getAtrPctMin(), v20.getLong().getAtrPctMax())
                && between(fourHour.getEma20Ema50CompPct(), v20.getLong().getEma20Ema50CompPctMin(), v20.getLong().getEma20Ema50CompPctMax())
                && between(fourHour.getCloseEma20DistPct(), v20.getLong().getCloseEma20DistPctMin(), v20.getLong().getCloseEma20DistPctMax())
                && between(fourHour.getDistFromLow20Pct(), v20.getLong().getDistFromLow20BaseMinPct(), v20.getLong().getDistFromLow20BaseMaxPct())
                && gt(fourHour.getDiDiff(), v20.getLong().getDiDiffBaseMinExclusive())
                && gt(fourHour.getTakerBuyRatio(), v20.getLong().getFourHourTakerBuyMinExclusive())
                && gt(fourHour.getClosePosition(), v20.getLong().getClosePositionMinExclusive())
                && lt(fourHour.getVolumeRatio(), v20.getLong().getVolumeRatio20Max())
                && le(fourHour.getRangePct(), v20.getLong().getRangePctMax())
                && (lt(fundingRate, v20.getLong().getFundingNegativeThreshold()) || gt(fundingRate, v20.getLong().getFundingPositiveThreshold()))
                && gt(oneHour.getTakerBuyRatio(), v20.getLong().getOneHourTakerBuyMinExclusive());
        boolean entry = between(fourHour.getDistFromLow20Pct(), v20.getLong().getDistFromLow20EntryMinPct(), v20.getLong().getDistFromLow20EntryMaxPct())
                && ge(fourHour.getDiDiff(), v20.getLong().getDiDiffEntryMin());
        int score = score(PositionSide.LONG, fourHour, oneHour, fundingMa3, qualityPass, qualityFail, reasons);
        return result(base, entry && score >= v20.getLong().getMinSignalScore(), score, reasons);
    }

    public V20SignalResult evaluateShort(TechnicalSnapshot fourHour, TechnicalSnapshot oneHour, BigDecimal fundingRate,
                                         List<BigDecimal> fundingRates, Boolean qualityPass, Boolean qualityFail) {
        ScannerProperties.V20 v20 = properties.getV20();
        List<String> reasons = new ArrayList<>();
        if (!hasRequired(fourHour, oneHour, fundingRate, fundingRates, reasons)) return result(false, false, 0, reasons);
        BigDecimal fundingMa3 = fundingMa3(fundingRates);
        boolean base = between(fourHour.getRsi14(), v20.getShort().getRsiMin(), v20.getShort().getRsiMax())
                && between(fourHour.getAdx14(), v20.getShort().getAdxMin(), v20.getShort().getAdxMax())
                && between(fourHour.getAtrPct(), v20.getShort().getAtrPctMin(), v20.getShort().getAtrPctMax())
                && between(fourHour.getEma20Ema50CompPct(), v20.getShort().getEma20Ema50CompPctMin(), v20.getShort().getEma20Ema50CompPctMax())
                && between(fourHour.getCloseEma20DistPct(), v20.getShort().getCloseEma20DistPctMin(), v20.getShort().getCloseEma20DistPctMax())
                && between(fourHour.getDistFromHigh20Pct(), v20.getShort().getDistFromHigh20BaseMinPct(), v20.getShort().getDistFromHigh20BaseMaxPct())
                && lt(fourHour.getDiDiff(), v20.getShort().getDiDiffBaseMaxExclusive())
                && lt(fourHour.getTakerBuyRatio(), v20.getShort().getFourHourTakerBuyMaxExclusive())
                && lt(fourHour.getClosePosition(), v20.getShort().getClosePositionMaxExclusive())
                && lt(fourHour.getVolumeRatio(), v20.getShort().getVolumeRatio20Max())
                && le(fourHour.getRangePct(), v20.getShort().getRangePctMax())
                && (lt(fundingRate, v20.getShort().getFundingNegativeThreshold()) || gt(fundingRate, v20.getShort().getFundingPositiveThreshold()))
                && lt(oneHour.getTakerBuyRatio(), v20.getShort().getOneHourTakerBuyMaxExclusive());
        boolean entry = between(fourHour.getDistFromHigh20Pct(), v20.getShort().getDistFromHigh20EntryMinPct(), v20.getShort().getDistFromHigh20EntryMaxPct())
                && le(fourHour.getDiDiff(), v20.getShort().getDiDiffEntryMax())
                && ge(fundingMa3, v20.getShort().getFundingMa3Min())
                && le(fourHour.getTakerBuyRatio(), v20.getShort().getFourHourTakerBuyEntryMax());
        int score = score(PositionSide.SHORT, fourHour, oneHour, fundingMa3, qualityPass, qualityFail, reasons);
        return result(base, entry && score >= v20.getShort().getMinSignalScore(), score, reasons);
    }

    public BigDecimal fundingMa3(List<BigDecimal> rates) {
        if (rates == null) return null;
        List<BigDecimal> safeRates = rates.stream().filter(java.util.Objects::nonNull).toList();
        if (safeRates.size() < 3) return null;
        return safeRates.subList(safeRates.size() - 3, safeRates.size()).stream()
                .reduce(ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(3), 12, RoundingMode.HALF_UP);
    }

    private int score(PositionSide side, TechnicalSnapshot f, TechnicalSnapshot h, BigDecimal fundingMa3, Boolean qualityPass, Boolean qualityFail, List<String> reasons) {
        ScannerProperties.V20Score s = properties.getV20().getScore(); int score = 0;
        if (side == PositionSide.LONG) {
            if (ge(f.getTakerBuyRatio(), new BigDecimal("0.50"))) { score++; reasons.add("4H_TAKER_BUY_GE_0_50"); }
            if (between(f.getDistFromLow20Pct(), s.getLong().getDistLow20IdealMinPct(), s.getLong().getDistLow20IdealMaxPct())) { score++; reasons.add("DIST_LOW20_IDEAL"); }
            if (between(f.getClosePosition(), s.getLong().getClosePositionIdealMin(), s.getLong().getClosePositionIdealMax())) { score++; reasons.add("CLOSE_POSITION_IDEAL"); }
            if (between(f.getBbPosition(), s.getLong().getBbPositionIdealMin(), s.getLong().getBbPositionIdealMax())) { score++; reasons.add("BB_POSITION_IDEAL"); }
            if (lt(fundingMa3, s.getLong().getFundingMa3NegativeSupport()) || gt(fundingMa3, s.getLong().getFundingMa3PositiveSupport())) { score++; reasons.add("FUNDING_MA3_SUPPORT"); }
            if (ge(h.getTakerBuyRatio(), s.getLong().getOneHourStrongTakerMin())) { score++; reasons.add("ONE_H_STRONG_TAKER"); }
            if (between(h.getClosePosition(), s.getLong().getOneHourClosePositionIdealMin(), s.getLong().getOneHourClosePositionIdealMax())) { score++; reasons.add("ONE_H_CLOSE_POSITION_IDEAL"); }
        } else {
            if (le(f.getTakerBuyRatio(), new BigDecimal("0.50"))) { score++; reasons.add("4H_TAKER_BUY_LE_0_50"); }
            if (between(f.getDistFromHigh20Pct(), s.getShort().getDistHigh20IdealMinPct(), s.getShort().getDistHigh20IdealMaxPct())) { score++; reasons.add("DIST_HIGH20_IDEAL"); }
            if (between(f.getClosePosition(), s.getShort().getClosePositionIdealMin(), s.getShort().getClosePositionIdealMax())) { score++; reasons.add("CLOSE_POSITION_IDEAL"); }
            if (between(f.getBbPosition(), s.getShort().getBbPositionIdealMin(), s.getShort().getBbPositionIdealMax())) { score++; reasons.add("BB_POSITION_IDEAL"); }
            if (gt(fundingMa3, s.getShort().getFundingMa3PositiveSupport()) || lt(fundingMa3, s.getShort().getFundingMa3NegativeSupport())) { score++; reasons.add("FUNDING_MA3_SUPPORT"); }
            if (le(h.getTakerBuyRatio(), s.getShort().getOneHourStrongTakerBuyMax())) { score++; reasons.add("ONE_H_STRONG_TAKER_SELL"); }
            if (between(h.getClosePosition(), s.getShort().getOneHourClosePositionIdealMin(), s.getShort().getOneHourClosePositionIdealMax())) { score++; reasons.add("ONE_H_CLOSE_POSITION_IDEAL"); }
        }
        if (between(f.getAdx14(), s.getAdxIdealMin(), s.getAdxIdealMax())) { score++; reasons.add("ADX_IDEAL_18_32"); }
        if (between(f.getAtrPct(), s.getAtrIdealMin(), s.getAtrIdealMax())) { score++; reasons.add("ATR_IDEAL"); }
        if (between(f.getVolumeRatio(), s.getVolumeRatioIdealMin(), s.getVolumeRatioIdealMax())) { score++; reasons.add("VOLUME_RATIO_IDEAL"); }
        if (le(f.getRangePct(), s.getRangePctLowMax())) { score++; reasons.add("RANGE_PCT_LOW"); }
        if (Boolean.TRUE.equals(qualityPass)) { score += 2; reasons.add("QUALITY_PASS"); }
        if (Boolean.TRUE.equals(qualityFail)) { score -= 2; reasons.add("QUALITY_FAIL"); }
        return score;
    }

    private boolean hasRequired(TechnicalSnapshot f, TechnicalSnapshot h, BigDecimal fundingRate, List<BigDecimal> fundingRates, List<String> reasons) {
        boolean ok = f != null && h != null && fundingRate != null && fundingMa3(fundingRates) != null
                && f.getTakerBuyRatio() != null && h.getTakerBuyRatio() != null && f.getVolumeRatio() != null;
        if (!ok) reasons.add("DATA_NOT_READY");
        return ok;
    }
    private V20SignalResult result(boolean base, boolean entry, int score, List<String> reasons) { return V20SignalResult.builder().baseSignalPass(base).entryFiltersPass(entry).scorePass(entry).signalScore(score).reasons(reasons).build(); }
    private boolean between(BigDecimal v, BigDecimal min, BigDecimal max) { return ge(v, min) && le(v, max); }
    private boolean gt(BigDecimal v, BigDecimal t) { return v != null && t != null && v.compareTo(t) > 0; }
    private boolean ge(BigDecimal v, BigDecimal t) { return v != null && t != null && v.compareTo(t) >= 0; }
    private boolean lt(BigDecimal v, BigDecimal t) { return v != null && t != null && v.compareTo(t) < 0; }
    private boolean le(BigDecimal v, BigDecimal t) { return v != null && t != null && v.compareTo(t) <= 0; }
}
