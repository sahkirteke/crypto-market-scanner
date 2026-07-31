package com.crypto.laplace.model;

import java.time.Instant;
import java.util.List;

public record LaplaceSignalResult(
 String strategyName, String strategyVersion, String symbol, String timeframe, String kernel, int bandwidth,
 String source, boolean repaint, Instant signalCandleOpenTime, Instant signalCandleCloseTime, double signalCandleClose,
 double regressionCurrent, double regressionPrevious, double regressionTwoBarsAgo, double currentSlope,
 double previousSlope, double currentAtr14, double previousAtr14, double atrPercentage,
 double previousRawTakerImbalance, double ret120mPct, double previous30mRawReturnPct,
 double aligned120mReturnPct, double currentNormalizedSlope,
 double previousNormalizedSlope, double entryThreshold, double reversalThreshold, int confirmationBars,
 LaplaceSignal entrySignal, LaplaceSignal strongReversalSignal, StartupState startupState,
 int postStartupClosedBarCount, boolean eligibleForExecution, List<RejectionReason> rejectionReasons) {
 public LaplaceSignalResult { rejectionReasons = List.copyOf(rejectionReasons); }

 /** Compatibility constructor for callers which do not yet supply price-return features. */
 public LaplaceSignalResult(String strategyName,String strategyVersion,String symbol,String timeframe,String kernel,int bandwidth,
   String source,boolean repaint,Instant signalCandleOpenTime,Instant signalCandleCloseTime,double signalCandleClose,
   double regressionCurrent,double regressionPrevious,double regressionTwoBarsAgo,double currentSlope,double previousSlope,
   double currentAtr14,double previousAtr14,double atrPercentage,double previousRawTakerImbalance,double currentNormalizedSlope,
   double previousNormalizedSlope,double entryThreshold,double reversalThreshold,int confirmationBars,LaplaceSignal entrySignal,
   LaplaceSignal strongReversalSignal,StartupState startupState,int postStartupClosedBarCount,boolean eligibleForExecution,
   List<RejectionReason> rejectionReasons) {
  this(strategyName,strategyVersion,symbol,timeframe,kernel,bandwidth,source,repaint,signalCandleOpenTime,signalCandleCloseTime,
    signalCandleClose,regressionCurrent,regressionPrevious,regressionTwoBarsAgo,currentSlope,previousSlope,currentAtr14,
    previousAtr14,atrPercentage,previousRawTakerImbalance,Double.NaN,Double.NaN,Double.NaN,currentNormalizedSlope,
    previousNormalizedSlope,entryThreshold,reversalThreshold,confirmationBars,entrySignal,strongReversalSignal,startupState,
    postStartupClosedBarCount,eligibleForExecution,rejectionReasons);
 }
}
