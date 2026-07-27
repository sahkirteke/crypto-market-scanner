package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.pool.*;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.stereotype.Service;

/** Keeps raw strategy signals untouched and applies the contrarian mapping only at execution. */
@Slf4j @Service
public class LaplacePaperTradeCoordinator {
 private final LaplaceStrategyProperties config;private final LaplacePaperPositionRepository positions;private final LaplacePaperExecutionService execution;private final LaplaceTradeJsonlWriter writer;private final ConcurrentHashMap<String,ReentrantLock> locks=new ConcurrentHashMap<>();private final ConcurrentHashMap<String,LaplaceSignal> rawStates=new ConcurrentHashMap<>();private final LaplaceCoinPoolService coinPool;
 @Autowired
 public LaplacePaperTradeCoordinator(LaplaceStrategyProperties config,LaplacePaperPositionRepository positions,LaplacePaperExecutionService execution,LaplaceTradeJsonlWriter writer,LaplaceCoinPoolService coinPool){this.config=config;this.positions=positions;this.execution=execution;this.writer=writer;this.coinPool=coinPool;}
 public LaplacePaperTradeCoordinator(LaplaceStrategyProperties config,LaplacePaperPositionRepository positions,LaplacePaperExecutionService execution,LaplaceTradeJsonlWriter writer){this.config=config;this.positions=positions;this.execution=execution;this.writer=writer;this.coinPool=null;}
private final AtomicBoolean paperDisabledLogged=new AtomicBoolean(false);
 private final ConcurrentHashMap<String,Instant> stopCooldowns=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,LaplaceSignal> oppositeSignalLocks=new ConcurrentHashMap<>();
 private volatile Instant cycleStartedAt=Instant.EPOCH; private final CycleMetrics metrics=new CycleMetrics();
 public void beginCycle(Instant startedAt){cycleStartedAt=startedAt;metrics.reset();}
 public CycleSummary cycleSummary(){return metrics.snapshot();}
 public Set<String> managementSymbols(){Set<String>x=new HashSet<>();for(var p:positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN))x.add(p.getSymbol());return Set.copyOf(x);}
 public PositionSide mapRawSignalToExecutionSide(LaplaceSignal raw){return switch(raw){case LONG->PositionSide.SHORT;case SHORT->PositionSide.LONG;case NONE->null;};}
 /** Startup history establishes a raw baseline only; it deliberately never executes. */
 public void initializeBaseline(String symbol,LaplaceSignal raw){rawStates.put(symbol,raw);if(coinPool!=null)coinPool.baseline(symbol, trend(raw));log.info("LAPLACE_STARTUP_RAW_BASELINE_INITIALIZED symbol={} startupRawSignalState={}",symbol,raw);}
 public void onStopLossClosed(String symbol,String entryRawSignal,String positionSide,String positionId,Instant stoppedAt){
  if(symbol==null||entryRawSignal==null)return;
  if(coinPool!=null)coinPool.recordStop(symbol,entryRawSignal,positionSide,positionId,stoppedAt);
  if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.REQUIRE_OPPOSITE_SIGNAL)oppositeSignalLocks.put(symbol,LaplaceSignal.valueOf(entryRawSignal));
  if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.ALLOW){rawStates.put(symbol,LaplaceSignal.NONE);return;}
  try{LaplaceSignal stoppedSignal=LaplaceSignal.valueOf(entryRawSignal);rawStates.putIfAbsent(symbol,stoppedSignal);if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.COOLDOWN)stopCooldowns.put(symbol,Instant.now().plusSeconds(config.getLaplace().getStopReentryCooldownMinutes()*60));log.info("LAPLACE_STOP_REENTRY_LOCKED symbol={} rawSignal={} policy={} entryBlockedReason=WAITING_FOR_NEW_SIGNAL",symbol,rawStates.get(symbol),config.getLaplace().getStopReentryPolicy());}
  catch(IllegalArgumentException invalid){log.warn("LAPLACE_STOP_REENTRY_LOCK_SKIPPED symbol={} invalidRawSignal={}",symbol,entryRawSignal);}
 }
 public void onSignal(LaplaceSignalResult signal,boolean entryUniverseSnapshotContainsSymbol){
  if(signal==null||signal.startupState()!=StartupState.ACTIVE||signal.postStartupClosedBarCount()<1)return;
  if(!config.getLaplace().isPaperExecutionEnabled()){if(signal.entrySignal()!=LaplaceSignal.NONE&&paperDisabledLogged.compareAndSet(false,true))log.warn("LAPLACE_PAPER_EXECUTION_DISABLED strategy={} symbol={}",LaplacePaperExecutionService.STRATEGY,signal.symbol());return;}
  ReentrantLock lock=locks.computeIfAbsent(signal.symbol(),s->new ReentrantLock());lock.lock();try {
   // The iteration snapshot is deliberately not authoritative: observeTrend may activate this symbol now.
   boolean poolActive=coinPool==null || coinPool.observeTrend(signal.symbol(),trend(signal.entrySignal()));
   if(!entryUniverseSnapshotContainsSymbol&&poolActive)metrics.activated.incrementAndGet();else if(!poolActive)metrics.waiting.incrementAndGet();
   boolean oppositeAllowed=oppositeSignalAllowed(signal.symbol(),signal.entrySignal());
   if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.COOLDOWN&&stopCooldowns.getOrDefault(signal.symbol(),Instant.EPOCH).isBefore(Instant.now())){rawStates.put(signal.symbol(),LaplaceSignal.NONE);stopCooldowns.remove(signal.symbol());}
   LaplaceSignal previous=rawStates.put(signal.symbol(),signal.entrySignal());if(previous==null){log.info("LAPLACE_STARTUP_RAW_BASELINE_INITIALIZED symbol={} startupRawSignalState={}",signal.symbol(),signal.entrySignal());return;}
   boolean fresh=previous!=signal.entrySignal();if(fresh)metrics.fresh.incrementAndGet();PositionSide effective=mapRawSignalToExecutionSide(signal.entrySignal());
   log.info("LAPLACE_FRESH_SIGNAL rawSignal={} effectiveExecutionSide={} previousRawState={} currentRawState={} isFreshSignal={} currentPosition={} requestedAction={}",signal.entrySignal(),effective,previous,signal.entrySignal(),fresh,current(signal.symbol()),fresh&&effective!=null?"EVALUATE":"NONE");
   if(fresh&&!oppositeAllowed)metrics.blockedByReentry.incrementAndGet();
   if(fresh&&oppositeAllowed)coordinate(signal,poolActive,effective,entryUniverseSnapshotContainsSymbol,Instant.now());
  }finally{lock.unlock();}
 }
 private boolean oppositeSignalAllowed(String symbol,LaplaceSignal current){if(config.getLaplace().getStopReentryPolicy()!=StopReentryPolicy.REQUIRE_OPPOSITE_SIGNAL)return true;if(coinPool!=null)return coinPool.allowAfterOppositeSignal(symbol,current.name());LaplaceSignal stopped=oppositeSignalLocks.get(symbol);if(stopped==null)return true;if(current==LaplaceSignal.NONE||current==stopped)return false;oppositeSignalLocks.remove(symbol);return true;}
 private LaplaceTrend trend(LaplaceSignal s){return s==LaplaceSignal.LONG?LaplaceTrend.LONG:s==LaplaceSignal.SHORT?LaplaceTrend.SHORT:LaplaceTrend.NEUTRAL;}
 private String current(String symbol){var x=positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,symbol,LaplacePositionStatus.OPEN);return x.isEmpty()?"FLAT":x.getFirst().getSide().name();}
 private void coordinate(LaplaceSignalResult signal,boolean poolActiveAfterTransition,PositionSide effective,boolean entryUniverseSnapshotContainsSymbol,Instant eligibilityEvaluatedAt){
  List<LaplacePaperPositionEntity> open=positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,signal.symbol(),LaplacePositionStatus.OPEN);if(open.size()>1){failure(signal,"CONFLICT","NONE","POSITION_STATE_CONFLICT",null,false);return;}
  LaplaceCoinPoolService.EntryEligibility eligibility=coinPool==null?null:coinPool.isCurrentlyEligibleForEntry(signal.symbol());
  boolean currentlyEligible=coinPool==null?poolActiveAfterTransition:eligibility.allowed();
  if(open.isEmpty()){if(effective==null||!signal.eligibleForExecution())return;metrics.entryAttempts.incrementAndGet();if(!currentlyEligible){
    Map<String,Object> audit=eligibilityAudit(eligibility,entryUniverseSnapshotContainsSymbol,"FLAT",eligibilityEvaluatedAt);
    String failureReason=eligibility!=null&&eligibility.blockReason()!=null?eligibility.blockReason():"SYMBOL_OUTSIDE_ENTRY_UNIVERSE";
    if("SYMBOL_OUTSIDE_ENTRY_UNIVERSE".equals(failureReason)){metrics.outsideUniverse.incrementAndGet();if(eligibility!=null&&eligibility.poolState()==LaplaceCoinPoolState.ACTIVE&&signal.eligibleForExecution())log.error("LAPLACE_STALE_ENTRY_UNIVERSE_INCONSISTENCY symbol={} poolState=ACTIVE eligibleForExecution=true currentPosition=FLAT freshSignal=true snapshotContains={}",signal.symbol(),entryUniverseSnapshotContainsSymbol);}
    metrics.failures.incrementAndGet();failure(signal,"FLAT","ENTRY",failureReason,null,false,audit);return;
   }try{execution.open(signal,effective,null,"FLAT");metrics.entryOpened.incrementAndGet();writer.drain();}catch(RuntimeException e){metrics.failures.incrementAndGet();failure(signal,"FLAT","ENTRY",reason(e,"ENTRY_EXECUTION_FAILED"),e,true);}return;}
  metrics.alreadyOpen.incrementAndGet();
  LaplacePaperPositionEntity p=open.getFirst();if(effective==null||p.getSide()==effective){log.info("LAPLACE_SAME_EFFECTIVE_SIDE_SIGNAL_IGNORED symbol={} side={}",signal.symbol(),p.getSide());return;}
  PositionSide reversalTarget=mapRawSignalToExecutionSide(signal.strongReversalSignal());if(reversalTarget==null||reversalTarget!=effective)return;
  boolean reversalTargetAllowed=eligibility==null?poolActiveAfterTransition:eligibility.poolState()==LaplaceCoinPoolState.ACTIVE&&!eligibility.reentryBlocked();
  try{execution.reverse(signal,p,effective,reversalTargetAllowed);writer.drain();}catch(RuntimeException e){failure(signal,p.getSide().name(),"REVERSAL",reason(e,"REVERSAL_CLOSE_FAILED"),e,true);}
 }
 private void failure(LaplaceSignalResult s,String current,String action,String reason,Throwable e,boolean retry){writer.failure(s.symbol(),s.signalCandleCloseTime(),current,action,reason,e,retry);writer.drain();}
 private void failure(LaplaceSignalResult s,String current,String action,String reason,Throwable e,boolean retry,Map<String,Object> audit){writer.failure(s.symbol(),s.signalCandleCloseTime(),current,action,reason,e,retry,audit);writer.drain();}
 private Map<String,Object> eligibilityAudit(LaplaceCoinPoolService.EntryEligibility e,boolean snapshot,String position,Instant evaluatedAt){Map<String,Object>x=new LinkedHashMap<>();x.put("poolStateAtEvaluation",e==null?null:e.poolState());x.put("includedInPoolAtEvaluation",e!=null&&e.includedInPool());x.put("newEntryAllowed",e!=null&&e.allowed());x.put("entryUniverseSnapshotContainsSymbol",snapshot);x.put("reentryBlocked",e!=null&&e.reentryBlocked());x.put("positionState",position);x.put("eligibilityEvaluatedAt",evaluatedAt);x.put("cycleStartedAt",cycleStartedAt);x.put("poolStateUpdatedAt",e==null?null:e.poolStateUpdatedAt());return x;}
 public record CycleSummary(int waitingSymbols,int activatedSymbols,int freshSignalCount,int entryAttemptCount,int entryOpenedCount,int outsideEntryUniverseCount,int blockedByReentryCount,int alreadyOpenCount,int failureCount){}
 private static final class CycleMetrics{final java.util.concurrent.atomic.AtomicInteger waiting=new java.util.concurrent.atomic.AtomicInteger(),activated=new java.util.concurrent.atomic.AtomicInteger(),fresh=new java.util.concurrent.atomic.AtomicInteger(),entryAttempts=new java.util.concurrent.atomic.AtomicInteger(),entryOpened=new java.util.concurrent.atomic.AtomicInteger(),outsideUniverse=new java.util.concurrent.atomic.AtomicInteger(),blockedByReentry=new java.util.concurrent.atomic.AtomicInteger(),alreadyOpen=new java.util.concurrent.atomic.AtomicInteger(),failures=new java.util.concurrent.atomic.AtomicInteger();void reset(){waiting.set(0);activated.set(0);fresh.set(0);entryAttempts.set(0);entryOpened.set(0);outsideUniverse.set(0);blockedByReentry.set(0);alreadyOpen.set(0);failures.set(0);}CycleSummary snapshot(){return new CycleSummary(waiting.get(),activated.get(),fresh.get(),entryAttempts.get(),entryOpened.get(),outsideUniverse.get(),blockedByReentry.get(),alreadyOpen.get(),failures.get());}}
 private String reason(RuntimeException e,String fallback){return e.getMessage()!=null&&Set.of("EXECUTION_PRICE_UNAVAILABLE","INVALID_QUANTITY","INVALID_NOTIONAL","POSITION_STATE_CONFLICT","DUPLICATE_EXECUTION_BLOCKED","REVERSAL_OPEN_FAILED").contains(e.getMessage())?e.getMessage():fallback;}
}
