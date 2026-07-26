package com.crypto.laplace.stop;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.*;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplaceFiveMinuteStopServiceTest {
    private BinanceFuturesClient client;
    private LaplacePaperPositionRepository positions;
    private LaplacePaperExecutionService execution;
    private LaplaceFiveMinuteStopService service;

    @BeforeEach void setUp(){client=mock(BinanceFuturesClient.class);positions=mock(LaplacePaperPositionRepository.class);execution=mock(LaplacePaperExecutionService.class);service=new LaplaceFiveMinuteStopService(client,positions,execution,mock(LaplacePaperTradeCoordinator.class));}

    @Test void longStopsWhenLowTouchesExactFivePointFivePercentLevel(){assertStops(position(PositionSide.LONG,"94.5"),candle("100","94.5"));}
    @Test void shortStopsWhenHighTouchesExactFivePointFivePercentLevel(){assertStops(position(PositionSide.SHORT,"105.5"),candle("105.5","99"));}
    @Test void doesNotStopBeforeThreshold(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));when(client.getKlines("BTCUSDT","5m",40)).thenReturn(List.of(candle("101","94.5001")));service.checkOpenPositions();verify(execution,never()).closeByStop(any(),any(),any(),any());verify(client).getKlines("BTCUSDT","5m",40);}
    @Test void restartCatchUpUsesFortyCandleChunksAndStopsOnOldTouch(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");p.setEntryTime(Instant.now().minusSeconds(8*3600));when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));List<Kline> batch=new java.util.ArrayList<>();Instant start=Instant.now().minusSeconds(7*3600);for(int i=0;i<40;i++){Instant close=start.plusSeconds(i*300L);batch.add(Kline.builder().openTime(close.minusSeconds(299)).closeTime(close).open(new BigDecimal("100")).high(new BigDecimal("101")).low(new BigDecimal(i==5?"94.5":"99")).close(new BigDecimal("100")).closed(true).build());}when(client.getKlines(eq("BTCUSDT"),eq("5m"),eq(40),any())).thenReturn(batch);service.catchUpOpenPositions();verify(execution).closeByStop(eq("position"),eq(batch.get(5)),eq(new BigDecimal("94.5")),eq("FIVE_MINUTE_STOP_SIMULATION"));verify(client).getKlines(eq("BTCUSDT"),eq("5m"),eq(40),any());}
    @Test void entryPartialCandleIsExcludedAndFirstFullCandleIsChecked(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");Instant entryOpen=LaplaceFiveMinuteStopService.entryCandleOpen(p);Kline partial=Kline.builder().openTime(entryOpen).closeTime(entryOpen.plusSeconds(299)).open(new BigDecimal("100")).high(new BigDecimal("101")).low(new BigDecimal("90")).close(new BigDecimal("100")).closed(true).build();Kline full=Kline.builder().openTime(entryOpen.plusSeconds(300)).closeTime(entryOpen.plusSeconds(599)).open(new BigDecimal("100")).high(new BigDecimal("101")).low(new BigDecimal("94.5")).close(new BigDecimal("100")).closed(true).build();service.inspect(p,List.of(partial,full),entryOpen.plusSeconds(600));verify(execution).closeByStop(eq("position"),eq(full),eq(new BigDecimal("94.5")),eq("FIVE_MINUTE_STOP_SIMULATION"));}
    @Test void openFiveMinuteCandleIsIgnored(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");Kline open=candle("101","90");open.setClosed(false);service.inspect(p,List.of(open),Instant.now());verify(execution,never()).closeByStop(any(),any(),any(),any());}
    @Test void historicalGapUsesCandleOpenAndNormalTouchUsesStopPrice(){LaplacePaperPositionEntity longPosition=position(PositionSide.LONG,"94.5");Kline longGap=candle("95","90");longGap.setOpen(new BigDecimal("93"));assertThat(LaplaceFiveMinuteStopService.historicalExecutionPrice(longPosition,longGap)).isEqualByComparingTo("93");Kline touch=candle("100","94.5");assertThat(LaplaceFiveMinuteStopService.historicalExecutionPrice(longPosition,touch)).isEqualByComparingTo("94.5");LaplacePaperPositionEntity shortPosition=position(PositionSide.SHORT,"105.5");Kline shortGap=candle("110","105");shortGap.setOpen(new BigDecimal("107"));assertThat(LaplaceFiveMinuteStopService.historicalExecutionPrice(shortPosition,shortGap)).isEqualByComparingTo("107");}

    private void assertStops(LaplacePaperPositionEntity p,Kline candle){when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));when(client.getKlines("BTCUSDT","5m",40)).thenReturn(List.of(candle));service.checkOpenPositions();verify(execution).closeByStop(eq("position"),eq(candle),any(),eq("FIVE_MINUTE_STOP_SIMULATION"));verify(client).getKlines("BTCUSDT","5m",40);}
    private LaplacePaperPositionEntity position(PositionSide side,String stop){return LaplacePaperPositionEntity.builder().id("position").strategy(LaplacePaperExecutionService.STRATEGY).symbol("BTCUSDT").side(side).status(LaplacePositionStatus.OPEN).entryTime(Instant.now().minusSeconds(1800)).stopPrice(new BigDecimal(stop)).build();}
    private Kline candle(String high,String low){Instant close=Instant.now().minusSeconds(30);return Kline.builder().openTime(close.minusSeconds(299)).closeTime(close).open(new BigDecimal("100")).high(new BigDecimal(high)).low(new BigDecimal(low)).close(new BigDecimal("100")).closed(true).build();}
}
