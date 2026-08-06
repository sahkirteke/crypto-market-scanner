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

    private Kline candle(Instant close, boolean closed) {
        return Kline.builder().openTime(close.minusSeconds(300)).closeTime(close)
                .high(BigDecimal.TEN).low(BigDecimal.ONE).closed(closed).build();
    }
}
