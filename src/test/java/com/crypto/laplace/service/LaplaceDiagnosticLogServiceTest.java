package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.crypto.common.enums.PositionSide;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplaceDirectionMapper;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LaplaceDiagnosticLogServiceTest {
 @Test void softFilterPayloadIsWrittenOnceToTrueDirectory(){var jsonl=mock(JsonlDecisionLogService.class);var properties=new LaplaceStrategyProperties();var clock=Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"),ZoneOffset.UTC);var service=new LaplaceDiagnosticLogService(jsonl,properties,new LaplaceDirectionMapper(),clock);var signal=signal();service.trueSoftRiskFilter(signal,PositionSide.SHORT,5.0,.16,5.0,.16);service.trueSoftRiskFilter(signal,PositionSide.SHORT,5.0,.16,5.0,.16);ArgumentCaptor<Map<String,Object>> payload=ArgumentCaptor.forClass(Map.class);verify(jsonl).append(eq("signals/laplace/inverted-true/diagnostics"),eq("laplace-signals"),payload.capture());assertThat(payload.getValue()).containsEntry("eventType","ENTRY_BLOCKED").containsEntry("paperVariant","INVERTED_TRUE").containsEntry("blockReason","TRUE_SOFT_RISK_FILTER").containsEntry("effectiveExecutionSide",PositionSide.SHORT).containsEntry("atrThreshold",5.0).containsEntry("slopeStrengthThreshold",.16).containsEntry("blockedAt",clock.instant());}
 @Test void cooldownPayloadIsWrittenOnce(){var jsonl=mock(JsonlDecisionLogService.class);var clock=Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"),ZoneOffset.UTC);var service=new LaplaceDiagnosticLogService(jsonl,new LaplaceStrategyProperties(),new LaplaceDirectionMapper(),clock);var cooldown=new LaplaceTrueSymbolStopLossCooldownService.ActiveCooldown("sl-id",clock.instant().minusSeconds(60),clock.instant().minusSeconds(60),clock.instant().plusSeconds(14340));service.trueSymbolStopLossCooldown(signal(),PositionSide.SHORT,6.0,.30,cooldown,14340);service.trueSymbolStopLossCooldown(signal(),PositionSide.SHORT,6.0,.30,cooldown,14340);ArgumentCaptor<Map<String,Object>> payload=ArgumentCaptor.forClass(Map.class);verify(jsonl).append(anyString(),eq("laplace-signals"),payload.capture());assertThat(payload.getValue()).containsEntry("blockReason","TRUE_SYMBOL_STOP_LOSS_COOLDOWN").containsEntry("triggeringStopLossPositionId","sl-id").containsEntry("cooldownUntil",cooldown.cooldownUntil()).containsEntry("remainingCooldownSeconds",14340L);}
 private LaplaceSignalResult signal(){Instant close=Instant.parse("2026-08-15T11:30:00Z");return new LaplaceSignalResult(LaplacePaperExecutionService.STRATEGY,"1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,close.minusSeconds(1800),close,100,99,98,97,1,1,5,5,.16,.1,.03,.04,2,LaplaceSignal.LONG,LaplaceSignal.NONE,StartupState.ACTIVE,1,true,List.of());}
}
