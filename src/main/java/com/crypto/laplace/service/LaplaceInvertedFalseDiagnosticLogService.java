package com.crypto.laplace.service;
import com.crypto.common.service.JsonlDecisionLogService;import com.crypto.laplace.config.LaplaceStrategyProperties;import com.crypto.laplace.model.LaplaceSignalResult;import java.util.LinkedHashMap;import java.util.Map;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class LaplaceInvertedFalseDiagnosticLogService {
 private final JsonlDecisionLogService jsonl;private final LaplaceStrategyProperties properties;
 public void signal(LaplaceSignalResult signal){Map<String,Object> m=new LinkedHashMap<>();m.put("paperVariant","INVERTED_FALSE");m.put("signalInverted",false);m.put("rawEntrySignal",signal.entrySignal());m.put("effectiveExecutionSide",signal.entrySignal());m.put("symbol",signal.symbol());m.put("signalCandleCloseTime",signal.signalCandleCloseTime());jsonl.append(properties.getLaplace().getPaper().getInvertedFalse().getDiagnosticDirectory(),"laplace-signals",m);}
}
