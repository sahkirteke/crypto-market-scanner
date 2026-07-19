package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;import lombok.extern.slf4j.Slf4j;import org.springframework.stereotype.Service;

/** Keeps raw strategy signals untouched and applies the contrarian mapping only at execution. */
@Slf4j @Service @RequiredArgsConstructor
public class LaplacePaperTradeCoordinator {
 private final LaplaceStrategyProperties config;private final LaplacePaperPositionRepository positions;private final LaplacePaperExecutionService execution;private final LaplaceTradeJsonlWriter writer;private final ConcurrentHashMap<String,ReentrantLock> locks=new ConcurrentHashMap<>();private final ConcurrentHashMap<String,LaplaceSignal> rawStates=new ConcurrentHashMap<>();private final AtomicBoolean paperDisabledLogged=new AtomicBoolean(false);
 public Set<String> managementSymbols(){Set<String>x=new HashSet<>();for(var p:positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN))x.add(p.getSymbol());return Set.copyOf(x);}
 public PositionSide mapRawSignalToExecutionSide(LaplaceSignal raw){return switch(raw){case LONG->PositionSide.SHORT;case SHORT->PositionSide.LONG;case NONE->null;};}
 /** Startup history establishes a raw baseline only; it deliberately never executes. */
 public void initializeBaseline(String symbol,LaplaceSignal raw){rawStates.put(symbol,raw);log.info("LAPLACE_STARTUP_RAW_BASELINE_INITIALIZED symbol={} startupRawSignalState={}",symbol,raw);}
 public void onSignal(LaplaceSignalResult signal,boolean inEntryUniverse){
  if(signal==null||signal.startupState()!=StartupState.ACTIVE||signal.postStartupClosedBarCount()<1)return;
  if(!config.getLaplace().isPaperExecutionEnabled()){if(signal.entrySignal()!=LaplaceSignal.NONE&&paperDisabledLogged.compareAndSet(false,true))log.warn("LAPLACE_PAPER_EXECUTION_DISABLED strategy={} symbol={}",LaplacePaperExecutionService.STRATEGY,signal.symbol());return;}
  ReentrantLock lock=locks.computeIfAbsent(signal.symbol(),s->new ReentrantLock());lock.lock();try {LaplaceSignal previous=rawStates.put(signal.symbol(),signal.entrySignal());if(previous==null){log.info("LAPLACE_STARTUP_RAW_BASELINE_INITIALIZED symbol={} startupRawSignalState={}",signal.symbol(),signal.entrySignal());return;}boolean fresh=previous!=signal.entrySignal();PositionSide effective=mapRawSignalToExecutionSide(signal.entrySignal());log.info("LAPLACE_FRESH_SIGNAL rawSignal={} effectiveExecutionSide={} previousRawState={} currentRawState={} isFreshSignal={} currentPosition={} requestedAction={}",signal.entrySignal(),effective,previous,signal.entrySignal(),fresh,current(signal.symbol()),fresh&&effective!=null?"EVALUATE":"NONE");if(fresh)coordinate(signal,inEntryUniverse,effective);}finally{lock.unlock();}
 }
 private String current(String symbol){var x=positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,symbol,LaplacePositionStatus.OPEN);return x.isEmpty()?"FLAT":x.getFirst().getSide().name();}
 private void coordinate(LaplaceSignalResult signal,boolean inEntryUniverse,PositionSide effective){
  List<LaplacePaperPositionEntity> open=positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,signal.symbol(),LaplacePositionStatus.OPEN);if(open.size()>1){failure(signal,"CONFLICT","NONE","POSITION_STATE_CONFLICT",null,false);return;}
  if(open.isEmpty()){if(effective==null||!signal.eligibleForExecution())return;if(!inEntryUniverse){failure(signal,"FLAT","ENTRY","SYMBOL_OUTSIDE_ENTRY_UNIVERSE",null,false);return;}try{execution.open(signal,effective,null,"FLAT");writer.drain();}catch(RuntimeException e){failure(signal,"FLAT","ENTRY",reason(e,"ENTRY_EXECUTION_FAILED"),e,true);}return;}
  LaplacePaperPositionEntity p=open.getFirst();if(effective==null||p.getSide()==effective){log.info("LAPLACE_SAME_EFFECTIVE_SIDE_SIGNAL_IGNORED symbol={} side={}",signal.symbol(),p.getSide());return;}
  PositionSide reversalTarget=mapRawSignalToExecutionSide(signal.strongReversalSignal());if(reversalTarget==null||reversalTarget!=effective)return;
  try{execution.reverse(signal,p,effective,inEntryUniverse);writer.drain();}catch(RuntimeException e){failure(signal,p.getSide().name(),"REVERSAL",reason(e,"REVERSAL_CLOSE_FAILED"),e,true);}
 }
 private void failure(LaplaceSignalResult s,String current,String action,String reason,Throwable e,boolean retry){writer.failure(s.symbol(),s.signalCandleCloseTime(),current,action,reason,e,retry);writer.drain();}
 private String reason(RuntimeException e,String fallback){return e.getMessage()!=null&&Set.of("EXECUTION_PRICE_UNAVAILABLE","INVALID_QUANTITY","INVALID_NOTIONAL","POSITION_STATE_CONFLICT","DUPLICATE_EXECUTION_BLOCKED","REVERSAL_OPEN_FAILED","INSUFFICIENT_PAPER_BALANCE").contains(e.getMessage())?e.getMessage():fallback;}
}
