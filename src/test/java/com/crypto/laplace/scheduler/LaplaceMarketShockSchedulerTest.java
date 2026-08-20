package com.crypto.laplace.scheduler;

import static org.mockito.Mockito.*;
import com.crypto.laplace.service.*;
import org.junit.jupiter.api.Test;

class LaplaceMarketShockSchedulerTest {
    @Test void inactiveOrMarketDataBlockedMakesNoShockRequest() {
        LaplaceMarketDataGate gate=mock(LaplaceMarketDataGate.class); LaplaceRuntimeService runtime=mock(LaplaceRuntimeService.class);
        LaplaceMarketShockService shock=mock(LaplaceMarketShockService.class); LaplaceMarketShockScheduler scheduler=new LaplaceMarketShockScheduler(gate,runtime,shock);
        when(gate.allowsMarketData()).thenReturn(false); scheduler.evaluate(); verifyNoInteractions(runtime,shock);
        when(gate.allowsMarketData()).thenReturn(true); when(runtime.isActive()).thenReturn(false); scheduler.evaluate(); verifyNoInteractions(shock);
    }

    @Test void activeRuntimeEvaluatesShock() {
        LaplaceMarketDataGate gate=mock(LaplaceMarketDataGate.class); LaplaceRuntimeService runtime=mock(LaplaceRuntimeService.class);
        LaplaceMarketShockService shock=mock(LaplaceMarketShockService.class); LaplaceMarketShockScheduler scheduler=new LaplaceMarketShockScheduler(gate,runtime,shock);
        when(gate.allowsMarketData()).thenReturn(true); when(runtime.isActive()).thenReturn(true); scheduler.evaluate(); verify(shock).evaluate();
    }
}
