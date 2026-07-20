package com.crypto.laplace.scheduler;

import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks only completed ten-minute windows. Missing candles deliberately leave a window retryable. */
@Slf4j @Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="trading.laplace",name="enabled",havingValue="true")
public class LaplaceStopLossScheduler {
 private final LaplacePaperPositionRepository positions; private final BinanceFuturesClient client; private final LaplacePaperExecutionService execution; private final LaplaceStrategyProperties properties;
 @Scheduled(cron="0 10,20,40,50 * * * *", zone="Europe/Istanbul") public void checkStops() {
  Instant end=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES); Instant start=end.minus(Duration.ofMinutes(10));
  for(var p:positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN)) try { check(p,start,end); } catch(RuntimeException e){log.error("LAPLACE_STOP_CHECK_FAILED symbol={} windowStart={} windowEnd={} message={}",p.getSymbol(),start,end,e.getMessage(),e);}
 }
 /** Uses the 30M candle already fetched by the regular scan; it never fetches 5M data. */
 void checkThirtyMinuteWindow(String symbol, Kline candle) {
  if(candle==null||candle.getOpenTime()==null||candle.getCloseTime()==null||Boolean.FALSE.equals(candle.getClosed())) return;
  for(var p:positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN)) {
   if(!symbol.equals(p.getSymbol())) continue;
   try { evaluate(p,candle.getOpenTime(),candle.getCloseTime(),candle.getHigh(),candle.getLow()); }
   catch(RuntimeException e){log.error("LAPLACE_STOP_CHECK_FAILED symbol={} windowStart={} windowEnd={} message={}",p.getSymbol(),candle.getOpenTime(),candle.getCloseTime(),e.getMessage(),e);}
  }
 }
 void check(LaplacePaperPositionEntity p,Instant start,Instant end) {
  if (!windowCanContainOnlyPostEntryPrices(p,start,end)) return;
  List<Kline> candles=client.getKlines(p.getSymbol(),"5m",4); List<Kline> selected=candles.stream().filter(k->start.equals(k.getOpenTime())||start.plus(Duration.ofMinutes(5)).equals(k.getOpenTime())).filter(k->k.getCloseTime()!=null&&!k.getCloseTime().isAfter(Instant.now())&&!Boolean.FALSE.equals(k.getClosed())).toList();
  if(selected.size()!=2){log.error("LAPLACE_STOP_WINDOW_INCOMPLETE symbol={} windowStart={} windowEnd={}",p.getSymbol(),start,end);return;}
  BigDecimal high=selected.stream().map(Kline::getHigh).max(BigDecimal::compareTo).orElseThrow(); BigDecimal low=selected.stream().map(Kline::getLow).min(BigDecimal::compareTo).orElseThrow();
  evaluate(p,start,end,high,low);
 }
 private boolean windowCanContainOnlyPostEntryPrices(LaplacePaperPositionEntity p,Instant start,Instant end) {
  if(p.getEntryTime()==null||!end.isAfter(p.getEntryTime())) return false;
  return !start.isBefore(p.getEntryTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
 }
 private void evaluate(LaplacePaperPositionEntity p,Instant start,Instant end,BigDecimal high,BigDecimal low) {
  if(!windowCanContainOnlyPostEntryPrices(p,start,end)) return;
  BigDecimal pct=executionStopPct(); BigDecimal stop=p.getEntryExecutionPrice().multiply(p.getSide()==PositionSide.LONG?BigDecimal.ONE.subtract(pct.movePointLeft(2)):BigDecimal.ONE.add(pct.movePointLeft(2)));
  if((p.getSide()==PositionSide.LONG&&low.compareTo(stop)<=0)||(p.getSide()==PositionSide.SHORT&&high.compareTo(stop)>=0)) execution.stop(p,start,end,high,low);
 }
 private BigDecimal executionStopPct(){ return properties.getLaplace().getStopLossPct(); }
}
