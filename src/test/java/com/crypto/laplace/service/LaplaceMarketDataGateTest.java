package com.crypto.laplace.service;
import static org.assertj.core.api.Assertions.assertThat;import static org.mockito.Mockito.*;import com.crypto.laplace.config.LaplaceStrategyProperties;import org.junit.jupiter.api.Test;
class LaplaceMarketDataGateTest {
 @Test void activeTrueAllowsMarketData(){var p=new LaplaceStrategyProperties();var runtime=mock(LaplaceRuntimeService.class);when(runtime.allowsMarketData()).thenReturn(true);assertThat(new LaplaceMarketDataGate(p,runtime).allowsMarketData()).isTrue();}
 @Test void trueCooldownStopsAllMarketData(){var p=new LaplaceStrategyProperties();var runtime=mock(LaplaceRuntimeService.class);assertThat(new LaplaceMarketDataGate(p,runtime).allowsMarketData()).isFalse();verify(runtime).allowsMarketData();}
 @Test void falseRuntimeIsPermanentlyDisabled(){assertThat(new LaplaceMarketDataGate(new LaplaceStrategyProperties(),mock(LaplaceRuntimeService.class)).enabledFalse()).isFalse();}
}
