package com.crypto.laplace.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.laplace.audit.VolumeScanAuditService; import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LaplaceCoinPoolServiceTest {
 @Test void nextDailyMarginIncludesStopLossNetPnl(){BinanceFuturesClient client=mock(BinanceFuturesClient.class);LaplaceCoinPoolRepository pool=mock(LaplaceCoinPoolRepository.class);LaplacePaperPositionRepository positions=mock(LaplacePaperPositionRepository.class);LaplaceCapitalSnapshotRepository snapshots=mock(LaplaceCapitalSnapshotRepository.class);when(client.getExchangeInfo()).thenReturn(List.of());when(client.getAll24hTickers()).thenReturn(List.of());when(pool.findAll()).thenReturn(List.of());when(positions.findAll()).thenReturn(List.of(LaplacePaperPositionEntity.builder().strategy(LaplacePaperExecutionService.STRATEGY).status(LaplacePositionStatus.CLOSED_BY_STOP_LOSS).netPnl(new BigDecimal("-2.7889")).build()));VolumeScanAuditService audit=mock(VolumeScanAuditService.class);when(audit.newRun()).thenReturn(new VolumeScanAuditService.Run("run",java.time.LocalDate.now(),java.time.OffsetDateTime.now()));new LaplaceCoinPoolService(client,pool,positions,snapshots,audit).scan();ArgumentCaptor<LaplaceCapitalSnapshotEntity> capture=ArgumentCaptor.forClass(LaplaceCapitalSnapshotEntity.class);verify(snapshots).save(capture.capture());assertThat(capture.getValue().getRealizedCapital()).isEqualByComparingTo("247.2111");assertThat(capture.getValue().getDailyTradeMargin()).isEqualByComparingTo("4.944222");}
 @Test void persistentReentryLockSurvivesServiceRecreationAndOnlyOppositeUnlocks(){BinanceFuturesClient client=mock(BinanceFuturesClient.class);LaplaceCoinPoolRepository repository=mock(LaplaceCoinPoolRepository.class);LaplaceCoinPoolEntity row=LaplaceCoinPoolEntity.builder().symbol("BTCUSDT").state(LaplaceCoinPoolState.ACTIVE).build();when(repository.findById("BTCUSDT")).thenReturn(java.util.Optional.of(row));LaplaceCoinPoolService first=new LaplaceCoinPoolService(client,repository,mock(LaplacePaperPositionRepository.class),mock(LaplaceCapitalSnapshotRepository.class),mock(VolumeScanAuditService.class));first.recordStop("BTCUSDT","LONG","SHORT","p1",java.time.Instant.now());LaplaceCoinPoolService restarted=new LaplaceCoinPoolService(client,repository,mock(LaplacePaperPositionRepository.class),mock(LaplaceCapitalSnapshotRepository.class),mock(VolumeScanAuditService.class));assertThat(restarted.allowAfterOppositeSignal("BTCUSDT","LONG")).isFalse();assertThat(restarted.allowAfterOppositeSignal("BTCUSDT","NONE")).isFalse();assertThat(restarted.allowAfterOppositeSignal("BTCUSDT","LONG")).isFalse();assertThat(restarted.allowAfterOppositeSignal("BTCUSDT","SHORT")).isTrue();assertThat(row.getUnblockedBySignalSide()).isEqualTo("SHORT");}
}
