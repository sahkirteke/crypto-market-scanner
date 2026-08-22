package com.crypto.laplace.scheduler;

import static org.mockito.Mockito.*;
import com.crypto.laplace.execution.LaplaceSignalFanOut;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.service.*;
import org.junit.jupiter.api.Test;

class LaplaceCooldownSchedulingIsolationTest {
 @Test void bothCooldownSkipsThirtyMinuteAndStopLossMarketCalls(){var gate=mock(LaplaceMarketDataGate.class);when(gate.allowsMarketData()).thenReturn(false);var klines30=mock(ThirtyMinuteKlineService.class);var scan=new LaplaceThirtyMinuteScheduler(mock(StartupMarketUniverseService.class),mock(LaplaceStartupHistoryService.class),klines30,mock(LaplaceSignalService.class),mock(LaplaceDiagnosticLogService.class),mock(LaplaceSignalFanOut.class),gate);scan.scan();verifyNoInteractions(klines30);var klines5=mock(FiveMinuteKlineService.class);var sl=new LaplaceStopLossFanOutScheduler(gate,mock(LaplaceRuntimeService.class),mock(LaplacePaperPositionRepository.class),klines5,mock(LaplaceStopLossScheduler.class));sl.evaluate();verifyNoInteractions(klines5);}
}
