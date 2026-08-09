package com.crypto.laplace.service;

import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LaplaceEntryDecisionService {
    private static final int EMA20 = 20, EMA50 = 50, TWELVE_BARS = 12;
    private final FiveMinuteKlineService klines;
    private final LaplaceVolumeProfileCalculator volumeProfile;
    private final LaplaceStrategyProperties properties;

    public LaplaceEntryDecision evaluate(LaplaceSignalResult signal, PositionSide effectiveSide) {
        var p = properties.getLaplace();
        List<LaplaceEntryRejectionReason> reasons = new ArrayList<>();
        List<Kline> history;
        try { history = klines.loadLastClosedThrough(signal.symbol(), signal.signalCandleCloseTime(), p.getFiveMinuteHistoryLimit()); }
        catch (RuntimeException e) { return unavailable(effectiveSide, signal, p, LaplaceEntryRejectionReason.FIVE_MINUTE_HISTORY_UNAVAILABLE); }
        int window = p.getVolumeProfileWindowBars();
        if (history.size() < Math.max(p.getFiveMinuteHistoryLimit(), window + TWELVE_BARS)) {
            return unavailable(effectiveSide, signal, p, LaplaceEntryRejectionReason.FIVE_MINUTE_HISTORY_UNAVAILABLE);
        }
        Instant expectedLastClose = klines.lastExpectedClosedFiveMinuteCandle(signal.signalCandleCloseTime());
        if (!history.getLast().getCloseTime().equals(expectedLastClose)) {
            return unavailable(effectiveSide, signal, p, LaplaceEntryRejectionReason.FIVE_MINUTE_HISTORY_UNAVAILABLE);
        }
        for (int i = 1; i < history.size(); i++) {
            long seconds = Duration.between(history.get(i - 1).getCloseTime(), history.get(i).getCloseTime()).getSeconds();
            if (seconds > 300 || seconds <= 0) reasons.add(LaplaceEntryRejectionReason.FIVE_MINUTE_HISTORY_GAP);
        }
        double[] e20 = ema(history, EMA20), e50 = ema(history, EMA50);
        int last = history.size() - 1;
        List<Kline> vpCandles = history.subList(history.size() - window, history.size());
        double current = history.get(last).getClose().doubleValue();
        double lowest = vpCandles.stream().mapToDouble(c -> c.getLow().doubleValue()).min().orElse(Double.NaN);
        double atrPct = signal.currentAtr14() / signal.signalCandleClose() * 100d;
        double distance = (current - lowest) / current * 100d;
        double emaGap = (e20[last] - e50[last]) / current * 100d;
        List<Kline> twoHours = history.subList(history.size() - 24, history.size());
        double lowest2h = twoHours.stream().mapToDouble(c -> c.getLow().doubleValue()).min().orElse(Double.NaN);
        double highest2h = twoHours.stream().mapToDouble(c -> c.getHigh().doubleValue()).max().orElse(Double.NaN);
        double rangePosition2h = highest2h == lowest2h ? 0d : (current - lowest2h) / (highest2h - lowest2h);
        double lastHourVolume = history.subList(last - 11, last + 1).stream().map(Kline::getQuoteAssetVolume).mapToDouble(java.math.BigDecimal::doubleValue).sum();
        double previousHourVolume = history.subList(last - 23, last - 11).stream().map(Kline::getQuoteAssetVolume).mapToDouble(java.math.BigDecimal::doubleValue).sum();
        double quoteVolumeAccel1h = previousHourVolume == 0d ? Double.POSITIVE_INFINITY : lastHourVolume / previousHourVolume;
        double emaRise = (e50[last] / e50[last - TWELVE_BARS] - 1d) * 100d;
        double poc = Double.NaN, vpGap = Double.NaN;
        try {
            poc = volumeProfile.calculatePoc(vpCandles, p.getVolumeProfileBins());
            vpGap = (effectiveSide == PositionSide.LONG ? poc - current : current - poc) / current * 100d;
        } catch (RuntimeException e) { reasons.add(LaplaceEntryRejectionReason.KURAL5_VOLUME_PROFILE); }
        if (effectiveSide == PositionSide.LONG && atrPct >= p.getLongAtrThreshold().doubleValue()
                && distance >= p.getLongDistanceFromLowThresholdPct().doubleValue()) reasons.add(LaplaceEntryRejectionReason.KURAL5_LONG_ATR_DISTANCE);
        if (effectiveSide == PositionSide.SHORT && emaGap >= p.getShortEmaGapThresholdPct().doubleValue()
                && emaRise >= p.getShortEma50RiseThresholdPct().doubleValue()) reasons.add(LaplaceEntryRejectionReason.KURAL5_SHORT_EMA_TREND);
        if (effectiveSide == PositionSide.SHORT && signal.currentNormalizedSlope() >= p.getShortRawSlopeThreshold().doubleValue()) reasons.add(LaplaceEntryRejectionReason.KURAL5_SHORT_STRONG_RAW_SLOPE);
        if (!(vpGap >= p.getVolumeProfileMinGapPct().doubleValue()) && !reasons.contains(LaplaceEntryRejectionReason.KURAL5_VOLUME_PROFILE)) reasons.add(LaplaceEntryRejectionReason.KURAL5_VOLUME_PROFILE);
        java.time.ZonedDateTime utc = signal.signalCandleOpenTime().atZone(java.time.ZoneOffset.UTC);
        if (utc.getHour() == 0 && utc.getMinute() == 0) reasons.add(LaplaceEntryRejectionReason.BLOCK_5C_0000_UTC);
        if (effectiveSide == PositionSide.LONG && utc.getHour() == 11 && utc.getMinute() == 0) reasons.add(LaplaceEntryRejectionReason.BLOCK_5C_1100_LONG);
        if (effectiveSide == PositionSide.LONG && rangePosition2h >= .30d && quoteVolumeAccel1h <= .90d) reasons.add(LaplaceEntryRejectionReason.BLOCK_5C_LONG_WEAK_BOUNCE);
        if (effectiveSide == PositionSide.SHORT && emaGap >= .20d && quoteVolumeAccel1h <= .90d) reasons.add(LaplaceEntryRejectionReason.BLOCK_5C_SHORT_EMA_VOLUME);
        boolean allowed = !p.isKural5Enabled() || reasons.isEmpty();
        return new LaplaceEntryDecision(allowed, effectiveSide, atrPct, lowest, distance, current, e20[last], e50[last], e50[last-TWELVE_BARS], emaGap, emaRise,
                signal.currentNormalizedSlope(), window, p.getVolumeProfileBins(), poc, vpGap, rangePosition2h, quoteVolumeAccel1h, emaGap, reasons);
    }

    private static double[] ema(List<Kline> candles, int span) {
        double alpha = 2d / (span + 1d), previous = candles.getFirst().getClose().doubleValue();
        double[] result = new double[candles.size()]; result[0] = previous;
        for (int i=1;i<candles.size();i++) result[i] = previous = alpha*candles.get(i).getClose().doubleValue()+(1-alpha)*previous;
        return result;
    }
    private static LaplaceEntryDecision unavailable(PositionSide side, LaplaceSignalResult s, LaplaceStrategyProperties.Laplace p, LaplaceEntryRejectionReason reason) {
        double n=Double.NaN;
        return new LaplaceEntryDecision(false,side,n,n,n,n,n,n,n,n,n,s.currentNormalizedSlope(),p.getVolumeProfileWindowBars(),p.getVolumeProfileBins(),n,n,n,n,n,List.of(reason));
    }
}
