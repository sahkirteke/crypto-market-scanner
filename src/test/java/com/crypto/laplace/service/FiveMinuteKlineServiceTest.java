package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.*;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiveMinuteKlineServiceTest {
    @Test
    void returnsLatestClosedFiveMinuteCandleAndExcludesOpenCandle() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        Instant oldClose = Instant.now().minusSeconds(600);
        Instant latestClose = Instant.now().minusSeconds(300);
        Kline old = candle(oldClose, true);
        Kline latest = candle(latestClose, true);
        Kline open = candle(Instant.now().plusSeconds(60), false);
        when(client.getKlines("BTCUSDT", "5m", 3)).thenReturn(List.of(old, latest, open));
        assertThat(new FiveMinuteKlineService(client).loadLatestClosed("BTCUSDT")).isSameAs(latest);
    }

    @Test
    void requestsOneThousandRawCandlesAndReturnsLastFiveHundredClosedThroughCutoff() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class); Instant cutoff=Instant.parse("2026-08-06T10:29:59.999Z");
        java.util.ArrayList<Kline> raw=new java.util.ArrayList<>();for(int i=0;i<501;i++)raw.add(complete(cutoff.minusSeconds((500L-i)*300),true));raw.add(complete(cutoff.plusSeconds(300),false));
        when(client.getKlines("BTCUSDT","5m",502)).thenReturn(raw);
        List<Kline> result=new FiveMinuteKlineService(client).loadLastClosedThrough("BTCUSDT",cutoff,500);
        assertThat(result).hasSize(500);assertThat(result.getLast().getCloseTime()).isEqualTo(cutoff);
    }

    @Test
    void rejectsWhenFewerThanRequiredClosedCandlesRemain() {
        BinanceFuturesClient client=mock(BinanceFuturesClient.class);Instant cutoff=Instant.parse("2026-08-06T10:29:59.999Z");
        when(client.getKlines("BTCUSDT","5m",502)).thenReturn(List.of(complete(cutoff,false)));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->new FiveMinuteKlineService(client).loadLastClosedThrough("BTCUSDT",cutoff,500)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void calculatesExpectedClosedBoundaryForExactAndInProgressIntervals() {
        FiveMinuteKlineService service=new FiveMinuteKlineService(mock(BinanceFuturesClient.class));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T10:59:59.999Z"))).isEqualTo(Instant.parse("2026-08-06T10:59:59.999Z"));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T11:00:00Z"))).isEqualTo(Instant.parse("2026-08-06T10:59:59.999Z"));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T11:03:00Z"))).isEqualTo(Instant.parse("2026-08-06T10:59:59.999Z"));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T11:04:59.999Z"))).isEqualTo(Instant.parse("2026-08-06T11:04:59.999Z"));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T11:05:00Z"))).isEqualTo(Instant.parse("2026-08-06T11:04:59.999Z"));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T11:05:01Z"))).isEqualTo(Instant.parse("2026-08-06T11:04:59.999Z"));
    }

    @Test
    void normalSingleCandleCatchUpRequestsOnlyThreeCandles() {
        BinanceFuturesClient client=mock(BinanceFuturesClient.class);FiveMinuteKlineService service=new FiveMinuteKlineService(client);
        Instant after=Instant.parse("2026-08-06T10:59:59.999Z"),cutoff=Instant.parse("2026-08-06T11:05:02Z");
        when(client.getKlines("BTCUSDT","5m",3,cutoff)).thenReturn(List.of(
                complete(Instant.parse("2026-08-06T10:54:59.999Z"),true),complete(after,true),complete(Instant.parse("2026-08-06T11:04:59.999Z"),true)));
        assertThat(service.loadClosedRange("BTCUSDT",after,cutoff)).extracting(Kline::getCloseTime)
                .containsExactly(Instant.parse("2026-08-06T11:04:59.999Z"));
        verify(client).getKlines("BTCUSDT","5m",3,cutoff);
        verify(client,never()).getKlines("BTCUSDT","5m",1000,cutoff);
    }

    @Test
    void sixCandleCatchUpRequestsEightCandlesOnce() {
        BinanceFuturesClient client=mock(BinanceFuturesClient.class);FiveMinuteKlineService service=new FiveMinuteKlineService(client);
        Instant after=Instant.parse("2026-08-06T10:59:59.999Z"),cutoff=Instant.parse("2026-08-06T11:30:01Z");
        java.util.ArrayList<Kline> page=new java.util.ArrayList<>();for(int i=-1;i<=6;i++)page.add(complete(after.plusSeconds(i*300L),true));
        when(client.getKlines("BTCUSDT","5m",8,cutoff)).thenReturn(page);
        assertThat(service.loadClosedRange("BTCUSDT",after,cutoff)).hasSize(6);
        verify(client).getKlines("BTCUSDT","5m",8,cutoff);
        verify(client,never()).getKlines(eq("BTCUSDT"),eq("5m"),eq(1000),any());
    }

    @Test
    void currentRangeDoesNotCallBinance() {
        BinanceFuturesClient client=mock(BinanceFuturesClient.class);FiveMinuteKlineService service=new FiveMinuteKlineService(client);
        Instant after=Instant.parse("2026-08-06T11:04:59.999Z");
        assertThat(service.loadClosedRange("BTCUSDT",after,Instant.parse("2026-08-06T11:05:02Z"))).isEmpty();
        verifyNoInteractions(client);
    }

    private Kline complete(Instant close,boolean closed){return Kline.builder().openTime(close.minusSeconds(300)).closeTime(close).open(BigDecimal.TEN).high(BigDecimal.TEN).low(BigDecimal.ONE).close(BigDecimal.TEN).volume(BigDecimal.ONE).quoteAssetVolume(BigDecimal.ONE).closed(closed).build();}

    private Kline candle(Instant close, boolean closed) {
        return Kline.builder().openTime(close.minusSeconds(300)).closeTime(close)
                .high(BigDecimal.TEN).low(BigDecimal.ONE).closed(closed).build();
    }
}
