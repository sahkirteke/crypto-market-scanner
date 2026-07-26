package com.crypto.laplace.scheduler;

import static org.mockito.Mockito.*;
import com.crypto.laplace.execution.*;
import com.crypto.laplace.pool.LaplaceCoinPoolService;
import com.crypto.laplace.service.*;
import com.crypto.laplace.stop.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LaplaceStopSchedulerOrchestrationTest {
 @Test void thirtyMinuteCycleRunsStopCheckExactlyOnceBeforeSignalWork(){LaplaceTradeReconciliationService reconciliation=mock(LaplaceTradeReconciliationService.class);when(reconciliation.isComplete()).thenReturn(true);LaplaceCoinPoolService pool=mock(LaplaceCoinPoolService.class);when(pool.symbolsToProcess()).thenReturn(Set.of());LaplacePaperTradeCoordinator coordinator=mock(LaplacePaperTradeCoordinator.class);when(coordinator.managementSymbols()).thenReturn(Set.of());LaplaceFiveMinuteStopService stop=mock(LaplaceFiveMinuteStopService.class);new LaplaceThirtyMinuteScheduler(mock(StartupMarketUniverseService.class),pool,mock(LaplaceStartupHistoryService.class),mock(ThirtyMinuteKlineService.class),mock(LaplaceSignalService.class),mock(LaplaceDiagnosticLogService.class),coordinator,stop,reconciliation).scan();verify(stop,times(1)).checkOpenPositions();}
 @Test void independentFiveMinuteCycleRunsOneStopCheck(){LaplaceTradeReconciliationService reconciliation=mock(LaplaceTradeReconciliationService.class);when(reconciliation.isComplete()).thenReturn(true);LaplaceFiveMinuteStopService stop=mock(LaplaceFiveMinuteStopService.class);new LaplaceFiveMinuteStopScheduler(stop,reconciliation).run();verify(stop,times(1)).checkOpenPositions();}
}
