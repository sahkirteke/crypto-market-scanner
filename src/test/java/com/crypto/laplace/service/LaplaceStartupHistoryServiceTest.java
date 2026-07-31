package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.StartupState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LaplaceStartupHistoryServiceTest {
    @Test
    void preparesTwentyOhlcvCandlesAndIndicatorFieldsWithoutActivatingExecution() {
        StartupMarketUniverseService universe = mock(StartupMarketUniverseService.class);
        ThirtyMinuteKlineService klines = mock(ThirtyMinuteKlineService.class);
        LaplaceKernelRegressionCalculator regression = mock(LaplaceKernelRegressionCalculator.class);
        Atr14Calculator atr = mock(Atr14Calculator.class);
        when(universe.isReady()).thenReturn(true);
        when(universe.symbols()).thenReturn(Set.of("BTCUSDT", "ETHUSDT"));
        when(klines.loadStartupClosed("BTCUSDT")).thenReturn(candles());
        when(klines.loadStartupClosed("ETHUSDT")).thenReturn(candles());
        when(regression.at(anyList(), anyInt())).thenAnswer(call -> 100d + call.getArgument(1, Integer.class));
        when(atr.at(anyList(), anyInt())).thenReturn(2d);
        LaplaceStartupHistoryService service = new LaplaceStartupHistoryService(universe, klines,
                regression, atr, new LaplaceStrategyProperties());
        service.run(null);
        var history = service.history("BTCUSDT");
        assertThat(history.candles()).hasSize(20);
        assertThat(history.preparedCandles()).hasSize(20);
        assertThat(history.state()).isEqualTo(StartupState.READY_WAITING_NEXT_CLOSE);
        assertThat(history.preparedCandles().get(history.preparedCandles().size() - 1).regressionValue()).isNotNull();
        assertThat(history.preparedCandles().get(history.preparedCandles().size() - 1).atr14()).isNotNull();
        assertThat(history.preparedCandles().get(history.preparedCandles().size() - 1).slope()).isNotNull();
        assertThat(history.preparedCandles().get(history.preparedCandles().size() - 1).normalizedSlope()).isNotNull();
        assertThat(service.readySymbols()).containsExactlyInAnyOrder("BTCUSDT", "ETHUSDT");
    }

    @Test
    void oneSymbolFailureDoesNotStopOtherStartupHistories() {
        StartupMarketUniverseService universe = mock(StartupMarketUniverseService.class);
        ThirtyMinuteKlineService klines = mock(ThirtyMinuteKlineService.class);
        when(universe.isReady()).thenReturn(true);
        when(universe.symbols()).thenReturn(Set.of("BTCUSDT", "BROKENUSDT"));
        when(klines.loadStartupClosed("BTCUSDT")).thenReturn(candles());
        when(klines.loadStartupClosed("BROKENUSDT")).thenThrow(new IllegalStateException("insufficient"));
        LaplaceKernelRegressionCalculator regression = mock(LaplaceKernelRegressionCalculator.class);
        Atr14Calculator atr = mock(Atr14Calculator.class);
        when(regression.at(anyList(), anyInt())).thenReturn(100d);
        when(atr.at(anyList(), anyInt())).thenReturn(2d);
        LaplaceStartupHistoryService service = new LaplaceStartupHistoryService(universe, klines,
                regression, atr, new LaplaceStrategyProperties());
        service.run(null);
        assertThat(service.isReady("BTCUSDT")).isTrue();
        assertThat(service.isReady("BROKENUSDT")).isFalse();
    }

    private List<Kline> candles() {
        Instant start = Instant.parse("2026-07-17T00:00:00Z");
        List<Kline> result = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            Instant open = start.plusSeconds(index * 1800L);
            result.add(Kline.builder().openTime(open).closeTime(open.plusSeconds(1799))
                    .open(BigDecimal.valueOf(100 + index)).high(BigDecimal.valueOf(102 + index))
                    .low(BigDecimal.valueOf(99 + index)).close(BigDecimal.valueOf(101 + index))
                    .volume(BigDecimal.TEN).closed(true).build());
        }
        return result;
    }
}
