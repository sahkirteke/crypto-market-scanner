package com.crypto.laplace.scheduler;

import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Loads one immutable closed 5m candle per symbol and isolates variant failures. */
@Slf4j @Component @RequiredArgsConstructor
public class LaplaceStopLossFanOutScheduler {
 private final LaplaceMarketDataGate gate; private final LaplaceRuntimeService trueRuntime; private final LaplaceInvertedFalseRuntimeService falseRuntime;
 private final LaplacePaperPositionRepository truePositions; private final LaplaceInvertedFalsePositionRepository falsePositions;
 private final FiveMinuteKlineService klines; private final LaplaceStopLossScheduler trueEvaluator; private final LaplaceInvertedFalseStopLossScheduler falseEvaluator;
 private final AtomicBoolean running=new AtomicBoolean();
 @Scheduled(cron="${trading.laplace.stop-loss-cron}",zone="${trading.laplace.zone}") public void evaluate(){
  boolean runTrue=gate.enabledTrue()&&trueRuntime.isActive(),runFalse=gate.enabledFalse()&&falseRuntime.isActive();
  if((!runTrue&&!runFalse)||!running.compareAndSet(false,true))return;
  try{
   List<LaplacePaperPositionEntity> a=runTrue?truePositions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN):List.of();
   List<LaplaceInvertedFalsePositionEntity> b=runFalse?falsePositions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN):List.of();
   Set<String> symbols=new HashSet<>();a.forEach(p->symbols.add(p.getSymbol()));b.forEach(p->symbols.add(p.getSymbol()));
   symbols.forEach(symbol->fanOut(symbol,a,b));
  }finally{running.set(false);}
 }
 private void fanOut(String symbol,List<LaplacePaperPositionEntity>a,List<LaplaceInvertedFalsePositionEntity>b){
  try{var candle=klines.loadLatestClosed(symbol);
   a.stream().filter(p->symbol.equals(p.getSymbol())).forEach(p->independently("INVERTED_TRUE",()->trueEvaluator.evaluate(p.getId(),symbol,candle)));
   b.stream().filter(p->symbol.equals(p.getSymbol())).forEach(p->independently("INVERTED_FALSE",()->falseEvaluator.evaluate(p.getId(),symbol,candle)));
  }catch(RuntimeException failure){log.error("LAPLACE_STOP_LOSS_CANDLE_LOAD_FAILED symbol={}",symbol,failure);}
 }
 private void independently(String variant,Runnable action){try{action.run();}catch(RuntimeException failure){log.error("LAPLACE_STOP_LOSS_VARIANT_FAILED variant={}",variant,failure);}}
}
