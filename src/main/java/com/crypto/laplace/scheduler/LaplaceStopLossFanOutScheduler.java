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

/** Loads one immutable closed 5m candle per TRUE position symbol. */
@Slf4j @Component @RequiredArgsConstructor
public class LaplaceStopLossFanOutScheduler {
 private final LaplaceMarketDataGate gate; private final LaplaceRuntimeService trueRuntime;
 private final LaplacePaperPositionRepository truePositions;
 private final FiveMinuteKlineService klines; private final LaplaceStopLossScheduler trueEvaluator;
 private final AtomicBoolean running=new AtomicBoolean();
 @Scheduled(cron="${trading.laplace.stop-loss-cron}",zone="${trading.laplace.zone}") public void evaluate(){
  if(!gate.allowsMarketData()||!trueRuntime.isActive()||!running.compareAndSet(false,true))return;
  try{
   List<LaplacePaperPositionEntity> positions=truePositions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN);
   positions.stream().map(LaplacePaperPositionEntity::getSymbol).collect(java.util.stream.Collectors.toSet()).forEach(symbol->evaluate(symbol,positions));
  }finally{running.set(false);}
 }
 private void evaluate(String symbol,List<LaplacePaperPositionEntity>positions){
  try{var candle=klines.loadLatestClosed(symbol);
   positions.stream().filter(p->symbol.equals(p.getSymbol())).forEach(p->independently(()->trueEvaluator.evaluate(p.getId(),symbol,candle)));
  }catch(RuntimeException failure){log.error("LAPLACE_STOP_LOSS_CANDLE_LOAD_FAILED symbol={}",symbol,failure);}
 }
 private void independently(Runnable action){try{action.run();}catch(RuntimeException failure){log.error("LAPLACE_STOP_LOSS_EVALUATION_FAILED variant=INVERTED_TRUE",failure);}}
}
