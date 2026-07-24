package com.crypto.laplace.pool;

import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistent daily quote-volume universe.  It deliberately does not alter signal calculation. */
@Slf4j @Service @RequiredArgsConstructor
public class LaplaceCoinPoolService {
 public static final BigDecimal ENTRY_VOLUME = new BigDecimal("25000000");
 public static final BigDecimal EXIT_VOLUME = new BigDecimal("20000000");
 public static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
 private final LaplaceCoinPoolRepository pool; private final LaplacePaperPositionRepository positions;
 /** Atomic update: callers must supply the already-filtered USDT perpetual/TRADING universe and quoteVolume map. */
 @Transactional public void refresh(Collection<String> eligibleSymbols, Map<String,BigDecimal> quoteVolumes, Instant now) {
  Map<String,LaplaceCoinPoolEntity> current=pool.findAll().stream().collect(Collectors.toMap(LaplaceCoinPoolEntity::getSymbol,x->x));
  for(String symbol:eligibleSymbols) {
   BigDecimal volume=quoteVolumes.get(symbol); if(volume==null){log.warn("LAPLACE_POOL_VOLUME_MISSING symbol={}",symbol);continue;}
   LaplaceCoinPoolEntity state=current.get(symbol);
   if(state==null || state.getStatus()==PoolStatus.REMOVED) { if(volume.compareTo(ENTRY_VOLUME)>0) add(symbol,volume,now,state); continue; }
   state.setQuoteVolume(volume);
   if(state.getStatus()==PoolStatus.PENDING_REMOVAL) { pool.save(state); continue; }
   if(volume.compareTo(EXIT_VOLUME)<0) removeOrPend(state,now); else pool.save(state);
  }
  // A missing ticker is intentionally not a removal; this preserves a known-good snapshot on partial API data.
  log.info("LAPLACE_POOL_REFRESH_COMPLETED time={} zone={} symbols={}",now,NEW_YORK,eligibleSymbols.size());
 }
 private void add(String symbol,BigDecimal volume,Instant now,LaplaceCoinPoolEntity old) { LaplaceCoinPoolEntity e=old==null?new LaplaceCoinPoolEntity():old; e.setSymbol(symbol);e.setStatus(PoolStatus.WAITING_FOR_NEW_TREND);e.setQuoteVolume(volume);e.setAddedAt(now);e.setRemovedAt(null);e.setLastObservedTrend(null);e.setTrendLockCompleted(false);pool.save(e);log.info("LAPLACE_POOL_STATE symbol={} transition=REMOVED_TO_WAITING_FOR_NEW_TREND volume={}",symbol,volume); }
 private void removeOrPend(LaplaceCoinPoolEntity e,Instant now) { if(open(e.getSymbol())) {e.setStatus(PoolStatus.PENDING_REMOVAL);pool.save(e);log.info("LAPLACE_POOL_STATE symbol={} transition=ACTIVE_TO_PENDING_REMOVAL",e.getSymbol());} else {e.setStatus(PoolStatus.REMOVED);e.setRemovedAt(now);pool.save(e);log.info("LAPLACE_POOL_STATE symbol={} transition=ACTIVE_TO_REMOVED",e.getSymbol());} }
 @Transactional public boolean observeClosedTrend(String symbol,LaplaceSignal trend) { LaplaceCoinPoolEntity e=pool.findById(symbol).orElse(null); if(e==null)return false; if(e.getStatus()==PoolStatus.PENDING_REMOVAL && !open(symbol)){e.setStatus(PoolStatus.REMOVED);e.setRemovedAt(Instant.now());pool.save(e);return false;} if(e.getStatus()==PoolStatus.REMOVED)return false;
  String next=trend.name(); if(e.getStatus()==PoolStatus.WAITING_FOR_NEW_TREND) {String previous=e.getLastObservedTrend(); if(previous==null){e.setLastObservedTrend(next);pool.save(e);return false;} if(!previous.equals(next)){e.setLastObservedTrend(next);if(trend!=LaplaceSignal.NONE){e.setStatus(PoolStatus.ACTIVE);e.setTrendLockCompleted(true);log.info("LAPLACE_POOL_STATE symbol={} transition=WAITING_FOR_NEW_TREND_TO_ACTIVE previousTrend={} newTrend={}",symbol,previous,next);pool.save(e);return true;}} pool.save(e);return false;} return e.getStatus()==PoolStatus.ACTIVE; }
 @Transactional public void reconcileClosedPosition(String symbol) { pool.findById(symbol).filter(e->e.getStatus()==PoolStatus.PENDING_REMOVAL&&!open(symbol)).ifPresent(e->{e.setStatus(PoolStatus.REMOVED);e.setRemovedAt(Instant.now());pool.save(e);}); }
 public boolean canOpen(String symbol){return pool.findById(symbol).map(e->e.getStatus()==PoolStatus.ACTIVE&&!open(symbol)).orElse(false);} public Set<String> managedSymbols(){return pool.findByStatusIn(List.of(PoolStatus.ACTIVE,PoolStatus.WAITING_FOR_NEW_TREND,PoolStatus.PENDING_REMOVAL)).stream().map(LaplaceCoinPoolEntity::getSymbol).collect(Collectors.toUnmodifiableSet());}
 private boolean open(String s){return !positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,s,LaplacePositionStatus.OPEN).isEmpty();}
}
