package com.crypto.laplace.model;

import com.crypto.common.enums.PositionSide;
import java.util.List;

public record LaplaceEntryDecision(
        boolean allowed, PositionSide effectiveExecutionSide, double atrPercentage30m,
        double lowestLow24, double distanceFromLowestLow24Pct, double current5mClose,
        double ema20, double ema50, double ema50TwelveBarsAgo, double ema20AboveEma50Pct,
        double ema50Rise60mPct, double currentNormalizedSlope, int volumeProfileWindowBars,
        int volumeProfileBins, double volumeProfilePoc, double volumeProfileGapPct,
        double rangePosition2h, double quoteVolumeAccel1h, double emaGapPct,
        double last30mReturnPct, double previous30mReturnPct, double momentumDeteriorationPct,
        int green5mCandleCountLast30m, boolean longFallingKnifeContinuation,
        List<LaplaceEntryRejectionReason> rejectionReasons) {
    public LaplaceEntryDecision {
        rejectionReasons = List.copyOf(rejectionReasons);
    }
}
