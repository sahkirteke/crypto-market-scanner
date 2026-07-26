package com.crypto.laplace.stop;

import static org.mockito.Mockito.*;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.BookTicker;
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
    @Test void doesNotStopBeforeThreshold(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));when(client.getKlines("BTCUSDT","5m",40)).thenReturn(List.of(candle("101","94.5001")));service.checkOpenPositions();verify(execution,never()).closeByStop(any(),any());verify(client).getKlines("BTCUSDT","5m",40);}
    @Test void liveBidObservedOnlyAfterEntryCanTriggerBeforeCandleCloses(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));when(client.getAllBookTickers()).thenReturn(List.of(BookTicker.builder().symbol("BTCUSDT").bidPrice(new BigDecimal("95")).askPrice(new BigDecimal("95.1")).build()),List.of(BookTicker.builder().symbol("BTCUSDT").bidPrice(new BigDecimal("94.4")).askPrice(new BigDecimal("94.5")).build()));service.checkLiveBookPrices();verify(execution,never()).closeByStop(any(),any());service.checkLiveBookPrices();verify(execution).closeByStop(eq("position"),any());}
    @Test void restartCatchUpUsesFortyCandleChunksAndStopsOnOldTouch(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");p.setEntryTime(Instant.now().minusSeconds(8*3600));when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));List<Kline> batch=new java.util.ArrayList<>();Instant start=Instant.now().minusSeconds(7*3600);for(int i=0;i<40;i++){Instant close=start.plusSeconds(i*300L);batch.add(Kline.builder().openTime(close.minusSeconds(299)).closeTime(close).high(new BigDecimal("101")).low(new BigDecimal(i==5?"94.5":"99")).close(new BigDecimal("100")).closed(true).build());}when(client.getKlines(eq("BTCUSDT"),eq("5m"),eq(40),any())).thenReturn(batch);service.catchUpOpenPositions();verify(execution).closeByStop(eq("position"),eq(batch.get(5)));verify(client).getKlines(eq("BTCUSDT"),eq("5m"),eq(40),any());}

    private void assertStops(LaplacePaperPositionEntity p,Kline candle){when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));when(client.getKlines("BTCUSDT","5m",40)).thenReturn(List.of(candle));service.checkOpenPositions();verify(execution).closeByStop("position",candle);verify(client).getKlines("BTCUSDT","5m",40);}
    private LaplacePaperPositionEntity position(PositionSide side,String stop){return LaplacePaperPositionEntity.builder().id("position").strategy(LaplacePaperExecutionService.STRATEGY).symbol("BTCUSDT").side(side).status(LaplacePositionStatus.OPEN).entryTime(Instant.now().minusSeconds(1800)).stopPrice(new BigDecimal(stop)).build();}
    private Kline candle(String high,String low){Instant close=Instant.now().minusSeconds(30);return Kline.builder().openTime(close.minusSeconds(299)).closeTime(close).high(new BigDecimal(high)).low(new BigDecimal(low)).close(new BigDecimal("100")).closed(true).build();}
}
