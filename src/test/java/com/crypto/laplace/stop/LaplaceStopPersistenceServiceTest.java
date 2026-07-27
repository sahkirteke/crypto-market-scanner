package com.crypto.laplace.stop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.*;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LaplaceStopPersistenceServiceTest {
 @Test void checkpointAdvancesMonotonicallyOnFreshManagedEntityWithoutMerge(){Fixture f=new Fixture();Instant ten=Instant.parse("2026-07-27T10:00:00Z"),tenFive=ten.plusSeconds(300);f.p.setLastStopCheckedCandleOpenTime(tenFive);var stale=new LaplaceFiveMinuteStopService.StopPositionSnapshot("p","BTCUSDT",PositionSide.LONG,new BigDecimal("100"),new BigDecimal("94.5"),ten.minusSeconds(3600),ten);var result=f.service.persist(stale,List.of(candle(ten.plusSeconds(120),"99")),ten.plusSeconds(600));assertThat(result.outcome()).isEqualTo(LaplaceStopPersistenceService.Outcome.NO_CHANGE);assertThat(f.p.getLastStopCheckedCandleOpenTime()).isEqualTo(tenFive);verify(f.repo,never()).save(any());}
 @Test void noStopAdvancesToLastClosedCandleAndNextPassSkipsIt(){Fixture f=new Fixture();Instant open=Instant.now().minusSeconds(600);var snapshot=f.snapshot();Kline c=candle(open,"99");var first=f.service.persist(snapshot,List.of(c),Instant.now());assertThat(first.outcome()).isEqualTo(LaplaceStopPersistenceService.Outcome.CHECKPOINT_ADVANCED);assertThat(f.p.getLastStopCheckedCandleOpenTime()).isEqualTo(open);var second=f.service.persist(snapshot,List.of(c),Instant.now());assertThat(second.outcome()).isEqualTo(LaplaceStopPersistenceService.Outcome.NO_CHANGE);verify(f.repo,never()).save(any());}
 @Test void stopTouchClosesOnceAdvancesCheckpointAndPersistsReentryLock(){Fixture f=new Fixture();Instant open=Instant.now().minusSeconds(600);Kline c=candle(open,"94.5");when(f.execution.closeByStop(eq("p"),eq(c),eq(new BigDecimal("94.5")),eq("FIVE_MINUTE_STOP_SIMULATION"))).thenReturn(Optional.of(f.p));var result=f.service.persist(f.snapshot(),List.of(c),Instant.now());assertThat(result.outcome()).isEqualTo(LaplaceStopPersistenceService.Outcome.STOP_CLOSED);assertThat(f.p.getLastStopCheckedCandleOpenTime()).isEqualTo(open);verify(f.execution,times(1)).closeByStop(any(),any(),any(),any());verify(f.coordinator).onStopLossClosed(eq("BTCUSDT"),eq("LONG"),eq("LONG"),eq("p"),any());}
 @Test void alreadyClosedRowIsNoOpAndCannotBeReopenedOrCheckpointed(){Fixture f=new Fixture();f.p.setStatus(LaplacePositionStatus.CLOSED_BY_SIGNAL);Instant before=f.p.getLastStopCheckedCandleOpenTime();var result=f.service.persist(f.snapshot(),List.of(candle(Instant.now().minusSeconds(600),"94")),Instant.now());assertThat(result.outcome()).isEqualTo(LaplaceStopPersistenceService.Outcome.ALREADY_CLOSED);assertThat(f.p.getLastStopCheckedCandleOpenTime()).isEqualTo(before);verifyNoInteractions(f.execution,f.coordinator);}
 private static Kline candle(Instant open,String low){return Kline.builder().openTime(open).closeTime(open.plusSeconds(299)).open(new BigDecimal("100")).high(new BigDecimal("101")).low(new BigDecimal(low)).close(new BigDecimal("100")).closed(true).build();}
 private static class Fixture{final LaplacePaperPositionRepository repo=mock(LaplacePaperPositionRepository.class);final LaplacePaperExecutionService execution=mock(LaplacePaperExecutionService.class);final LaplacePaperTradeCoordinator coordinator=mock(LaplacePaperTradeCoordinator.class);final LaplacePaperPositionEntity p=LaplacePaperPositionEntity.builder().id("p").symbol("BTCUSDT").side(PositionSide.LONG).status(LaplacePositionStatus.OPEN).entryRawSignal("LONG").entryExecutionPrice(new BigDecimal("100")).entryTime(Instant.now().minusSeconds(3600)).stopPrice(new BigDecimal("94.5")).exitTime(Instant.now()).build();final LaplaceStopPersistenceService service=new LaplaceStopPersistenceService(repo,execution,coordinator);Fixture(){when(repo.findByIdForUpdate("p")).thenReturn(Optional.of(p));}LaplaceFiveMinuteStopService.StopPositionSnapshot snapshot(){return LaplaceFiveMinuteStopService.StopPositionSnapshot.from(p);}}
}
