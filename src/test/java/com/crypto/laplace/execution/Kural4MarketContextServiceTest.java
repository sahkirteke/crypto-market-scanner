package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class Kural4MarketContextServiceTest {
    private static final Instant SIGNAL=Instant.parse("2026-01-01T01:00:00Z");
    @Test void excludesOpenAndFutureCandlesAndIncludesCloseAtSignalTime(){var client=mock(BinanceFuturesClient.class);List<Kline> data=validCandles();data.add(candle(SIGNAL.plusSeconds(300),999,0,1000,true));data.add(candle(SIGNAL,888,0,999,false));when(client.getKlines("BTCUSDT","5m",100)).thenReturn(data);var c=new Kural4MarketContextService(client).contextAt(SIGNAL);assertThat(c.btcLastClose()).isEqualTo(112);assertThat(c.lastCompleted5mCloseTime()).isEqualTo(SIGNAL);}
    @Test void computesReturnsAndRangeFromLastTwelveCompletedCandles(){var client=mock(BinanceFuturesClient.class);when(client.getKlines(any(),any(),anyInt())).thenReturn(validCandles());var c=new Kural4MarketContextService(client).contextAt(SIGNAL);assertThat(c.btcReturn15mPct()).isCloseTo((112d/109-1)*100,org.assertj.core.data.Offset.offset(1e-9));assertThat(c.btcReturn30mPct()).isCloseTo((112d/106-1)*100,org.assertj.core.data.Offset.offset(1e-9));assertThat(c.btcRangePosition60()).isCloseTo(12d/13,org.assertj.core.data.Offset.offset(1e-9));}
    @Test void flatRangeIsUnavailable(){var client=mock(BinanceFuturesClient.class);when(client.getKlines(any(),any(),anyInt())).thenReturn(java.util.stream.IntStream.range(0,13).mapToObj(i->candle(SIGNAL.minusSeconds((12-i)*300L),100,100,100,true)).toList());assertThat(new Kural4MarketContextService(client).contextAt(SIGNAL)).isNull();}
    @Test void insufficientCandlesIsUnavailable(){var client=mock(BinanceFuturesClient.class);when(client.getKlines(any(),any(),anyInt())).thenReturn(validCandles().subList(0,11));assertThat(new Kural4MarketContextService(client).contextAt(SIGNAL)).isNull();}
    @Test void successfulResultIsCached(){var client=mock(BinanceFuturesClient.class);when(client.getKlines(any(),any(),anyInt())).thenReturn(validCandles());var service=new Kural4MarketContextService(client);assertThat(service.contextAt(SIGNAL)).isSameAs(service.contextAt(SIGNAL));verify(client,times(1)).getKlines("BTCUSDT","5m",100);}
    @Test void unavailableResultIsNotCachedAndNextCallRetries(){var client=mock(BinanceFuturesClient.class);when(client.getKlines(any(),any(),anyInt())).thenReturn(List.of(),validCandles());var service=new Kural4MarketContextService(client);assertThat(service.contextAt(SIGNAL)).isNull();assertThat(service.contextAt(SIGNAL)).isNotNull();verify(client,times(2)).getKlines("BTCUSDT","5m",100);}
    private List<Kline> validCandles(){List<Kline>x=new ArrayList<>();for(int i=0;i<13;i++)x.add(candle(SIGNAL.minusSeconds((12-i)*300L),100+i,99+i,101+i,true));return x;}
    private Kline candle(Instant close,double value,double low,double high,boolean closed){return Kline.builder().closeTime(close).close(BigDecimal.valueOf(value)).low(BigDecimal.valueOf(low)).high(BigDecimal.valueOf(high)).closed(closed).build();}
}
