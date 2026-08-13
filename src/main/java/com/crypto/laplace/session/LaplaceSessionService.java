package com.crypto.laplace.session;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.*;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor @Order(50)
public class LaplaceSessionService implements ApplicationRunner {
    private static final int SCALE=12;
    private final LaplaceTradingSessionRepository repository;
    private final LaplacePaperPositionRepository positions;
    private final LaplacePaperExecutionService execution;
    private final LaplaceStrategyProperties properties;
    private final LaplaceRuntimeInitializer initializer;
    private volatile String runtimeSessionId;

    @Override @Transactional public void run(ApplicationArguments args) {Instant now=Instant.now();repository.findFirstByOrderByStartTimeDesc().filter(s->s.getStatus()!=LaplaceSessionStatus.COMPLETED&&!s.getEndTime().isAfter(now)).ifPresent(s->completeSession(s.getSessionId(),now));ensureActiveSessionBeforeScan(now);}

    public boolean entriesAllowed(Instant now) {
        LaplaceTradingSessionEntity s=ensureCurrent(now);
        return s.getStatus()==LaplaceSessionStatus.ACTIVE && !s.isEntryLocked() && now.isBefore(s.getEntryCutoffTime());
    }
    public boolean acceptsSignal(Instant now,Instant signalClose){LaplaceTradingSessionEntity s=ensureCurrent(now);return entriesAllowed(now)&&signalClose.isAfter(s.getStartTime());}
    public boolean managementAllowed(Instant now){LaplaceTradingSessionEntity s=ensureCurrent(now);return s.getStatus()!=LaplaceSessionStatus.CLOSING&&s.getStatus()!=LaplaceSessionStatus.COMPLETED&&s.getStatus()!=LaplaceSessionStatus.INITIALIZING&&s.getStatus()!=LaplaceSessionStatus.INITIALIZATION_FAILED;}
    public String currentSessionId(Instant now){return ensureCurrent(now).getSessionId();}
    public BigDecimal currentMargin(Instant now){return ensureCurrent(now).getStartingMargin();}

    /** Returns false when a rollover/cold-start happened; callers must discard their pre-rollover work. */
    @Transactional public synchronized boolean ensureActiveSessionBeforeScan(Instant now) {
        LaplaceSessionWindow wanted=window(now);
        if(runtimeSessionId!=null && !runtimeSessionId.equals(wanted.sessionId())) { rollover(now); return false; }
        LaplaceTradingSessionEntity s=ensureCurrent(now);
        return s.getStatus()==LaplaceSessionStatus.ACTIVE && runtimeSessionId.equals(s.getSessionId());
    }

    @Transactional public synchronized void rollover(Instant now) {
        if(runtimeSessionId!=null) completeSession(runtimeSessionId,now);
        LaplaceTradingSessionEntity next=repository.findById(window(now).sessionId()).orElseGet(()->create(window(now)));
        if(next.getStatus()!=LaplaceSessionStatus.COMPLETED) initialize(next);
    }

    public synchronized LaplaceTradingSessionEntity ensureCurrent(Instant now) {
        LaplaceSessionWindow w=window(now);
        if(runtimeSessionId!=null&&!runtimeSessionId.equals(w.sessionId())) {rollover(now);return repository.findById(w.sessionId()).orElseThrow();}
        LaplaceTradingSessionEntity s=repository.findById(w.sessionId()).orElseGet(()->create(w));
        if(!w.sessionId().equals(runtimeSessionId) && s.getStatus()!=LaplaceSessionStatus.COMPLETED) initialize(s);
        return s;
    }

    @Transactional
    public void completeSession(String sessionId,Instant now) {
        LaplaceTradingSessionEntity s=repository.findByIdForUpdate(sessionId).orElse(null);
        if(s==null||s.getStatus()==LaplaceSessionStatus.COMPLETED)return;
        s.setEntryLocked(true);s.setStatus(LaplaceSessionStatus.CLOSING);repository.saveAndFlush(s);
        log.info("SESSION_END_STARTED sessionId={}",sessionId);
        open(sessionId).forEach(p->{if(execution.closeForSession(p.getId(),"SESSION_END_FORCE_CLOSE",now))log.info("SESSION_FORCE_CLOSE sessionId={} positionId={}",sessionId,p.getId());});
        if(!open(sessionId).isEmpty())throw new IllegalStateException("Session positions remain open");
        BigDecimal realized=realized(sessionId);BigDecimal ret=LaplaceSessionMath.returnRatio(realized,s.getStartingCapital());
        s.setRealizedPnl(realized);s.setFinalCapital(s.getStartingCapital().add(realized));s.setFinalReturnPct(ret);
        s.setNextSessionMargin(LaplaceSessionMath.nextMargin(s.getStartingMargin(),ret,properties.getLaplace().getSession().isCompoundPositiveSessionProfit()));
        s.setCompletedAt(now);s.setStatus(LaplaceSessionStatus.COMPLETED);repository.saveAndFlush(s);
        runtimeSessionId=null;log.info("SESSION_COMPLETED sessionId={} realizedNetPnl={} finalCapital={} finalReturnPct={} nextSessionMargin={}",sessionId,realized,s.getFinalCapital(),ret,s.getNextSessionMargin());
    }

    @Transactional
    public boolean evaluateProfitLock(Instant now) {
        if(runtimeSessionId==null)return false;
        LaplaceTradingSessionEntity s=repository.findByIdForUpdate(runtimeSessionId).orElse(null);
        if(s==null||s.getStatus()!=LaplaceSessionStatus.ACTIVE||s.isProfitTargetLocked())return false;
        BigDecimal realized=realized(s.getSessionId());
        BigDecimal projectedOpen=open(s.getSessionId()).stream().map(execution::projectedRemainingNet).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal projected=realized.add(projectedOpen);var cfg=properties.getLaplace().getSession();
        if(!LaplaceSessionMath.shouldProfitLock(s.getStartingCapital(),realized,projected,cfg.getRealizedProfitTargetPct(),cfg.getProjectedProfitFloorPct()))return false;
        s.setEntryLocked(true);s.setProfitTargetLocked(true);s.setProfitTargetLockedAt(now);s.setStatus(LaplaceSessionStatus.PROFIT_LOCKED);repository.saveAndFlush(s);
        log.info("SESSION_PROFIT_LOCK_STARTED sessionId={} realizedNetPnl={} projectedOpenNetPnl={} projectedSessionNetPnl={}",s.getSessionId(),realized,projectedOpen,projected);
        open(s.getSessionId()).forEach(p->execution.closeForSession(p.getId(),"SESSION_PROFIT_LOCK",now));
        s.setRealizedPnl(realized(s.getSessionId()));repository.saveAndFlush(s);log.info("SESSION_PROFIT_LOCK_COMPLETED sessionId={} realizedNetPnl={}",s.getSessionId(),s.getRealizedPnl());return true;
    }

    private List<LaplacePaperPositionEntity> open(String id){return positions.findByStrategyAndSessionIdAndStatus(LaplacePaperExecutionService.STRATEGY,id,LaplacePositionStatus.OPEN);}
    private BigDecimal realized(String id){
        BigDecimal closed=positions.findByStrategyAndSessionIdAndStatus(LaplacePaperExecutionService.STRATEGY,id,LaplacePositionStatus.CLOSED).stream().map(p->p.getNetPnl()==null?BigDecimal.ZERO:p.getNetPnl()).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal partial=open(id).stream().filter(p->Boolean.TRUE.equals(p.getPartialTakeProfitExecuted())).map(p->{BigDecimal ratio=p.getPartialTakeProfitQuantity().divide(p.getOriginalQuantity(),SCALE,RoundingMode.HALF_UP);return p.getPartialGrossPnl().subtract(p.getPartialExitFee()).subtract(p.getEntryFee().multiply(ratio));}).reduce(BigDecimal.ZERO,BigDecimal::add);
        return closed.add(partial).setScale(SCALE,RoundingMode.HALF_UP);
    }
    private LaplaceSessionWindow window(Instant now){var c=properties.getLaplace().getSession();return LaplaceSessionWindow.at(now,c.getDurationHours(),c.getEntryWindowHours());}
    private LaplaceTradingSessionEntity create(LaplaceSessionWindow w){var previous=repository.findFirstByStatusOrderByStartTimeDesc(LaplaceSessionStatus.COMPLETED);BigDecimal capital=previous.map(LaplaceTradingSessionEntity::getFinalCapital).orElse(properties.getLaplace().getInitialCapitalUsdt());BigDecimal margin=previous.map(LaplaceTradingSessionEntity::getNextSessionMargin).orElse(properties.getLaplace().getMarginPerPositionUsdt());return repository.saveAndFlush(LaplaceTradingSessionEntity.builder().sessionId(w.sessionId()).startTime(w.startTime()).entryCutoffTime(w.entryCutoffTime()).endTime(w.endTime()).startingCapital(capital).startingMargin(margin).realizedPnl(BigDecimal.ZERO).status(LaplaceSessionStatus.INITIALIZING).entryLocked(true).build());}
    private void initialize(LaplaceTradingSessionEntity s){s.setStatus(LaplaceSessionStatus.INITIALIZING);s.setEntryLocked(true);repository.saveAndFlush(s);try{initializer.initializeForSession(s);s.setStatus(LaplaceSessionStatus.ACTIVE);s.setEntryLocked(false);runtimeSessionId=s.getSessionId();repository.saveAndFlush(s);log.info("SESSION_STARTED sessionId={}",s.getSessionId());}catch(RuntimeException e){s.setStatus(LaplaceSessionStatus.INITIALIZATION_FAILED);s.setEntryLocked(true);repository.saveAndFlush(s);throw e;}}
}
