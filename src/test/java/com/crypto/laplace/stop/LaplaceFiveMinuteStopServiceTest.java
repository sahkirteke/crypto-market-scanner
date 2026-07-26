package com.crypto.laplace.stop;

import static org.mockito.Mockito.*;

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
    @Test void doesNotStopBeforeThreshold(){LaplacePaperPositionEntity p=position(PositionSide.LONG,"94.5");when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));when(client.getKlines("BTCUSDT","5m",40)).thenReturn(List.of(candle("101","94.5001")));service.checkOpenPositions();verify(execution,never()).closeByStop(any(),any());verify(client).getKlines("BTCUSDT","5m",40);}

    private void assertStops(LaplacePaperPositionEntity p,Kline candle){when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(p));when(client.getKlines("BTCUSDT","5m",40)).thenReturn(List.of(candle));service.checkOpenPositions();verify(execution).closeByStop("position",candle);verify(client).getKlines("BTCUSDT","5m",40);}
    private LaplacePaperPositionEntity position(PositionSide side,String stop){return LaplacePaperPositionEntity.builder().id("position").strategy(LaplacePaperExecutionService.STRATEGY).symbol("BTCUSDT").side(side).status(LaplacePositionStatus.OPEN).entryTime(Instant.now().minusSeconds(1800)).stopPrice(new BigDecimal(stop)).build();}
    private Kline candle(String high,String low){Instant close=Instant.now().minusSeconds(30);return Kline.builder().openTime(close.minusSeconds(299)).closeTime(close).high(new BigDecimal(high)).low(new BigDecimal(low)).close(new BigDecimal("100")).closed(true).build();}
}
