package com.crypto.laplace.scheduler;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.service.*;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="trading.laplace",name="enabled",havingValue="true")
public class LaplaceThirtyMinuteScheduler {
 private final StartupMarketUniverseService universe; private final ThirtyMinuteKlineService klines; private final LaplaceSignalService signals; private final LaplaceDiagnosticLogService diagnostics;
 private final LaplacePaperTradeCoordinator coordinator;
 private final AtomicBoolean running=new AtomicBoolean(); private final ConcurrentHashMap<String,Instant> lastProcessed=new ConcurrentHashMap<>(); private final ConcurrentHashMap<String,Integer> newBarCounts=new ConcurrentHashMap<>();
 @Scheduled(cron="${trading.laplace.cron}",zone="${trading.laplace.zone}") public void scan(){
  java.util.Set<String> managed=coordinator.managementSymbols();if(!universe.isReady()&&managed.isEmpty()){log.error("LAPLACE_SCAN_SKIPPED marketUniverseReady=false");return;} if(!running.compareAndSet(false,true)){log.info("LAPLACE_SCAN_SKIPPED concurrentRun=true");return;}
  try{java.util.Set<String> symbols=new java.util.HashSet<>(universe.symbols());symbols.addAll(managed);for(String symbol:symbols) process(symbol);}finally{running.set(false);}
 }
 void process(String symbol){ Instant close=null; try{
   List<Kline> data=klines.loadClosed(symbol); close=data.get(data.size()-1).getCloseTime(); Instant previous=lastProcessed.putIfAbsent(symbol,close);
   if(previous==null){newBarCounts.put(symbol,0);log.debug("LAPLACE_SYMBOL_BASELINE symbol={} candleCloseTime={}",symbol,close);return;}
   if(!close.isAfter(previous)){log.debug("LAPLACE_DUPLICATE_CANDLE symbol={} candleCloseTime={}",symbol,close);return;}
   if(!lastProcessed.replace(symbol,previous,close)) {log.debug("LAPLACE_DUPLICATE_CANDLE symbol={} candleCloseTime={}",symbol,close);return;}
   int count=newBarCounts.merge(symbol,1,(a,b)->Math.min(2,a+b)); LaplaceSignalResult result=signals.calculate(symbol,data,count); diagnostics.signal(result);coordinator.onSignal(result,universe.symbols().contains(symbol));
   log.info("LAPLACE_SIGNAL_CALCULATED symbol={} candleCloseTime={} entrySignal={} strongReversalSignal={} startupState={} postStartupClosedBarCount={} eligibleForExecution={}",symbol,close,result.entrySignal(),result.strongReversalSignal(),result.startupState(),count,result.eligibleForExecution());
  }catch(RuntimeException e){log.error("LAPLACE_SYMBOL_FAILED symbol={} candleCloseTime={} errorType={} errorMessage={}",symbol,close,e.getClass().getSimpleName(),e.getMessage(),e);diagnostics.error(symbol,e.getClass().getSimpleName(),e.getMessage());}
 }
}
