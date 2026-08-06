package com.crypto.laplace.scheduler;

import static org.mockito.Mockito.*;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.service.FiveMinuteKlineService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LaplaceStopLossSchedulerTest {
 @Test void catchesUpCandlesChronologicallyAndStopsAfterClose(){
  var positions=mock(LaplacePaperPositionRepository.class);var klines=mock(FiveMinuteKlineService.class);var execution=mock(LaplacePaperExecutionService.class);
  var position=LaplacePaperPositionEntity.builder().id("position").symbol("BTCUSDT").status(LaplacePositionStatus.OPEN).build();
  var first=candle("2026-08-06T10:05:00Z");var second=candle("2026-08-06T10:10:00Z");
  when(positions.findById("position")).thenReturn(Optional.of(position));when(klines.loadClosedThrough(eq("BTCUSDT"),any(),eq(500))).thenReturn(List.of(first,second));
  when(execution.evaluateClosedFiveMinuteCandle("position",first)).thenReturn(PositionManagementOutcome.CLOSED_STOP_LOSS);
  new LaplaceStopLossScheduler(positions,klines,execution).evaluate("position","BTCUSDT");
  verify(execution).evaluateClosedFiveMinuteCandle("position",first);verify(execution,never()).evaluateClosedFiveMinuteCandle("position",second);
 }
 private Kline candle(String close){return Kline.builder().openTime(Instant.parse(close).minusSeconds(300)).closeTime(Instant.parse(close)).low(BigDecimal.ONE).high(BigDecimal.TEN).closed(true).build();}
}
