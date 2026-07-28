package com.crypto.laplace.stop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceFiveMinuteStopServiceTest {
 @Test void marketDataAndPersistenceAreSeparateAndDetachedPositionIsNeverSaved(){BinanceFuturesClient client=mock(BinanceFuturesClient.class);LaplacePaperPositionRepository repo=mock(LaplacePaperPositionRepository.class);LaplaceStopPersistenceService persistence=mock(LaplaceStopPersistenceService.class);LaplacePaperExecutionService execution=mock(LaplacePaperExecutionService.class);LaplacePaperPositionEntity detached=position();when(repo.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(detached));when(client.getKlines("BTCUSDT","5m",40)).thenReturn(List.of(candle("101","99")));when(persistence.persist(any(),any(),any())).thenReturn(new LaplaceStopPersistenceService.Result(LaplaceStopPersistenceService.Outcome.CHECKPOINT_ADVANCED,Instant.now()));new LaplaceFiveMinuteStopService(client,repo,persistence,execution).checkOpenPositions();verify(persistence).persist(argThat(s->s.positionId().equals("position")),any(),any());verify(repo,never()).save(any());}
 @Test void restartCatchUpUsesFortyCandleRequests(){BinanceFuturesClient client=mock(BinanceFuturesClient.class);LaplacePaperPositionRepository repo=mock(LaplacePaperPositionRepository.class);LaplaceStopPersistenceService persistence=mock(LaplaceStopPersistenceService.class);LaplacePaperPositionEntity p=position();p.setEntryTime(Instant.now().minusSeconds(8*3600));when(repo.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));List<Kline> batch=java.util.stream.IntStream.range(0,40).mapToObj(i->candle("101","99")).toList();when(client.getKlines(eq("BTCUSDT"),eq("5m"),eq(40),any())).thenReturn(batch);when(persistence.persist(any(),any(),any())).thenReturn(new LaplaceStopPersistenceService.Result(LaplaceStopPersistenceService.Outcome.ALREADY_CLOSED,null));new LaplaceFiveMinuteStopService(client,repo,persistence,mock(LaplacePaperExecutionService.class)).catchUpOpenPositions();verify(client).getKlines(eq("BTCUSDT"),eq("5m"),eq(40),any());}
 @Test void historicalGapAndTouchPricesRemainDeterministic(){LaplacePaperPositionEntity p=position();p.setStopPrice(new BigDecimal("95.4"));Kline gap=candle("95","90");gap.setOpen(new BigDecimal("93"));assertThat(LaplaceFiveMinuteStopService.historicalExecutionPrice(p,gap)).isEqualByComparingTo("93");Kline touch=candle("100","95.4");assertThat(LaplaceFiveMinuteStopService.historicalExecutionPrice(p,touch)).isEqualByComparingTo("95.4");}
 private LaplacePaperPositionEntity position(){return LaplacePaperPositionEntity.builder().id("position").strategy(LaplacePaperExecutionService.STRATEGY).symbol("BTCUSDT").side(PositionSide.LONG).status(LaplacePositionStatus.OPEN).entryExecutionPrice(new BigDecimal("100")).entryTime(Instant.now().minusSeconds(1800)).stopPrice(new BigDecimal("95.4")).build();}
 private Kline candle(String high,String low){Instant close=Instant.now().minusSeconds(30);return Kline.builder().openTime(close.minusSeconds(299)).closeTime(close).open(new BigDecimal("100")).high(new BigDecimal(high)).low(new BigDecimal(low)).close(new BigDecimal("100")).closed(true).build();}
}
