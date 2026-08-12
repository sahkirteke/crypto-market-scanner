package com.crypto.laplace.session;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.scheduler.LaplaceThirtyMinuteScheduler;
import com.crypto.laplace.service.LaplaceStartupHistoryService;
import com.crypto.laplace.service.StartupMarketUniverseService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Session-only risk controls around the unchanged KURAL5 signal and position lifecycle. */
@Slf4j
@Service
@Order(300)
@ConditionalOnProperty(prefix="trading.laplace", name="enabled", havingValue="true")
public class LaplaceSessionManager implements ApplicationRunner {
    public static final String PROFIT_LOCK_REASON="SESSION_PROFIT_LOCK_5PCT_REALIZED_4_6PCT_PROJECTED";
    public static final String CUTOFF_REASON="BLOCK_SESSION_ENTRY_CUTOFF_10H";
    public static final String HARD_CLOSE_REASON="EXIT_SESSION_HARD_CLOSE_12H";
    public static final String LONG_CAP_REASON="BLOCK_LONG_30M_SLOT_CAP_5";
    public static final String VOLUME_REASON="BLOCK_CURRENT_24H_VOLUME_BELOW_20M";
    private final LaplaceStrategyProperties properties;
    private final LaplacePaperExecutionService execution;
    private final LaplacePaperPositionRepository positions;
    private final StartupMarketUniverseService universe;
    private final LaplaceStartupHistoryService history;
    private final ObjectProvider<LaplacePaperTradeCoordinator> coordinator;
    private final ObjectProvider<LaplaceThirtyMinuteScheduler> scheduler;
    private final ConcurrentHashMap<Instant, AtomicInteger> longSlots=new ConcurrentHashMap<>();
    private volatile Snapshot state;

    public LaplaceSessionManager(LaplaceStrategyProperties properties, LaplacePaperExecutionService execution,
            LaplacePaperPositionRepository positions, StartupMarketUniverseService universe,
            LaplaceStartupHistoryService history, ObjectProvider<LaplacePaperTradeCoordinator> coordinator,
            ObjectProvider<LaplaceThirtyMinuteScheduler> scheduler) {
        this.properties=properties;this.execution=execution;this.positions=positions;this.universe=universe;
        this.history=history;this.coordinator=coordinator;this.scheduler=scheduler;
    }

    @Override public synchronized void run(ApplicationArguments ignored){start(Instant.now(), null);}

    @Scheduled(fixedDelayString="${trading.laplace.session-check-delay-ms:60000}")
    public synchronized void tick(){
        if(state==null)return;
        Instant now=Instant.now();Metrics metrics=metrics();state=state.withMetrics(metrics);
        if(!now.isBefore(state.endsAt())){finishAndReset(now,HARD_CLOSE_REASON);return;}
        if(state.locked()){logContext("SESSION_LOCKED_WAITING_PLANNED_END",null,null,null);return;}
        if(state.riskOff()){closeAndLock(PROFIT_LOCK_REASON);return;}
        if(metrics.realizedReturnPct().compareTo(properties.getLaplace().getSessionProfitLockRealizedPct())>=0
                &&metrics.projectedReturnPct().compareTo(properties.getLaplace().getSessionProfitLockProjectedPct())>=0){
            closeAndLock(PROFIT_LOCK_REASON);return;
        }
        if(!now.isBefore(state.entryCutoffAt())&&state.entryEnabled()){
            state=state.withEntry(false);logContext(CUTOFF_REASON,null,null,null);
        }
    }

    public synchronized boolean allowEntry(String symbol, PositionSide side, Instant signalSlot){
        tick(); Snapshot s=state;if(s==null||!s.entryEnabled()||s.locked()){logContext(CUTOFF_REASON,symbol,signalSlot,null);return false;}
        BigDecimal volume=universe.currentVolume(symbol);
        if(volume==null||volume.compareTo(universe.minimumVolumeThreshold())<0){logContext(VOLUME_REASON,symbol,signalSlot,volume);return false;}
        if(side==PositionSide.LONG){int selected=longSlots.computeIfAbsent(signalSlot,k->new AtomicInteger()).incrementAndGet();
            if(selected>properties.getLaplace().getMaxLongPerThirtyMinuteSlot()){logContext(LONG_CAP_REASON,symbol,signalSlot,volume);return false;}}
        logContext("ENTRY_SESSION_GUARDS_PASSED",symbol,signalSlot,volume);return true;
    }

    private void closeAndLock(String reason){
        state=state.withEntry(false);
        try{execution.closeAllOpen(reason);if(!flat())throw new IllegalStateException("exchange/paper ledger not flat");
            state=state.locked(metrics());logContext(reason,null,null,null);
        }catch(RuntimeException e){state=state.riskOff();log.error("SESSION_CLOSE_FAILED sessionId={} reason={} entryEnabled=false reset=false error={}",state.id(),reason,e.getMessage(),e);}
    }

    private void finishAndReset(Instant now,String reason){
        state=state.withEntry(false);
        try{
            // Paper execution has no resting entry orders; entry is disabled before market closes are requested.
            execution.closeAllOpen(reason);
            if(!flat())throw new IllegalStateException("exchange/paper ledger not flat");
            Metrics finalMetrics=metrics();BigDecimal previous=finalMetrics.realizedReturnPct();
            coordinator.ifAvailable(LaplacePaperTradeCoordinator::clearSessionRuntime);
            scheduler.ifAvailable(LaplaceThirtyMinuteScheduler::clearSessionRuntime);
            longSlots.clear();
            universe.reload();
            if(!universe.isReady())throw new IllegalStateException("new universe unavailable");
            history.hardReload();
            if(history.readySymbols().isEmpty())throw new IllegalStateException("fresh historical data unavailable");
            start(now,previous);
            logContext("SESSION_HARD_RESET_COMPLETED",null,null,null);
        }catch(RuntimeException e){state=state.riskOff();log.error("SESSION_RESET_FAILED sessionId={} entryEnabled=false reset=false flat={} error={}",state.id(),flat(),e.getMessage(),e);}
    }

    private void start(Instant now,BigDecimal previous){
        BigDecimal capital=execution.accountEquity();
        BigDecimal margin=properties.getLaplace().getSessionBaseMarginUsdt().multiply(capital)
                .divide(properties.getLaplace().getSessionBaseCapitalUsdt(),12,RoundingMode.HALF_UP);
        execution.setSessionMargin(margin);
        var c=properties.getLaplace();state=new Snapshot(UUID.randomUUID().toString(),now,
                now.plus(Duration.ofHours(c.getSessionHours())),now.plus(Duration.ofHours(c.getEntryCutoffHours())),
                true,false,false,capital,margin,previous==null?BigDecimal.ZERO:previous,Metrics.zero());
        logContext("SESSION_STARTED",null,null,null);
    }

    private boolean flat(){return positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN).isEmpty();}
    private Metrics metrics(){Snapshot s=state;if(s==null)return Metrics.zero();BigDecimal realized=positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.CLOSED).stream()
            .filter(p->p.getExitTime()!=null&&!p.getExitTime().isBefore(s.startedAt())).map(p->p.getNetPnl()==null?BigDecimal.ZERO:p.getNetPnl()).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal open=execution.projectedOpenNetPnl(),projected=realized.add(open);return new Metrics(realized,pct(realized,s.capital()),open,projected,pct(projected,s.capital()));}
    private BigDecimal pct(BigDecimal value,BigDecimal capital){return value.multiply(BigDecimal.valueOf(100)).divide(capital,8,RoundingMode.HALF_UP);}
    private void logContext(String reason,String symbol,Instant slot,BigDecimal volume){Snapshot s=state;if(s==null)return;Metrics m=s.metrics();int selected=slot==null?0:longSlots.getOrDefault(slot,new AtomicInteger()).get();
        log.info("SESSION_DECISION reason={} symbol={} sessionId={} sessionStartedAt={} sessionAgeMinutes={} entryEnabled={} sessionStartCapital={} sessionMargin={} realizedSessionNetPnl={} realizedSessionReturnPct={} projectedOpenPositionsNetPnl={} projectedSessionNetPnl={} projectedSessionReturnPct={} profitLockTriggered={} current24hQuoteVolume={} signal30mSlot={} longCandidateCountInSlot={} longSelectedInSlot={} previousSessionReturnPct={}",reason,symbol,s.id(),s.startedAt(),Duration.between(s.startedAt(),Instant.now()).toMinutes(),s.entryEnabled(),s.capital(),s.margin(),m.realized(),m.realizedReturnPct(),m.open(),m.projected(),m.projectedReturnPct(),s.locked(),volume,slot,selected,Math.min(selected,properties.getLaplace().getMaxLongPerThirtyMinuteSlot()),s.previousReturnPct());}

    public Snapshot snapshot(){return state;}
    public record Metrics(BigDecimal realized,BigDecimal realizedReturnPct,BigDecimal open,BigDecimal projected,BigDecimal projectedReturnPct){static Metrics zero(){return new Metrics(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);}}
    public record Snapshot(String id,Instant startedAt,Instant endsAt,Instant entryCutoffAt,boolean entryEnabled,boolean locked,boolean riskOff,BigDecimal capital,BigDecimal margin,BigDecimal previousReturnPct,Metrics metrics){
        Snapshot withEntry(boolean value){return new Snapshot(id,startedAt,endsAt,entryCutoffAt,value,locked,riskOff,capital,margin,previousReturnPct,metrics);}
        Snapshot withMetrics(Metrics value){return new Snapshot(id,startedAt,endsAt,entryCutoffAt,entryEnabled,locked,riskOff,capital,margin,previousReturnPct,value);}
        Snapshot locked(Metrics value){return new Snapshot(id,startedAt,endsAt,entryCutoffAt,false,true,false,capital,margin,previousReturnPct,value);}
        Snapshot riskOff(){return new Snapshot(id,startedAt,endsAt,entryCutoffAt,false,locked,true,capital,margin,previousReturnPct,metrics);}
    }
}
