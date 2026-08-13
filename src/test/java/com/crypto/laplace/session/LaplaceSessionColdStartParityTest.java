package com.crypto.laplace.session;

import static org.mockito.Mockito.*;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.scheduler.LaplaceThirtyMinuteScheduler;
import com.crypto.laplace.service.*;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LaplaceSessionColdStartParityTest {
 @Test void processStartupAndRolloverUseExactlyTheSamePipeline(){
  var universe=mock(StartupMarketUniverseService.class);var history=mock(LaplaceStartupHistoryService.class);
  var coordinator=mock(LaplacePaperTradeCoordinator.class);var scheduler=mock(LaplaceThirtyMinuteScheduler.class);
  when(universe.isReady()).thenReturn(true);when(universe.symbols()).thenReturn(Set.of("BTCUSDT"));when(history.readySymbols()).thenReturn(Set.of("BTCUSDT"));
  var initializer=new LaplaceRuntimeInitializer(universe,history,coordinator,scheduler);
  initializer.initializeForSession(session("2026-08-13T00:00:00Z"));
  initializer.initializeForSession(session("2026-08-13T12:00:00Z"));
  verify(coordinator,times(2)).resetForSession();verify(scheduler,times(2)).resetForSession();verify(history,times(2)).resetForSession();
  verify(universe,times(2)).initialize();verify(history,times(2)).initializeSymbol("BTCUSDT");
 }
 private LaplaceTradingSessionEntity session(String start){Instant s=Instant.parse(start);return LaplaceTradingSessionEntity.builder().sessionId(start).startTime(s).entryCutoffTime(s.plusSeconds(36000)).endTime(s.plusSeconds(43200)).build();}
}
