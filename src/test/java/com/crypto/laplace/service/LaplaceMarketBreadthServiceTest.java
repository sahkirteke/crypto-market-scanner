package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.pool.LaplaceCoinPoolService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LaplaceMarketBreadthServiceTest {
 @Test void calculatesBreadthsExcludesMissingAndZeroAndPreventsLookAhead(){Instant ref=Instant.parse("2026-07-28T12:29:59.999Z");BinanceFuturesClient client=mock(BinanceFuturesClient.class);LaplaceCoinPoolService pool=mock(LaplaceCoinPoolService.class);when(pool.activeSymbols()).thenReturn(Set.of("A","B","C","D","E"));
  when(client.getKlines(any(),eq("5m"),eq(60))).thenAnswer(i->{String s=i.getArgument(0);double old=100,now=s.equals("D")?90:s.equals("E")?100:110;List<Kline>x=new ArrayList<>();x.add(c(ref.minusSeconds(14400),s.equals("C")?120:old));if(!s.equals("E"))x.add(c(ref.minusSeconds(7200),old));x.add(c(ref,now));x.add(c(ref.plusSeconds(300),1000));return x;});
  var service=new LaplaceMarketBreadthService(client,pool);var result=service.snapshot(ref);assertThat(result.marketBreadth2h()).isEqualTo(75);assertThat(result.validCoinCount2h()).isEqualTo(4);assertThat(result.marketBreadth4h()).isEqualTo(40);assertThat(result.positiveCoinCount4h()).isEqualTo(2);assertThat(service.snapshot(ref)).isSameAs(result);verify(client,times(5)).getKlines(any(),eq("5m"),eq(60));}
 private Kline c(Instant close,double price){return Kline.builder().openTime(close.minusSeconds(300).plusMillis(1)).closeTime(close).close(BigDecimal.valueOf(price)).build();}
}
