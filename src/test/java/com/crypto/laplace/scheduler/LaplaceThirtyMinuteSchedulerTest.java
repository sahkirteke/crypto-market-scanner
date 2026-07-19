package com.crypto.laplace.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.service.LaplaceDiagnosticLogService;
import com.crypto.laplace.service.LaplaceSignalService;
import com.crypto.laplace.service.LaplaceStartupHistoryService;
import com.crypto.laplace.service.StartupMarketUniverseService;
import com.crypto.laplace.service.ThirtyMinuteKlineService;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LaplaceThirtyMinuteSchedulerTest {
    @Test
    void refreshOnlyRebaselinesSymbolsNewToTheUniverse() {
        StartupMarketUniverseService universe = mock(StartupMarketUniverseService.class);
        LaplaceStartupHistoryService histories = mock(LaplaceStartupHistoryService.class);
        when(universe.symbols()).thenReturn(Set.of("BTCUSDT"), Set.of("BTCUSDT", "ETHUSDT"));
        when(histories.isReady("BTCUSDT")).thenReturn(true);
        LaplaceThirtyMinuteScheduler scheduler = new LaplaceThirtyMinuteScheduler(universe, histories,
                mock(ThirtyMinuteKlineService.class), mock(LaplaceSignalService.class),
                mock(LaplaceDiagnosticLogService.class), mock(LaplacePaperTradeCoordinator.class));

        scheduler.activateInitialUniverse();
        scheduler.activateRefreshedUniverse();

        assertThat(scheduler.activeUniverse()).containsExactlyInAnyOrder("BTCUSDT", "ETHUSDT");
        verify(histories).initializeSymbol("ETHUSDT");
        verify(histories, never()).initializeSymbol("BTCUSDT");
    }
}
