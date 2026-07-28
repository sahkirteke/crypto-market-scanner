package com.crypto.laplace.model;

import java.time.Instant;
import java.util.List;

public record LaplaceSignalResult(
 String strategyName, String strategyVersion, String symbol, String timeframe, String kernel, int bandwidth,
 String source, boolean repaint, Instant signalCandleOpenTime, Instant signalCandleCloseTime, double signalCandleClose,
 double regressionCurrent, double regressionPrevious, double regressionTwoBarsAgo, double currentSlope,
 double previousSlope, double currentAtr14, double previousAtr14, double atrPercentage,
 double previousRawTakerImbalance, double currentNormalizedSlope,
 double previousNormalizedSlope, double entryThreshold, double reversalThreshold, int confirmationBars,
 LaplaceSignal entrySignal, LaplaceSignal strongReversalSignal, StartupState startupState,
 int postStartupClosedBarCount, boolean eligibleForExecution, List<RejectionReason> rejectionReasons) {
 public LaplaceSignalResult { rejectionReasons = List.copyOf(rejectionReasons); }
}
