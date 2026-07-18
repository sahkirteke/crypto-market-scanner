package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThirtyMinuteKlineServiceTest {
    @Test
    void startupUsesLatestTwentyClosedCandlesAndExcludesOpenCandle() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        List<Kline> response = candles(21);
        response.add(candle(response.getLast().getOpenTime().plusSeconds(1800), Instant.now().plusSeconds(1800), false));
        when(client.getKlines("BTCUSDT", "30m", 100)).thenReturn(response);
        List<Kline> result = new ThirtyMinuteKlineService(client, new LaplaceStrategyProperties())
                .loadStartupClosed("BTCUSDT");
        assertThat(result).hasSize(20);
        assertThat(result.getFirst().getOpenTime()).isEqualTo(response.get(1).getOpenTime());
        assertThat(result.getLast().getClosed()).isTrue();
    }

    @Test
    void fewerThanTwentyClosedCandlesFailsClosed() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getKlines("BTCUSDT", "30m", 100)).thenReturn(candles(19));
        assertThatThrownBy(() -> new ThirtyMinuteKlineService(client, new LaplaceStrategyProperties())
                .loadStartupClosed("BTCUSDT")).hasMessageContaining("Insufficient startup");
    }

    private List<Kline> candles(int count) {
        Instant start = Instant.now().minusSeconds((count + 2L) * 1800L);
        List<Kline> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Instant open = start.plusSeconds(index * 1800L);
            result.add(candle(open, open.plusSeconds(1799), true));
        }
        return result;
    }

    private Kline candle(Instant open, Instant close, boolean closed) {
        return Kline.builder().openTime(open).closeTime(close).open(BigDecimal.TEN)
                .high(BigDecimal.valueOf(11)).low(BigDecimal.valueOf(9)).close(BigDecimal.TEN)
                .volume(BigDecimal.ONE).closed(closed).build();
    }
}
