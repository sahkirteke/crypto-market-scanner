package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.pool.*;
import com.crypto.laplace.service.LaplaceMarketBreadthService;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.stereotype.Service;

/** Keeps raw strategy signals untouched and applies the contrarian mapping only at execution. */
@Slf4j @Service
public class LaplacePaperTradeCoordinator {
 private final LaplaceStrategyProperties config;private final LaplacePaperPositionRepository positions;private final LaplacePaperExecutionService execution;private final LaplaceTradeJsonlWriter writer;private final ConcurrentHashMap<String,ReentrantLock> locks=new ConcurrentHashMap<>();private final ConcurrentHashMap<String,LaplaceSignal> rawStates=new ConcurrentHashMap<>();private final LaplaceCoinPoolService coinPool;private final LaplaceRiskyEntryFilter riskyEntryFilter;private final LaplaceMarketBreadthService breadthService;private final LaplaceEntryFilter entryFilter;
 @Autowired
 public LaplacePaperTradeCoordinator(LaplaceStrategyProperties config,LaplacePaperPositionRepository positions,LaplacePaperExecutionService execution,LaplaceTradeJsonlWriter writer,LaplaceCoinPoolService coinPool,LaplaceRiskyEntryFilter riskyEntryFilter,LaplaceMarketBreadthService breadthService,LaplaceEntryFilter entryFilter){this.config=config;this.positions=positions;this.execution=execution;this.writer=writer;this.coinPool=coinPool;this.riskyEntryFilter=riskyEntryFilter;this.breadthService=breadthService;this.entryFilter=entryFilter;}
 public LaplacePaperTradeCoordinator(LaplaceStrategyProperties config,LaplacePaperPositionRepository positions,LaplacePaperExecutionService execution,LaplaceTradeJsonlWriter writer,LaplaceCoinPoolService coinPool,LaplaceRiskyEntryFilter riskyEntryFilter){this(config,positions,execution,writer,coinPool,riskyEntryFilter,null,new LaplaceEntryFilter(riskyEntryFilter));}
 public LaplacePaperTradeCoordinator(LaplaceStrategyProperties config,LaplacePaperPositionRepository positions,LaplacePaperExecutionService execution,LaplaceTradeJsonlWriter writer,LaplaceCoinPoolService coinPool){this(config,positions,execution,writer,coinPool,new LaplaceRiskyEntryFilter());}
 public LaplacePaperTradeCoordinator(LaplaceStrategyProperties config,LaplacePaperPositionRepository positions,LaplacePaperExecutionService execution,LaplaceTradeJsonlWriter writer){this(config,positions,execution,writer,null,new LaplaceRiskyEntryFilter());}
private final AtomicBoolean paperDisabledLogged=new AtomicBoolean(false);
 private final ConcurrentHashMap<String,Instant> stopCooldowns=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,LaplaceSignal> oppositeSignalLocks=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,LaplaceSignal> riskBlockedDirections=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,LaplaceSignal> marketRetryDirections=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,Instant> marketRetryCandles=new ConcurrentHashMap<>();
 private volatile Instant cycleStartedAt=Instant.EPOCH; private final CycleMetrics metrics=new CycleMetrics();
 public void beginCycle(Instant startedAt){beginCycle(startedAt,coinPool==null?Set.of():coinPool.activeSymbols());}
 public void beginCycle(Instant startedAt,Set<String> breadthUniverse){cycleStartedAt=startedAt;metrics.reset();execution.beginPricingCycle();if(breadthService!=null)breadthService.beginCycle(startedAt,breadthUniverse);}
 public CycleSummary cycleSummary(){return metrics.snapshot(execution.pricingMetrics());}
 public Set<String> managementSymbols(){Set<String>x=new HashSet<>();for(var p:positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN))x.add(p.getSymbol());return Set.copyOf(x);}
 public PositionSide mapRawSignalToExecutionSide(LaplaceSignal raw){return switch(raw){case LONG->PositionSide.SHORT;case SHORT->PositionSide.LONG;case NONE->null;};}
 /** Startup history establishes a raw baseline only; it deliberately never executes. */
 public void initializeBaseline(String symbol,LaplaceSignal raw){if(raw!=LaplaceSignal.NONE)rawStates.put(symbol,raw);if(coinPool!=null)coinPool.baseline(symbol, trend(raw));log.info("LAPLACE_STARTUP_RAW_BASELINE_INITIALIZED symbol={} startupRawSignalState={}",symbol,raw);}
 public void onStopLossClosed(String symbol,String entryRawSignal,String positionSide,String positionId,Instant stoppedAt){
  if(symbol==null||entryRawSignal==null)return;
  if(coinPool!=null)coinPool.recordStop(symbol,entryRawSignal,positionSide,positionId,stoppedAt);
  if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.REQUIRE_OPPOSITE_SIGNAL)oppositeSignalLocks.put(symbol,LaplaceSignal.valueOf(entryRawSignal));
  if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.ALLOW){rawStates.remove(symbol);return;}
  try{LaplaceSignal stoppedSignal=LaplaceSignal.valueOf(entryRawSignal);rawStates.putIfAbsent(symbol,stoppedSignal);if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.COOLDOWN)stopCooldowns.put(symbol,Instant.now().plusSeconds(config.getLaplace().getStopReentryCooldownMinutes()*60));log.info("LAPLACE_STOP_REENTRY_LOCKED symbol={} rawSignal={} policy={} entryBlockedReason=WAITING_FOR_NEW_SIGNAL",symbol,rawStates.get(symbol),config.getLaplace().getStopReentryPolicy());}
  catch(IllegalArgumentException invalid){log.warn("LAPLACE_STOP_REENTRY_LOCK_SKIPPED symbol={} invalidRawSignal={}",symbol,entryRawSignal);}
 }
 public void onSignal(LaplaceSignalResult signal,boolean entryUniverseSnapshotContainsSymbol){
  if(signal==null||signal.startupState()!=StartupState.ACTIVE||signal.postStartupClosedBarCount()<1)return;
  if(!config.getLaplace().isPaperExecutionEnabled()){if(signal.entrySignal()!=LaplaceSignal.NONE&&paperDisabledLogged.compareAndSet(false,true))log.warn("LAPLACE_PAPER_EXECUTION_DISABLED strategy={} symbol={}",LaplacePaperExecutionService.STRATEGY,signal.symbol());return;}
  if(signal.entrySignal()==LaplaceSignal.NONE)return;
  ReentrantLock lock=locks.computeIfAbsent(signal.symbol(),s->new ReentrantLock());lock.lock();try {
   // The iteration snapshot is deliberately not authoritative: observeTrend may activate this symbol now.
   boolean poolActive=coinPool==null || coinPool.observeTrend(signal.symbol(),trend(signal.entrySignal()));
   if(!entryUniverseSnapshotContainsSymbol&&poolActive)metrics.activated.incrementAndGet();else if(!poolActive)metrics.waiting.incrementAndGet();
   boolean oppositeAllowed=oppositeSignalAllowed(signal.symbol(),signal.entrySignal());
   if(config.getLaplace().getStopReentryPolicy()==StopReentryPolicy.COOLDOWN&&stopCooldowns.getOrDefault(signal.symbol(),Instant.EPOCH).isBefore(Instant.now())){rawStates.remove(signal.symbol());stopCooldowns.remove(signal.symbol());}
   LaplaceSignal previous=rawStates.put(signal.symbol(),signal.entrySignal());
   boolean fresh=previous==null||previous!=signal.entrySignal();if(fresh)metrics.fresh.incrementAndGet();PositionSide effective=mapRawSignalToExecutionSide(signal.entrySignal());
   boolean oppositeDirectional=fresh&&previous!=null&&isOpposite(previous,signal.entrySignal());
   String persistedRiskRaw=coinPool==null?null:coinPool.blockedRiskyRawSignal(signal.symbol());
   String persistedRetryRaw=coinPool==null?null:coinPool.pendingMarketRawSignal(signal.symbol());
   if(oppositeDirectional||isOppositeName(persistedRiskRaw,signal.entrySignal())||isOppositeName(persistedRetryRaw,signal.entrySignal())){if(hasRiskyDirectionBlock(signal.symbol()))clearRiskyDirectionBlock(signal.symbol());clearMarketRetry(signal.symbol());}
   boolean riskyDirectionBlocked=isRiskyDirectionBlocked(signal.symbol(),signal.entrySignal());
   log.info("LAPLACE_FRESH_SIGNAL rawSignal={} effectiveExecutionSide={} previousRawState={} currentRawState={} isFreshSignal={} currentPosition={} requestedAction={}",signal.entrySignal(),effective,previous,signal.entrySignal(),fresh,current(signal.symbol()),fresh&&effective!=null?"EVALUATE":"NONE");
   if(fresh&&!oppositeAllowed)metrics.blockedByReentry.incrementAndGet();
   if(riskyDirectionBlocked){log.info("LAPLACE_ENTRY_DECISION decision=SKIP entryAllowed=false entryStateConsumed=true reason=RAW_DIRECTION_ALREADY_BLOCKED symbol={} rawSignal={} signalCandleCloseTime={}",signal.symbol(),signal.entrySignal(),signal.signalCandleCloseTime());return;}
   boolean retry=marketRetryMatches(signal.symbol(),signal.entrySignal());
   if(retry&&marketRetryAlreadyRecorded(signal))return;
   boolean actionableReversal=actionableStrongReversal(signal,effective);
   if((fresh||retry||actionableReversal)&&oppositeAllowed)coordinate(signal,poolActive,effective,entryUniverseSnapshotContainsSymbol,Instant.now());
  }finally{lock.unlock();}
 }
 private boolean isOpposite(LaplaceSignal a,LaplaceSignal b){return a==LaplaceSignal.LONG&&b==LaplaceSignal.SHORT||a==LaplaceSignal.SHORT&&b==LaplaceSignal.LONG;}
 private boolean isOppositeName(String a,LaplaceSignal b){if(a==null)return false;try{return isOpposite(LaplaceSignal.valueOf(a),b);}catch(IllegalArgumentException ignored){return false;}}
 private boolean actionableStrongReversal(LaplaceSignalResult signal,PositionSide effective){List<LaplacePaperPositionEntity> open=positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,signal.symbol(),LaplacePositionStatus.OPEN);return open.size()==1&&effective!=null&&open.getFirst().getSide()!=effective&&mapRawSignalToExecutionSide(signal.strongReversalSignal())==effective;}
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
   }LaplaceMarketBreadthSnapshot breadth=breadth(signal);LaplaceEntryFilter.Decision decision=entryFilter.evaluate(signal,effective,breadth);if(!decision.entryAllowed()){reject(signal,effective,breadth,decision);return;}decisionLog(signal,effective,breadth,decision,"OPEN_INVERTED",false);Instant requestStartedAt=Instant.now();try{execution.open(signal,effective,null,"FLAT");clearMarketRetry(signal.symbol());metrics.entryOpened.incrementAndGet();writer.drain();}catch(RuntimeException e){metrics.failures.incrementAndGet();String reason=reason(e,"ENTRY_EXECUTION_FAILED");if("BOOK_TICKER_UNAVAILABLE".equals(reason)){Map<String,Object>a=new LinkedHashMap<>();a.put("requestedSide",effective);a.put("requestedExecutionPriceType",effective==PositionSide.LONG?"ASK":"BID");a.put("requestStartedAt",requestStartedAt);a.put("failedAt",Instant.now());failure(signal,"FLAT","ENTRY",reason,e,false,a);}else failure(signal,"FLAT","ENTRY",reason,e,true);}return;}
  metrics.alreadyOpen.incrementAndGet();
  LaplacePaperPositionEntity p=open.getFirst();if(effective==null||p.getSide()==effective){log.info("LAPLACE_SAME_EFFECTIVE_SIDE_SIGNAL_IGNORED symbol={} side={}",signal.symbol(),p.getSide());return;}
  PositionSide reversalTarget=mapRawSignalToExecutionSide(signal.strongReversalSignal());if(reversalTarget==null||reversalTarget!=effective)return;
  boolean replacementEligible=eligibility==null?poolActiveAfterTransition:eligibility.poolState()==LaplaceCoinPoolState.ACTIVE&&!eligibility.reentryBlocked();
  LaplaceMarketBreadthSnapshot breadth=null;LaplaceEntryFilter.Decision decision=null;boolean openTarget=false;
  if(replacementEligible){breadth=breadth(signal);decision=entryFilter.evaluate(signal,effective,breadth);openTarget=decision.entryAllowed();if(!openTarget)reject(signal,effective,breadth,decision);else decisionLog(signal,effective,breadth,decision,"OPEN_INVERTED",false);}else clearMarketRetry(signal.symbol());
  try{execution.reverse(signal,p,effective,openTarget);if(openTarget)clearMarketRetry(signal.symbol());writer.drain();}catch(RuntimeException e){failure(signal,p.getSide().name(),"REVERSAL",reason(e,"REVERSAL_CLOSE_FAILED"),e,true);}
 }
 private void failure(LaplaceSignalResult s,String current,String action,String reason,Throwable e,boolean retry){writer.failure(s.symbol(),s.signalCandleCloseTime(),current,action,reason,e,retry);writer.drain();}
 private boolean isRiskyDirectionBlocked(String symbol,LaplaceSignal raw){return riskBlockedDirections.get(symbol)==raw||(coinPool!=null&&coinPool.isRiskyDirectionBlocked(symbol,raw.name()));}
 private boolean hasRiskyDirectionBlock(String symbol){return riskBlockedDirections.containsKey(symbol)||(coinPool!=null&&coinPool.hasRiskyDirectionBlock(symbol));}
 private void blockRiskyDirection(LaplaceSignalResult signal){riskBlockedDirections.put(signal.symbol(),signal.entrySignal());if(coinPool!=null)coinPool.blockRiskyDirection(signal.symbol(),signal.entrySignal().name(),signal.signalCandleCloseTime());}
 private void clearRiskyDirectionBlock(String symbol){riskBlockedDirections.remove(symbol);if(coinPool!=null)coinPool.clearRiskyDirectionBlock(symbol);}
 private LaplaceMarketBreadthSnapshot breadth(LaplaceSignalResult signal){return breadthService==null?new LaplaceMarketBreadthSnapshot(cycleStartedAt,signal.signalCandleCloseTime(),100,100,1,1,1,1):breadthService.snapshot(signal.signalCandleCloseTime());}
 private boolean marketRetryMatches(String symbol,LaplaceSignal raw){return marketRetryDirections.get(symbol)==raw||(coinPool!=null&&coinPool.marketRetryMatches(symbol,raw.name()));}
 private boolean marketRetryAlreadyRecorded(LaplaceSignalResult signal){return signal.signalCandleCloseTime().equals(marketRetryCandles.get(signal.symbol()))||(coinPool!=null&&coinPool.marketRetryAlreadyRecorded(signal.symbol(),signal.entrySignal().name(),signal.signalCandleCloseTime()));}
 private void saveMarketRetry(LaplaceSignalResult signal,String reason){marketRetryDirections.put(signal.symbol(),signal.entrySignal());marketRetryCandles.put(signal.symbol(),signal.signalCandleCloseTime());if(coinPool!=null)coinPool.saveMarketRetry(signal.symbol(),signal.entrySignal().name(),reason,signal.signalCandleCloseTime());}
 private void clearMarketRetry(String symbol){marketRetryDirections.remove(symbol);marketRetryCandles.remove(symbol);if(coinPool!=null)coinPool.clearMarketRetry(symbol);}
 private void reject(LaplaceSignalResult signal,PositionSide effective,LaplaceMarketBreadthSnapshot breadth,LaplaceEntryFilter.Decision decision){boolean consumed=decision.rejectionPolicy()==LaplaceEntryRejectionPolicy.PERSIST_UNTIL_OPPOSITE_RAW_SIGNAL;if(consumed){blockRiskyDirection(signal);clearMarketRetry(signal.symbol());}else saveMarketRetry(signal,decision.rejectionReason());writer.entrySkipped(signal.symbol(),signal.signalCandleCloseTime(),effective,java.math.BigDecimal.valueOf(signal.signalCandleClose()),signal.entrySignal().name(),decision.rejectionReason());decisionLog(signal,effective,breadth,decision,"SKIP",consumed);}
 private void decisionLog(LaplaceSignalResult s,PositionSide side,LaplaceMarketBreadthSnapshot b,LaplaceEntryFilter.Decision d,String decision,boolean consumed){log.info("LAPLACE_ENTRY_DECISION symbol={} rawSignal={} effectiveExecutionSide={} signalCandleCloseTime={} atrPercentage={} previousRawTakerImbalance={} marketBreadth2h={} marketBreadth4h={} marketBreadthAcceleration={} positiveCoinCount2h={} validCoinCount2h={} positiveCoinCount4h={} validCoinCount4h={} riskyEntry={} marketRising={} shortBullRisk={} entryAllowed={} decision={} rejectionReason={} blockedRawDirection={} entryStateConsumed={}",s.symbol(),s.entrySignal(),side,s.signalCandleCloseTime(),s.atrPercentage(),s.previousRawTakerImbalance(),b.marketBreadth2h(),b.marketBreadth4h(),d.marketBreadthAcceleration(),b.positiveCoinCount2h(),b.validCoinCount2h(),b.positiveCoinCount4h(),b.validCoinCount4h(),d.riskyEntry(),d.marketRising(),d.shortBullRisk(),d.entryAllowed(),decision,d.rejectionReason(),riskBlockedDirections.get(s.symbol()),consumed);}
 private void failure(LaplaceSignalResult s,String current,String action,String reason,Throwable e,boolean retry,Map<String,Object> audit){writer.failure(s.symbol(),s.signalCandleCloseTime(),current,action,reason,e,retry,audit);writer.drain();}
 private Map<String,Object> eligibilityAudit(LaplaceCoinPoolService.EntryEligibility e,boolean snapshot,String position,Instant evaluatedAt){Map<String,Object>x=new LinkedHashMap<>();x.put("poolStateAtEvaluation",e==null?null:e.poolState());x.put("includedInPoolAtEvaluation",e!=null&&e.includedInPool());x.put("newEntryAllowed",e!=null&&e.allowed());x.put("entryUniverseSnapshotContainsSymbol",snapshot);x.put("reentryBlocked",e!=null&&e.reentryBlocked());x.put("positionState",position);x.put("eligibilityEvaluatedAt",evaluatedAt);x.put("cycleStartedAt",cycleStartedAt);x.put("poolStateUpdatedAt",e==null?null:e.poolStateUpdatedAt());return x;}
 public record CycleSummary(int waitingSymbols,int activatedSymbols,int freshSignalCount,int entryAttemptCount,int entryOpenedCount,int outsideEntryUniverseCount,int blockedByReentryCount,int alreadyOpenCount,int failureCount,int bookTickerRequestCount,int bookTickerFailureCount,int bulkBookTickerRequestCount){}
 private static final class CycleMetrics{final java.util.concurrent.atomic.AtomicInteger waiting=new java.util.concurrent.atomic.AtomicInteger(),activated=new java.util.concurrent.atomic.AtomicInteger(),fresh=new java.util.concurrent.atomic.AtomicInteger(),entryAttempts=new java.util.concurrent.atomic.AtomicInteger(),entryOpened=new java.util.concurrent.atomic.AtomicInteger(),outsideUniverse=new java.util.concurrent.atomic.AtomicInteger(),blockedByReentry=new java.util.concurrent.atomic.AtomicInteger(),alreadyOpen=new java.util.concurrent.atomic.AtomicInteger(),failures=new java.util.concurrent.atomic.AtomicInteger();void reset(){waiting.set(0);activated.set(0);fresh.set(0);entryAttempts.set(0);entryOpened.set(0);outsideUniverse.set(0);blockedByReentry.set(0);alreadyOpen.set(0);failures.set(0);}CycleSummary snapshot(LaplaceExecutionPriceProvider.RequestMetrics p){if(p==null)p=new LaplaceExecutionPriceProvider.RequestMetrics(0,0,0);return new CycleSummary(waiting.get(),activated.get(),fresh.get(),entryAttempts.get(),entryOpened.get(),outsideUniverse.get(),blockedByReentry.get(),alreadyOpen.get(),failures.get(),p.bookTickerRequestCount(),p.bookTickerFailureCount(),p.bulkBookTickerRequestCount());}}
 private String reason(RuntimeException e,String fallback){return e.getMessage()!=null&&Set.of("BOOK_TICKER_UNAVAILABLE","EXECUTION_PRICE_UNAVAILABLE","INVALID_QUANTITY","INVALID_NOTIONAL","POSITION_STATE_CONFLICT","DUPLICATE_EXECUTION_BLOCKED","REVERSAL_OPEN_FAILED").contains(e.getMessage())?e.getMessage():fallback;}
}
