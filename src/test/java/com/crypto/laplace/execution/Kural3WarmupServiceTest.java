package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.service.StartupMarketUniverseService;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Kural3WarmupServiceTest {
 @Test void prewarmsOnlyActiveUniverseAndMarksEachSuccessfulSymbolReady(){StartupMarketUniverseService universe=mock(StartupMarketUniverseService.class);when(universe.symbols()).thenReturn(Set.of("BTCUSDT","ETHUSDT"));LaplaceVolumeProfileService profiles=mock(LaplaceVolumeProfileService.class);when(profiles.prewarm(any(),any())).thenReturn(result());Kural3WarmupService service=new Kural3WarmupService(universe,profiles,properties(2,1));service.run(null);await(service,"BTCUSDT",Kural3WarmupStatus.READY);await(service,"ETHUSDT",Kural3WarmupStatus.READY);verify(profiles).prewarm(eq("BTCUSDT"),any());verify(profiles).prewarm(eq("ETHUSDT"),any());verify(profiles,never()).prewarm(eq("XRPUSDT"),any());service.shutdown();}
 @Test void oneSymbolFailureDoesNotPreventOtherSymbolReadiness(){StartupMarketUniverseService universe=mock(StartupMarketUniverseService.class);LaplaceVolumeProfileService profiles=mock(LaplaceVolumeProfileService.class);when(profiles.prewarm(eq("BAD"),any())).thenThrow(new IllegalStateException("network"));when(profiles.prewarm(eq("GOOD"),any())).thenReturn(result());Kural3WarmupService service=new Kural3WarmupService(universe,profiles,properties(2,1));service.prewarm(Set.of("BAD","GOOD"));await(service,"BAD",Kural3WarmupStatus.FAILED);await(service,"GOOD",Kural3WarmupStatus.READY);service.shutdown();}
 @Test void statusStartsPendingBeforeAsynchronousWorkCompletes(){StartupMarketUniverseService universe=mock(StartupMarketUniverseService.class);LaplaceVolumeProfileService profiles=mock(LaplaceVolumeProfileService.class);Kural3WarmupService service=new Kural3WarmupService(universe,profiles,properties(1,1));assertThat(service.status("BTCUSDT")).isEqualTo(Kural3WarmupStatus.PENDING);service.shutdown();}
 private static LaplaceStrategyProperties properties(int concurrency,int attempts){LaplaceStrategyProperties p=new LaplaceStrategyProperties();p.getLaplace().setVolumeProfileWarmupConcurrency(concurrency);p.getLaplace().setMaxWarmupAttempts(attempts);return p;}
 private static LaplaceVolumeProfileService.WarmupResult result(){return new LaplaceVolumeProfileService.WarmupResult(LocalDate.of(2026,7,30),78,12,.4,false,true,5);}
 private static void await(Kural3WarmupService service,String symbol,Kural3WarmupStatus expected){for(int i=0;i<100&&service.status(symbol)!=expected;i++){try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();}}assertThat(service.status(symbol)).isEqualTo(expected);}
}
