package com.crypto.laplace.session;

import static org.mockito.Mockito.*;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.scheduler.LaplaceThirtyMinuteRuntimeState;
import com.crypto.laplace.service.*;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LaplaceSessionColdStartParityTest {
 @Test void initializerDoesNotDependOnScheduledBean(){assertThat(java.util.Arrays.stream(LaplaceRuntimeInitializer.class.getDeclaredConstructors()).flatMap(c->java.util.Arrays.stream(c.getParameterTypes()))).doesNotContain(com.crypto.laplace.scheduler.LaplaceThirtyMinuteScheduler.class);}
 @Test void processStartupAndRolloverUseExactlyTheSamePipeline(){
  var universe=mock(StartupMarketUniverseService.class);var history=mock(LaplaceStartupHistoryService.class);
  var coordinator=mock(LaplacePaperTradeCoordinator.class);var schedulerState=mock(LaplaceThirtyMinuteRuntimeState.class);
  when(universe.isReady()).thenReturn(true);when(universe.symbols()).thenReturn(Set.of("BTCUSDT"));when(history.readySymbols()).thenReturn(Set.of("BTCUSDT"));
  var initializer=new LaplaceRuntimeInitializer(universe,history,coordinator,schedulerState);
  initializer.initializeForSession(session("2026-08-13T00:00:00Z"));
  initializer.initializeForSession(session("2026-08-13T12:00:00Z"));
  verify(coordinator,times(2)).resetForSession();verify(schedulerState,times(2)).resetForSession();verify(history,times(2)).resetForSession();
  verify(universe,times(2)).initialize();verify(history,times(2)).initializeSymbol("BTCUSDT");
 }
 private LaplaceTradingSessionEntity session(String start){Instant s=Instant.parse(start);return LaplaceTradingSessionEntity.builder().sessionId(start).startTime(s).entryCutoffTime(s.plusSeconds(36000)).endTime(s.plusSeconds(43200)).build();}
}
