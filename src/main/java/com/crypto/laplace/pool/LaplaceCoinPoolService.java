package com.crypto.laplace.pool;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.laplace.audit.*;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor
public class LaplaceCoinPoolService {
    static final BigDecimal INITIAL_CAPITAL=new BigDecimal("250"), INITIAL_MARGIN=new BigDecimal("5");
    private final BinanceFuturesClient client;
    private final LaplaceCoinPoolRepository pool;
    private final LaplacePaperPositionRepository positions;
    private final LaplaceCapitalSnapshotRepository snapshots;
    private final VolumeScanAuditService audit;
    private final com.crypto.laplace.config.LaplaceStrategyProperties properties;

    public boolean canOpen(String symbol){return pool.findById(symbol).map(p->p.getState()==LaplaceCoinPoolState.ACTIVE).orElse(false);}
    public EntryEligibility isCurrentlyEligibleForEntry(String symbol) {
        LaplaceCoinPoolEntity member=pool.findById(symbol).orElse(null);
        List<com.crypto.laplace.persistence.LaplacePaperPositionEntity> open=positions.findByStrategyAndSymbolAndStatus(
                LaplacePaperExecutionService.STRATEGY,symbol,LaplacePositionStatus.OPEN);
        LaplaceCoinPoolState state=member==null?null:member.getState();
        boolean included=state!=null&&state!=LaplaceCoinPoolState.REMOVED;
        boolean reentryBlocked=member!=null&&Boolean.TRUE.equals(member.getReentryBlocked());
        String reason=member==null?"COIN_POOL_RECORD_MISSING":state!=LaplaceCoinPoolState.ACTIVE?"POOL_STATE_"+state:
                reentryBlocked?"REENTRY_LOCK_ACTIVE":open.size()>1?"POSITION_STATE_CONFLICT":!open.isEmpty()?"POSITION_ALREADY_OPEN":null;
        return new EntryEligibility(reason==null,state,included,reentryBlocked,open.size(),reason,member==null?null:member.getUpdatedAt());
    }
    public record EntryEligibility(boolean allowed,LaplaceCoinPoolState poolState,boolean includedInPool,
                                   boolean reentryBlocked,int openPositionCount,String blockReason,Instant poolStateUpdatedAt) {}
    private BigDecimal entryThreshold(){return properties.getLaplace().getVolumeScan().getEntryMinQuoteVolume();}
    private BigDecimal retentionThreshold(){return properties.getLaplace().getVolumeScan().getRetentionMinQuoteVolume();}
    public Set<String> symbolsToProcess(){return new HashSet<>(pool.findByStateIn(List.of(LaplaceCoinPoolState.ACTIVE,LaplaceCoinPoolState.WAITING_FOR_NEW_TREND,LaplaceCoinPoolState.PENDING_REMOVAL)).stream().map(LaplaceCoinPoolEntity::getSymbol).toList());}
    public BigDecimal dailyMargin(){return snapshots.findById(LocalDate.now(ZoneId.of("America/New_York"))).map(LaplaceCapitalSnapshotEntity::getDailyTradeMargin).orElse(INITIAL_MARGIN);}

    @Transactional
    public void scan() {
        VolumeScanAuditService.Run run=audit.newRun();
        List<VolumeScanCoinEvaluation> evaluations=new ArrayList<>();
        boolean started=false;
        try {
            List<SymbolInfo> info=client.getExchangeInfo();
            if(info==null) throw new IllegalStateException("POOL_EXCHANGE_INFO_UNAVAILABLE");
            List<String> candidates=info.stream().filter(Objects::nonNull)
                    .filter(s->"USDT".equals(s.getQuoteAsset())&&"PERPETUAL".equals(s.getContractType())&&"TRADING".equals(s.getStatus()))
                    .map(SymbolInfo::getSymbol).distinct().sorted().toList();
            audit.started(run,candidates.size()); started=true;
            List<Ticker24h> ticker=client.getAll24hTickers();
            if(ticker==null) throw new IllegalStateException("POOL_TICKER_DATA_UNAVAILABLE");
            Map<String,Ticker24h> tickers=ticker.stream().filter(Objects::nonNull).filter(t->candidates.contains(t.getSymbol()))
                    .collect(Collectors.toMap(Ticker24h::getSymbol,Function.identity(),(a,b)->b));
            Map<String,LaplaceCoinPoolEntity> existing=pool.findAll().stream().collect(Collectors.toMap(LaplaceCoinPoolEntity::getSymbol,Function.identity()));
            Map<String,Integer> ranks=volumeRanks(tickers.values());
            Instant now=Instant.now();

            for(String symbol:candidates) {
                LaplaceCoinPoolEntity entity=existing.get(symbol);
                VolumeScanAuditStatus previous=entity==null?null:status(entity);
                BigDecimal previousVolume=entity==null?null:entity.getLastQuoteVolume();
                Ticker24h market=tickers.get(symbol);
                boolean open=hasOpen(symbol);
                if(market==null||market.getQuoteVolume()==null) {
                    log.warn("LAPLACE_POOL_VOLUME_MISSING symbol={} state={}",symbol,entity==null?null:entity.getState());
                    evaluations.add(evaluation(symbol,previous,VolumeScanAuditStatus.NOT_EVALUATED,"MARKET_DATA_MISSING",
                            List.of("MARKET_DATA_MISSING"),List.of("QUOTE_VOLUME_AVAILABLE"),List.of(),market,previousVolume,null,open,entity,false,"MARKET_DATA_MISSING","quoteVolume is unavailable"));
                    continue;
                }
                BigDecimal volume=market.getQuoteVolume();
                if(entity!=null) {
                    entity.setLastQuoteVolume(volume); entity.setLastVolumeScanAt(now);
                    if(entity.getState()!=LaplaceCoinPoolState.PENDING_REMOVAL && entity.getState()!=LaplaceCoinPoolState.REMOVED && volume.compareTo(retentionThreshold())<0)
                        transition(entity,open?LaplaceCoinPoolState.PENDING_REMOVAL:LaplaceCoinPoolState.REMOVED);
                    else pool.save(entity);
                }
                if((entity==null||entity.getState()==LaplaceCoinPoolState.REMOVED)&&volume.compareTo(entryThreshold())>0) {
                    if(entity==null) entity=LaplaceCoinPoolEntity.builder().symbol(symbol).build();
                    entity.setLastQuoteVolume(volume);entity.setLastVolumeScanAt(now);entity.setAddedAt(now);entity.setRemovedAt(null);entity.setPendingRemovalAt(null);entity.setWaitingForNewTrend(true);
                    transition(entity,LaplaceCoinPoolState.WAITING_FOR_NEW_TREND);
                }
                VolumeScanAuditStatus next=status(entity);
                String reason=reason(entity,volume,open);
                List<String> reasons=new ArrayList<>(); reasons.add(reason);
                if(entity!=null&&entity.getState()==LaplaceCoinPoolState.PENDING_REMOVAL&&open) reasons.add("EXISTING_POSITION_PROTECTED");
                List<String> failed=volume.compareTo(entity==null||entity.getState()==LaplaceCoinPoolState.REMOVED?entryThreshold():retentionThreshold())<0?List.of("MIN_VOLUME"):List.of();
                List<String> passed=failed.isEmpty()?List.of("MIN_VOLUME"):List.of();
                evaluations.add(evaluation(symbol,previous,next,reason,reasons,failed,passed,market,previousVolume,ranks.get(symbol),open,entity,true,null,null));
            }
            evaluations.forEach(e->audit.coin(run,e));
            saveCapitalSnapshot(now);
            audit.completed(run,evaluations);
            log.info("LAPLACE_POOL_SCAN completed symbols={} evaluated={} realizedCapital={}",candidates.size(),evaluations.size(),snapshots.findById(run.date()).map(LaplaceCapitalSnapshotEntity::getRealizedCapital).orElse(null));
        } catch(RuntimeException e) {
            if(!started) audit.started(run,0);
            audit.failed(run,e,evaluations.size());
            throw e;
        }
    }

    private void saveCapitalSnapshot(Instant now) {
        BigDecimal realized=INITIAL_CAPITAL.add(positions.findAll().stream().filter(x->LaplacePaperExecutionService.STRATEGY.equals(x.getStrategy())&&x.getStatus()!=LaplacePositionStatus.OPEN).map(x->x.getNetPnl()==null?BigDecimal.ZERO:x.getNetPnl()).reduce(BigDecimal.ZERO,BigDecimal::add));
        BigDecimal margin=INITIAL_MARGIN.multiply(realized.divide(INITIAL_CAPITAL,12,RoundingMode.HALF_UP));
        snapshots.save(LaplaceCapitalSnapshotEntity.builder().snapshotDate(LocalDate.now(ZoneId.of("America/New_York"))).realizedCapital(realized).dailyTradeMargin(margin).leverage(10).positionNotional(margin.multiply(BigDecimal.TEN)).calculatedAt(now).build());
    }
    private VolumeScanCoinEvaluation evaluation(String symbol,VolumeScanAuditStatus previous,VolumeScanAuditStatus next,String primary,List<String> reasons,List<String> failed,List<String> passed,Ticker24h t,BigDecimal previousVolume,Integer rank,boolean open,LaplaceCoinPoolEntity entity,boolean complete,String errorCode,String errorMessage) {
        BigDecimal volume=t==null?null:t.getQuoteVolume(); BigDecimal change=null;
        if(volume!=null&&previousVolume!=null&&previousVolume.signum()!=0) change=volume.subtract(previousVolume).multiply(BigDecimal.valueOf(100)).divide(previousVolume,6,RoundingMode.HALF_UP);
        return new VolumeScanCoinEvaluation(symbol,previous,next,VolumeScanAuditService.transition(previous,next),next!=VolumeScanAuditStatus.ELIMINATED&&next!=VolumeScanAuditStatus.NOT_EVALUATED,next==VolumeScanAuditStatus.ACTIVE||next==VolumeScanAuditStatus.WATCHLIST,primary,List.copyOf(reasons),failed,passed,OffsetDateTime.now(VolumeScanAuditService.ZONE),volume,previousVolume,change,rank,t==null?null:t.getLastPrice(),t==null?null:t.getPriceChangePercent(),open,entity!=null&&Boolean.TRUE.equals(entity.getReentryBlocked()),complete,errorCode,errorMessage);
    }
    private static Map<String,Integer> volumeRanks(Collection<Ticker24h> tickers){List<Ticker24h> sorted=tickers.stream().filter(t->t.getQuoteVolume()!=null).sorted(Comparator.comparing(Ticker24h::getQuoteVolume).reversed()).toList();Map<String,Integer> result=new HashMap<>();for(int i=0;i<sorted.size();i++)result.put(sorted.get(i).getSymbol(),i+1);return result;}
    private static VolumeScanAuditStatus status(LaplaceCoinPoolEntity p){if(p==null||p.getState()==LaplaceCoinPoolState.REMOVED)return VolumeScanAuditStatus.ELIMINATED;if(p.getState()==LaplaceCoinPoolState.ACTIVE)return VolumeScanAuditStatus.ACTIVE;return VolumeScanAuditStatus.WATCHLIST;}
    private String reason(LaplaceCoinPoolEntity p,BigDecimal volume,boolean open){if(p==null||p.getState()==LaplaceCoinPoolState.REMOVED)return "MIN_VOLUME_NOT_MET";if(p.getState()==LaplaceCoinPoolState.PENDING_REMOVAL)return open?"EXISTING_POSITION_PROTECTED":"MIN_VOLUME_NOT_MET";if(p.getState()==LaplaceCoinPoolState.WAITING_FOR_NEW_TREND)return "WAITING_FOR_NEW_TREND";return volume.compareTo(retentionThreshold())>=0?"VOLUME_CRITERIA_PASSED":"MIN_VOLUME_NOT_MET";}

    @Transactional public boolean observeTrend(String symbol,LaplaceTrend trend){var p=pool.findById(symbol).orElse(null);if(p==null||p.getState()!=LaplaceCoinPoolState.WAITING_FOR_NEW_TREND)return p!=null&&p.getState()==LaplaceCoinPoolState.ACTIVE;LaplaceTrend previous=p.getLastObservedTrend();p.setLastObservedTrend(trend);if(previous!=null&&previous!=trend&&trend!=LaplaceTrend.NEUTRAL){transition(p,LaplaceCoinPoolState.ACTIVE);p.setWaitingForNewTrend(false);log.info("LAPLACE_TREND_LOCK_RELEASED symbol={} previousTrend={} currentTrend={} poolState=ACTIVE",symbol,previous,trend);return true;}log.info("LAPLACE_TREND_LOCK_WAITING symbol={} previousTrend={} currentTrend={} entryBlockedReason=WAITING_FOR_NEW_TREND",symbol,previous,trend);return false;}
    @Transactional public void baseline(String symbol,LaplaceTrend trend){pool.findById(symbol).filter(p->p.getState()==LaplaceCoinPoolState.WAITING_FOR_NEW_TREND&&p.getLastObservedTrend()==null).ifPresent(p->{p.setLastObservedTrend(trend);pool.save(p);});}
    @Transactional public void positionClosed(String symbol){pool.findById(symbol).filter(p->p.getState()==LaplaceCoinPoolState.PENDING_REMOVAL).ifPresent(p->transition(p,LaplaceCoinPoolState.REMOVED));}
    @Transactional public void recordStop(String symbol,String rawSignal,String positionSide,String positionId,Instant stoppedAt){pool.findById(symbol).ifPresent(p->{p.setStoppedRawSignalSide(rawSignal);p.setStoppedPositionSide(positionSide);p.setStoppedPositionId(positionId);p.setStoppedAt(stoppedAt);p.setReentryBlocked(true);p.setUnblockedBySignalSide(null);p.setUnblockedAt(null);pool.save(p);log.info("LAPLACE_REENTRY_LOCK_PERSISTED symbol={} rawSignal={} positionSide={} positionId={}",symbol,rawSignal,positionSide,positionId);});}
    @Transactional public boolean allowAfterOppositeSignal(String symbol,String rawSignal){var p=pool.findById(symbol).orElse(null);if(p==null||!Boolean.TRUE.equals(p.getReentryBlocked()))return true;if(rawSignal==null||"NONE".equals(rawSignal)||rawSignal.equals(p.getStoppedRawSignalSide()))return false;boolean opposite=("LONG".equals(rawSignal)&&"SHORT".equals(p.getStoppedRawSignalSide()))||("SHORT".equals(rawSignal)&&"LONG".equals(p.getStoppedRawSignalSide()));if(!opposite)return false;p.setReentryBlocked(false);p.setUnblockedBySignalSide(rawSignal);p.setUnblockedAt(Instant.now());pool.save(p);log.info("LAPLACE_REENTRY_LOCK_RELEASED symbol={} stoppedRawSignal={} oppositeSignal={}",symbol,p.getStoppedRawSignalSide(),rawSignal);return true;}
    private boolean hasOpen(String symbol){return !positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,symbol,LaplacePositionStatus.OPEN).isEmpty();}
    private void transition(LaplaceCoinPoolEntity p,LaplaceCoinPoolState next){LaplaceCoinPoolState old=p.getState();p.setState(next);Instant n=Instant.now();if(next==LaplaceCoinPoolState.REMOVED)p.setRemovedAt(n);if(next==LaplaceCoinPoolState.PENDING_REMOVAL)p.setPendingRemovalAt(n);pool.save(p);log.info("LAPLACE_POOL_STATE_CHANGED symbol={} previousState={} state={}",p.getSymbol(),old,next);}
}
