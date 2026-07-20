package com.crypto.laplace.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceStopLossSchedulerTest {
    @Test
    void thirtyMinuteScanUsesItsClosedCandleWithoutFetchingFiveMinuteData() {
        LaplacePaperPositionRepository positions = mock(LaplacePaperPositionRepository.class);
        com.crypto.binance.client.BinanceFuturesClient client = mock(com.crypto.binance.client.BinanceFuturesClient.class);
        LaplacePaperExecutionService execution = mock(LaplacePaperExecutionService.class);
        LaplacePaperPositionEntity position = LaplacePaperPositionEntity.builder().id("id").symbol("BTCUSDT")
                .side(PositionSide.LONG).status(LaplacePositionStatus.OPEN).entryTime(Instant.parse("2026-07-20T12:00:00Z"))
                .entryExecutionPrice(new BigDecimal("100")).build();
        when(positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(List.of(position));
        LaplaceStopLossScheduler scheduler = new LaplaceStopLossScheduler(positions, client, execution, new LaplaceStrategyProperties());
        Kline closedThirtyMinute = Kline.builder().symbol("BTCUSDT").openTime(Instant.parse("2026-07-20T12:00:00Z"))
                .closeTime(Instant.parse("2026-07-20T12:30:00Z")).high(new BigDecimal("101")).low(new BigDecimal("97"))
                .closed(true).build();

        scheduler.checkThirtyMinuteWindow("BTCUSDT", closedThirtyMinute);

        verify(execution).stop(eq(position), eq(closedThirtyMinute.getOpenTime()), eq(closedThirtyMinute.getCloseTime()),
                eq(new BigDecimal("101")), eq(new BigDecimal("97")));
        verify(client, never()).getKlines(any(), eq("5m"), anyInt());
    }
}
