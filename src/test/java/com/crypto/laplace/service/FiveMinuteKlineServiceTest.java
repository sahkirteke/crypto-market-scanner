package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        BinanceFuturesClient client = mock(BinanceFuturesClient.class); Instant cutoff=Instant.parse("2026-08-06T10:30:00Z");
        java.util.ArrayList<Kline> raw=new java.util.ArrayList<>();for(int i=0;i<501;i++)raw.add(complete(cutoff.minusSeconds((500L-i)*300),true));raw.add(complete(cutoff.plusSeconds(300),false));
        when(client.getKlines("BTCUSDT","5m",1000)).thenReturn(raw);
        List<Kline> result=new FiveMinuteKlineService(client).loadLastClosedThrough("BTCUSDT",cutoff,500);
        assertThat(result).hasSize(500);assertThat(result.getLast().getCloseTime()).isEqualTo(cutoff);
    }

    @Test
    void rejectsWhenFewerThanRequiredClosedCandlesRemain() {
        BinanceFuturesClient client=mock(BinanceFuturesClient.class);Instant cutoff=Instant.parse("2026-08-06T10:30:00Z");
        when(client.getKlines("BTCUSDT","5m",1000)).thenReturn(List.of(complete(cutoff,false)));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->new FiveMinuteKlineService(client).loadLastClosedThrough("BTCUSDT",cutoff,500)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void calculatesExpectedClosedBoundaryForExactAndInProgressIntervals() {
        FiveMinuteKlineService service=new FiveMinuteKlineService(mock(BinanceFuturesClient.class));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T11:00:00Z"))).isEqualTo(Instant.parse("2026-08-06T11:00:00Z"));
        assertThat(service.lastExpectedClosedFiveMinuteCandle(Instant.parse("2026-08-06T11:03:00Z"))).isEqualTo(Instant.parse("2026-08-06T11:00:00Z"));
    }

    private Kline complete(Instant close,boolean closed){return Kline.builder().openTime(close.minusSeconds(300)).closeTime(close).open(BigDecimal.TEN).high(BigDecimal.TEN).low(BigDecimal.ONE).close(BigDecimal.TEN).volume(BigDecimal.ONE).quoteAssetVolume(BigDecimal.ONE).closed(closed).build();}

    private Kline candle(Instant close, boolean closed) {
        return Kline.builder().openTime(close.minusSeconds(300)).closeTime(close)
                .high(BigDecimal.TEN).low(BigDecimal.ONE).closed(closed).build();
    }
}
