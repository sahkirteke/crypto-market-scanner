package com.crypto.laplace.service;

import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
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
  jsonl.append(properties.getLaplace().getPaper().getInvertedTrue().getDiagnosticDirectory(),"laplace-signals",m);
 }
 public void error(String symbol,String type,String message){ jsonl.append(properties.getLaplace().getDiagnosticDirectory(),"laplace-signals",Map.of("eventType","DATA_REJECTION","strategy","LAPLACE_KERNEL_REGRESSION_30M","symbol",symbol,"timeframe","30m","errorType",type,"errorMessage",String.valueOf(message))); }
}
