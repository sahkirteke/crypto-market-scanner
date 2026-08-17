package com.crypto.laplace.service;

import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplaceDirectionMapper;
import com.crypto.laplace.model.LaplacePaperVariant;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.service.LaplaceTrueSymbolStopLossCooldownService.ActiveCooldown;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LaplaceDiagnosticLogService {
 private final JsonlDecisionLogService jsonl; private final LaplaceStrategyProperties properties;
 private final LaplaceDirectionMapper directions;
 private final Clock clock;
 private final Set<String> entryBlockLogKeys=ConcurrentHashMap.newKeySet();
 public void signal(LaplaceSignalResult r) {
  if(r.entrySignal()==LaplaceSignal.NONE && r.strongReversalSignal()==LaplaceSignal.NONE) return;
  writeSignal(r, LaplacePaperVariant.INVERTED_TRUE);
 }
 private void writeSignal(LaplaceSignalResult r, LaplacePaperVariant variant) {
  if (!enabled(variant)) return;
  Map<String,Object> m=base(variant); m.put("eventType","SIGNAL"); m.put("strategy",r.strategyName()); m.put("strategyVersion",r.strategyVersion());
  m.put("symbol",r.symbol());m.put("timeframe",r.timeframe());m.put("kernel",r.kernel());m.put("bandwidth",r.bandwidth());m.put("source",r.source());m.put("repaint",r.repaint());
  m.put("signalCandleCloseTime",r.signalCandleCloseTime());m.put("signalCandleClose",r.signalCandleClose());m.put("regressionCurrent",r.regressionCurrent());m.put("regressionPrevious",r.regressionPrevious());m.put("regressionTwoBarsAgo",r.regressionTwoBarsAgo());m.put("currentSlope",r.currentSlope());m.put("previousSlope",r.previousSlope());m.put("currentAtr14",r.currentAtr14());m.put("previousAtr14",r.previousAtr14());m.put("currentNormalizedSlope",r.currentNormalizedSlope());m.put("previousNormalizedSlope",r.previousNormalizedSlope());m.put("entryThreshold",r.entryThreshold());m.put("reversalThreshold",r.reversalThreshold());m.put("confirmationBars",r.confirmationBars());m.put("entrySignal",r.entrySignal());m.put("strongReversalSignal",r.strongReversalSignal());m.put("effectiveExecutionSide",directions.map(variant,r.entrySignal()));m.put("eligibleForExecution",r.eligibleForExecution());m.put("rejectionReasons",r.rejectionReasons());
  jsonl.append(directory(variant),"laplace-signals",m);
 }
 public void error(String symbol,String type,String message){
  if(enabled(LaplacePaperVariant.INVERTED_TRUE)){Map<String,Object> m=base(LaplacePaperVariant.INVERTED_TRUE);m.put("eventType","DATA_REJECTION");m.put("strategy","LAPLACE_KERNEL_REGRESSION_30M");m.put("symbol",symbol);m.put("timeframe","30m");m.put("errorType",type);m.put("errorMessage",String.valueOf(message));jsonl.append(directory(LaplacePaperVariant.INVERTED_TRUE),"laplace-signals",m);}
 }
 public void trueSymbolStopLossCooldown(LaplaceSignalResult r,PositionSide effective,double atrPercentage,double slopeStrength,ActiveCooldown cooldown,long remainingSeconds){Map<String,Object>m=blocked(r,effective,"TRUE_SYMBOL_STOP_LOSS_COOLDOWN",atrPercentage,slopeStrength);m.put("triggeringStopLossPositionId",cooldown.positionId());m.put("stopLossExitTime",cooldown.stopLossExitTime());m.put("cooldownStartedAt",cooldown.cooldownStartedAt());m.put("cooldownUntil",cooldown.cooldownUntil());m.put("remainingCooldownSeconds",remainingSeconds);writeBlocked(r,m,"TRUE_SYMBOL_STOP_LOSS_COOLDOWN");}
 private Map<String,Object> blocked(LaplaceSignalResult r,PositionSide effective,String reason,double atrPercentage,double slopeStrength){Map<String,Object>m=base(LaplacePaperVariant.INVERTED_TRUE);m.put("eventType","ENTRY_BLOCKED");m.put("blockReason",reason);m.put("symbol",r.symbol());m.put("rawEntrySignal",r.entrySignal());m.put("effectiveExecutionSide",effective);m.put("signalCandleOpenTime",r.signalCandleOpenTime());m.put("signalCandleCloseTime",r.signalCandleCloseTime());m.put("signalDetectedAt",r.signalCandleCloseTime());m.put("blockedAt",clock.instant());m.put("atrPercentage",atrPercentage);m.put("currentNormalizedSlope",r.currentNormalizedSlope());m.put("slopeStrength",slopeStrength);return m;}
 private void writeBlocked(LaplaceSignalResult r,Map<String,Object>m,String reason){if(!enabled(LaplacePaperVariant.INVERTED_TRUE))return;String key=r.symbol()+":"+r.signalCandleCloseTime()+":"+r.entrySignal()+":"+reason;if(!entryBlockLogKeys.add(key))return;try{jsonl.append(directory(LaplacePaperVariant.INVERTED_TRUE),"laplace-signals",m);}catch(RuntimeException failure){entryBlockLogKeys.remove(key);}}
 private Map<String,Object> base(LaplacePaperVariant variant){Map<String,Object> m=new LinkedHashMap<>();m.put("paperVariant",variant.name());m.put("signalInverted",variant.signalInverted());return m;}
 private boolean enabled(LaplacePaperVariant v){return v==LaplacePaperVariant.INVERTED_TRUE?properties.getLaplace().getPaper().getInvertedTrue().isEnabled():properties.getLaplace().getPaper().getInvertedFalse().isEnabled();}
 private String directory(LaplacePaperVariant v){return v==LaplacePaperVariant.INVERTED_TRUE?properties.getLaplace().getPaper().getInvertedTrue().getDiagnosticDirectory():properties.getLaplace().getPaper().getInvertedFalse().getDiagnosticDirectory();}
}
