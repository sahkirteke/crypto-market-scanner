package com.crypto.laplace.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LaplaceCoinPoolServiceTest {
 @Test void nextDailyMarginIncludesStopLossNetPnl(){BinanceFuturesClient client=mock(BinanceFuturesClient.class);LaplaceCoinPoolRepository pool=mock(LaplaceCoinPoolRepository.class);LaplacePaperPositionRepository positions=mock(LaplacePaperPositionRepository.class);LaplaceCapitalSnapshotRepository snapshots=mock(LaplaceCapitalSnapshotRepository.class);when(client.getExchangeInfo()).thenReturn(List.of());when(client.getAll24hTickers()).thenReturn(List.of());when(pool.findAll()).thenReturn(List.of());when(positions.findAll()).thenReturn(List.of(LaplacePaperPositionEntity.builder().strategy(LaplacePaperExecutionService.STRATEGY).status(LaplacePositionStatus.CLOSED_BY_STOP_LOSS).netPnl(new BigDecimal("-2.7889")).build()));new LaplaceCoinPoolService(client,pool,positions,snapshots).scan();ArgumentCaptor<LaplaceCapitalSnapshotEntity> capture=ArgumentCaptor.forClass(LaplaceCapitalSnapshotEntity.class);verify(snapshots).save(capture.capture());assertThat(capture.getValue().getRealizedCapital()).isEqualByComparingTo("247.2111");assertThat(capture.getValue().getDailyTradeMargin()).isEqualByComparingTo("4.944222");}
}
