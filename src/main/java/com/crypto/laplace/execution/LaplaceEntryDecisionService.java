package com.crypto.laplace.execution;

import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.common.enums.PositionSide;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

@Component
public class LaplaceEntryDecisionService {
    public static final double SLOPE_STRENGTH_THRESHOLD = 0.04;
    private final LaplaceRiskyEntryFilter riskyEntryFilter;

    public LaplaceEntryDecisionService(LaplaceRiskyEntryFilter riskyEntryFilter) {
        this.riskyEntryFilter = riskyEntryFilter;
    }

    public LaplaceEntryDecision decide(LaplaceSignalResult signal) {
        boolean rawLongRule = signal.entrySignal() == LaplaceSignal.LONG
                && signal.previousRawTakerImbalance() >= 0.05 && signal.ret120mPct() <= 0.30;
        boolean rawShortRule = signal.entrySignal() == LaplaceSignal.SHORT
                && signal.previous30mRawReturnPct() >= 0.90 && signal.aligned120mReturnPct() <= 2.00;
        boolean signalInverted = !(rawLongRule || rawShortRule);
        PositionSide effectiveSide = signal.entrySignal() == LaplaceSignal.NONE ? null
                : signal.entrySignal() == LaplaceSignal.LONG
                    ? (signalInverted ? PositionSide.SHORT : PositionSide.LONG)
                    : (signalInverted ? PositionSide.LONG : PositionSide.SHORT);
        boolean riskyEntry = riskyEntryFilter.isRisky(signal);
        boolean strongConfirmedSignal = Math.abs(signal.currentNormalizedSlope()) >= SLOPE_STRENGTH_THRESHOLD
                && Math.abs(signal.previousNormalizedSlope()) >= SLOPE_STRENGTH_THRESHOLD;
        EnumSet<LaplaceEntrySkipReason> reasons = EnumSet.noneOf(LaplaceEntrySkipReason.class);
        if (riskyEntry) reasons.add(LaplaceEntrySkipReason.RISKY_ENTRY);
        boolean weakCurrent=Math.abs(signal.currentNormalizedSlope()) < SLOPE_STRENGTH_THRESHOLD;
        boolean weakPrevious=Math.abs(signal.previousNormalizedSlope()) < SLOPE_STRENGTH_THRESHOLD;
        if(weakCurrent&&weakPrevious) reasons.add(LaplaceEntrySkipReason.WEAK_BOTH_SLOPES);
        else if(weakCurrent) reasons.add(LaplaceEntrySkipReason.WEAK_CURRENT_SLOPE);
        else if(weakPrevious) reasons.add(LaplaceEntrySkipReason.WEAK_PREVIOUS_SLOPE);
        return new LaplaceEntryDecision(reasons.isEmpty(),riskyEntry,strongConfirmedSignal,rawLongRule,rawShortRule,
                signalInverted,effectiveSide,reasons.stream().toList());
    }
}
