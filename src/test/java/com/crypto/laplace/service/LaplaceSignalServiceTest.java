package com.crypto.laplace.service;
import static org.assertj.core.api.Assertions.assertThat;import static org.mockito.Mockito.*;
import com.crypto.domain.model.Kline;import com.crypto.laplace.model.*;import java.math.BigDecimal;import java.time.Instant;import java.util.*;import org.junit.jupiter.api.Test;
class LaplaceSignalServiceTest {
 @Test void firstPostStartupBarCanConfirmLongAndStrongReversal(){assertSignal(101,100,99,10,10,1,LaplaceSignal.LONG,LaplaceSignal.LONG);}
 @Test void startupBaselineCannotSignal(){assertSignal(101,100,99,10,10,0,LaplaceSignal.NONE,LaplaceSignal.NONE);}
 @Test void firstPostStartupBarCanConfirmShortAndStrongReversal(){assertSignal(99,100,101,10,10,1,LaplaceSignal.SHORT,LaplaceSignal.SHORT);}
 @Test void deadZoneIsNone(){assertSignal(100.1,100,99.9,10,10,2,LaplaceSignal.NONE,LaplaceSignal.NONE);}
 @Test void atrPercentageAndPreviousImbalanceAreCalculatedAndSignedByRawDirection(){LaplaceKernelRegressionCalculator kr=mock(LaplaceKernelRegressionCalculator.class);Atr14Calculator atr=mock(Atr14Calculator.class);when(kr.calculate(anyList())).thenReturn(new RegressionValues(99,98,97));when(atr.at(anyList(),eq(15))).thenReturn(2.0);when(atr.at(anyList(),eq(14))).thenReturn(2.0);List<Kline> candles=new ArrayList<>();for(int i=0;i<16;i++)candles.add(Kline.builder().openTime(Instant.EPOCH).closeTime(Instant.EPOCH).close(new BigDecimal("100")).volume(new BigDecimal("100")).takerBuyBaseVolume(new BigDecimal("55")).build());var result=new LaplaceSignalService(kr,atr).calculate("BTCUSDT",candles,1);assertThat(result.atrPercentage()).isEqualTo(2.0);assertThat(result.previousRawTakerImbalance()).isEqualTo(0.10);}
 private void assertSignal(double current,double previous,double two,double ca,double pa,int bars,LaplaceSignal entry,LaplaceSignal reversal){
  LaplaceKernelRegressionCalculator kr=mock(LaplaceKernelRegressionCalculator.class);Atr14Calculator atr=mock(Atr14Calculator.class);when(kr.calculate(anyList())).thenReturn(new RegressionValues(current,previous,two));when(atr.at(anyList(),eq(15))).thenReturn(ca);when(atr.at(anyList(),eq(14))).thenReturn(pa);
  List<Kline> candles=new ArrayList<>();for(int i=0;i<16;i++)candles.add(Kline.builder().openTime(Instant.EPOCH).closeTime(Instant.EPOCH).close(BigDecimal.valueOf(current+(current>=previous?1:-1))).build());
  var result=new LaplaceSignalService(kr,atr).calculate("BTCUSDT",candles,bars);assertThat(result.entrySignal()).isEqualTo(entry);assertThat(result.strongReversalSignal()).isEqualTo(reversal);assertThat(result.eligibleForExecution()).isEqualTo(bars>=1&&entry!=LaplaceSignal.NONE);
 }
}
