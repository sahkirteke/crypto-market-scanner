package com.crypto.laplace.service;

import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.model.LaplaceEntryDecision;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LaplaceDiagnosticLogService {
 private final JsonlDecisionLogService jsonl; private final LaplaceStrategyProperties properties;
 public void signal(LaplaceSignalResult r) {
  if(r.entrySignal()==LaplaceSignal.NONE && r.strongReversalSignal()==LaplaceSignal.NONE) return;
  Map<String,Object> m=new LinkedHashMap<>(); m.put("eventType","SIGNAL"); m.put("strategy",r.strategyName()); m.put("strategyVersion",r.strategyVersion());
  m.put("symbol",r.symbol());m.put("timeframe",r.timeframe());m.put("kernel",r.kernel());m.put("bandwidth",r.bandwidth());m.put("source",r.source());m.put("repaint",r.repaint());
  m.put("signalCandleCloseTime",r.signalCandleCloseTime());m.put("signalCandleClose",r.signalCandleClose());m.put("regressionCurrent",r.regressionCurrent());m.put("regressionPrevious",r.regressionPrevious());m.put("regressionTwoBarsAgo",r.regressionTwoBarsAgo());m.put("currentSlope",r.currentSlope());m.put("previousSlope",r.previousSlope());m.put("currentAtr14",r.currentAtr14());m.put("previousAtr14",r.previousAtr14());m.put("currentNormalizedSlope",r.currentNormalizedSlope());m.put("previousNormalizedSlope",r.previousNormalizedSlope());m.put("entryThreshold",r.entryThreshold());m.put("reversalThreshold",r.reversalThreshold());m.put("confirmationBars",r.confirmationBars());m.put("entrySignal",r.entrySignal());m.put("strongReversalSignal",r.strongReversalSignal());m.put("eligibleForExecution",r.eligibleForExecution());m.put("rejectionReasons",r.rejectionReasons());
  jsonl.append(properties.getLaplace().getDiagnosticDirectory(),"laplace-signals",m);
 }
 public void error(String symbol,String type,String message){ jsonl.append(properties.getLaplace().getDiagnosticDirectory(),"laplace-signals",Map.of("eventType","DATA_REJECTION","strategy","LAPLACE_KERNEL_REGRESSION_30M","symbol",symbol,"timeframe","30m","errorType",type,"errorMessage",String.valueOf(message))); }
 public void entryDecision(LaplaceSignalResult r, LaplaceEntryDecision d) {
  Map<String,Object> m=new LinkedHashMap<>();m.put("eventType","ENTRY_DECISION");m.put("rawEntrySignal",r.entrySignal());m.put("rawStrongReversalSignal",r.strongReversalSignal());m.put("effectiveExecutionSide",d.effectiveExecutionSide());m.put("signalInverted",true);m.put("signalCandleCloseTime",r.signalCandleCloseTime());m.put("current5mClose",d.current5mClose());m.put("atrPercentage30m",d.atrPercentage30m());m.put("lowestLow24",d.lowestLow24());m.put("distanceFromLowestLow24Pct",d.distanceFromLowestLow24Pct());m.put("ema20",d.ema20());m.put("ema50",d.ema50());m.put("ema50TwelveBarsAgo",d.ema50TwelveBarsAgo());m.put("ema20AboveEma50Pct",d.ema20AboveEma50Pct());m.put("ema50Rise60mPct",d.ema50Rise60mPct());m.put("currentNormalizedSlope",d.currentNormalizedSlope());m.put("volumeProfileWindowBars",d.volumeProfileWindowBars());m.put("volumeProfileBins",d.volumeProfileBins());m.put("volumeProfilePoc",d.volumeProfilePoc());m.put("volumeProfileGapPct",d.volumeProfileGapPct());m.put("kural5Allowed",d.allowed());m.put("kural5RejectionReasons",d.rejectionReasons());jsonl.append(properties.getLaplace().getDiagnosticDirectory(),"laplace-signals",m);
 }
}
