package com.crypto.laplace.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.laplace.execution.*;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceInvertedFalseProfitLockServiceTest {
 @Test void closedOnlyFivePercentProfitCoolsOnlyFalseVariant() {
  var runtime=mock(LaplaceInvertedFalseRuntimeService.class);var positions=mock(LaplaceInvertedFalsePositionRepository.class);var client=mock(BinanceFuturesClient.class);var execution=mock(LaplaceInvertedFalseExecutionService.class);var reset=mock(LaplaceTemporaryStateResetService.class);
  var session=LaplaceInvertedFalseSessionEntity.builder().sessionId("false-session").runtimeState(LaplaceRuntimeState.ACTIVE).sessionStartCapital(new BigDecimal("350")).build();
  var closed=LaplaceInvertedFalsePositionEntity.builder().id("closed").sessionId("false-session").status(LaplacePositionStatus.CLOSED).netPnl(new BigDecimal("17.5")).exitTime(Instant.parse("2026-08-14T11:00:00Z")).build();
  when(runtime.isActive()).thenReturn(true);when(runtime.current()).thenReturn(session);when(runtime.target(session)).thenReturn(new BigDecimal("17.5"));when(runtime.beginLiquidation(any(),any(),any(),eq(new BigDecimal("17.5")))).thenReturn(true);
  when(positions.findBySessionIdAndStatus("false-session",LaplacePositionStatus.OPEN)).thenReturn(List.of());when(positions.findBySessionIdAndStatus("false-session",LaplacePositionStatus.CLOSED)).thenReturn(List.of(closed));
  new LaplaceInvertedFalseProfitLockService(runtime,positions,client,new LaplacePnlCalculator(),execution,reset,Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"),ZoneOffset.UTC)).evaluate();
  verifyNoInteractions(client);verify(runtime).recordLiquidationResult(new BigDecimal("17.5"));verify(runtime).startCooldown(Instant.parse("2026-08-14T11:00:00Z"));verify(reset).clearInvertedFalse();verify(reset,never()).clearInvertedTrue();
 }
}
