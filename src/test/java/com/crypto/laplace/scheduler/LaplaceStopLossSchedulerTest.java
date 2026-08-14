package com.crypto.laplace.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.service.FiveMinuteKlineService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LaplaceStopLossSchedulerTest {
    @Test
    void evaluatesEachClosedFiveMinuteCandleOnlyOncePerPosition() {
        LaplacePaperPositionRepository positions = mock(LaplacePaperPositionRepository.class);
        FiveMinuteKlineService klines = mock(FiveMinuteKlineService.class);
        LaplacePaperExecutionService execution = mock(LaplacePaperExecutionService.class);
        Kline candle = Kline.builder().openTime(Instant.parse("2026-08-06T10:00:00Z"))
                .closeTime(Instant.parse("2026-08-06T10:05:00Z"))
                .low(BigDecimal.ONE).high(BigDecimal.TEN).closed(true).build();
        when(klines.loadLatestClosed("BTCUSDT")).thenReturn(candle);
        when(execution.closeAtStopLoss("position", candle)).thenReturn(true);
        var runtime = mock(com.crypto.laplace.service.LaplaceRuntimeService.class);
        when(runtime.isActive()).thenReturn(true);
        LaplaceStopLossScheduler scheduler = new LaplaceStopLossScheduler(positions, klines, execution, runtime);

        scheduler.evaluate("position", "BTCUSDT");
        scheduler.evaluate("position", "BTCUSDT");

        verify(execution, times(1)).closeAtStopLoss("position", candle);
        verify(execution).drainTradeEvents();
    }
}
