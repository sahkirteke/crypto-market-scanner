package com.crypto.laplace.execution;

import com.crypto.laplace.model.LaplaceSignalResult;
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
        boolean riskyEntry = riskyEntryFilter.isRisky(signal);
        boolean strongConfirmedSignal = Math.abs(signal.currentNormalizedSlope()) >= SLOPE_STRENGTH_THRESHOLD
                && Math.abs(signal.previousNormalizedSlope()) >= SLOPE_STRENGTH_THRESHOLD;
        EnumSet<LaplaceEntrySkipReason> reasons = EnumSet.noneOf(LaplaceEntrySkipReason.class);
        if (riskyEntry) reasons.add(LaplaceEntrySkipReason.RISKY_ENTRY_FILTER);
        if (!strongConfirmedSignal) reasons.add(LaplaceEntrySkipReason.SLOPE_STRENGTH_FILTER);
        return new LaplaceEntryDecision(reasons.isEmpty(), riskyEntry, strongConfirmedSignal,
                reasons.stream().toList());
    }
}
