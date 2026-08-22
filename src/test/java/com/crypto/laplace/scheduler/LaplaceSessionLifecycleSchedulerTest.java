package com.crypto.laplace.scheduler;

import static org.mockito.Mockito.*;
import com.crypto.laplace.service.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LaplaceSessionLifecycleSchedulerTest {
 @Test void beforeCooldownExpiryDoesNothing(){var runtime=mock(LaplaceRuntimeService.class);var reset=mock(LaplaceTemporaryStateResetService.class);var universe=mock(StartupMarketUniverseService.class);var histories=mock(LaplaceStartupHistoryService.class);var gate=mock(LaplaceMarketDataGate.class);when(gate.enabledTrue()).thenReturn(true);new LaplaceSessionLifecycleScheduler(runtime,reset,universe,histories,gate).advance();verify(runtime).beginInitializationIfDue();verifyNoInteractions(reset,universe,histories);}
 @Test void dueCooldownPerformsFullFreshStartBeforeActivation(){var runtime=mock(LaplaceRuntimeService.class);var reset=mock(LaplaceTemporaryStateResetService.class);var universe=mock(StartupMarketUniverseService.class);var histories=mock(LaplaceStartupHistoryService.class);var gate=mock(LaplaceMarketDataGate.class);when(gate.enabledTrue()).thenReturn(true);when(runtime.beginInitializationIfDue()).thenReturn(true);when(universe.isReady()).thenReturn(false,true);when(universe.symbols()).thenReturn(Set.of("BTCUSDT"));when(histories.isReady("BTCUSDT")).thenReturn(false);when(histories.readySymbols()).thenReturn(Set.of("BTCUSDT"));new LaplaceSessionLifecycleScheduler(runtime,reset,universe,histories,gate).advance();var order=inOrder(reset,universe,histories,runtime);order.verify(reset).clear();order.verify(universe).initialize();order.verify(histories).initializeSymbol("BTCUSDT");order.verify(runtime).activateNextSession();}
}
