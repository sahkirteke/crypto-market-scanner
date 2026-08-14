package com.crypto.laplace.service;
import static org.assertj.core.api.Assertions.assertThat;import static org.mockito.Mockito.*;import com.crypto.laplace.config.LaplaceStrategyProperties;import org.junit.jupiter.api.Test;
class LaplaceMarketDataGateTest{
 @Test void falseActiveKeepsSharedDataAliveWhileTrueCoolsDown(){var p=new LaplaceStrategyProperties();var t=mock(LaplaceRuntimeService.class);var f=mock(LaplaceInvertedFalseRuntimeService.class);when(t.allowsMarketData()).thenReturn(false);when(f.allowsMarketData()).thenReturn(true);assertThat(new LaplaceMarketDataGate(p,t,f).allowsMarketData()).isTrue();}
 @Test void bothCooldownOrDisabledMeansNoMarketData(){var p=new LaplaceStrategyProperties();var t=mock(LaplaceRuntimeService.class);var f=mock(LaplaceInvertedFalseRuntimeService.class);var gate=new LaplaceMarketDataGate(p,t,f);assertThat(gate.allowsMarketData()).isFalse();p.getLaplace().getPaper().getInvertedFalse().setEnabled(false);when(f.allowsMarketData()).thenReturn(true);assertThat(gate.allowsMarketData()).isFalse();}
}
