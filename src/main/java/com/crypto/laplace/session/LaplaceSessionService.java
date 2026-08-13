package com.crypto.laplace.session;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import java.math.BigDecimal;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor @Order(50)
public class LaplaceSessionService implements ApplicationRunner {
    private final LaplaceTradingSessionRepository repository;
    private final LaplaceStrategyProperties properties;
    private final LaplaceRuntimeInitializer initializer;
    private volatile String runtimeSessionId;

    @Override public void run(ApplicationArguments args) { ensureCurrent(Instant.now()); }

    public boolean entriesAllowed(Instant now) {
        LaplaceTradingSessionEntity s=current(now);
        return s != null && s.getStatus()==LaplaceSessionStatus.ACTIVE && !s.isEntryLocked()
                && now.isBefore(s.getEntryCutoffTime());
    }
    public BigDecimal currentMargin(Instant now) {
        LaplaceTradingSessionEntity s=current(now);
        return s==null ? properties.getLaplace().getMarginPerPositionUsdt() : s.getStartingMargin();
    }
    public synchronized LaplaceTradingSessionEntity ensureCurrent(Instant now) {
        var cfg=properties.getLaplace().getSession();
        LaplaceSessionWindow w=LaplaceSessionWindow.at(now,cfg.getDurationHours(),cfg.getEntryWindowHours());
        var existing=repository.findById(w.sessionId());
        LaplaceTradingSessionEntity s=existing.orElseGet(()->create(w));
        if (s.getStatus()==LaplaceSessionStatus.COMPLETED) return s;
        if (!s.getSessionId().equals(runtimeSessionId)) initialize(s);
        return s;
    }
    private LaplaceTradingSessionEntity current(Instant now){return ensureCurrent(now);}
    private LaplaceTradingSessionEntity create(LaplaceSessionWindow w) {
        var completed=repository.findFirstByStatusOrderByStartTimeDesc(LaplaceSessionStatus.COMPLETED);
        BigDecimal capital=completed.map(LaplaceTradingSessionEntity::getFinalCapital).orElse(properties.getLaplace().getInitialCapitalUsdt());
        BigDecimal margin=completed.map(LaplaceTradingSessionEntity::getNextSessionMargin).orElse(properties.getLaplace().getMarginPerPositionUsdt());
        return repository.saveAndFlush(LaplaceTradingSessionEntity.builder().sessionId(w.sessionId()).startTime(w.startTime())
                .entryCutoffTime(w.entryCutoffTime()).endTime(w.endTime()).startingCapital(capital).startingMargin(margin)
                .realizedPnl(BigDecimal.ZERO).status(LaplaceSessionStatus.INITIALIZING).entryLocked(true).build());
    }
    private void initialize(LaplaceTradingSessionEntity s) {
        s.setStatus(LaplaceSessionStatus.INITIALIZING);s.setEntryLocked(true);repository.saveAndFlush(s);
        try { initializer.initializeForSession(s); s.setStatus(LaplaceSessionStatus.ACTIVE);s.setEntryLocked(false);
            runtimeSessionId=s.getSessionId();repository.saveAndFlush(s);log.info("SESSION_STARTED sessionId={}",s.getSessionId());
        } catch(RuntimeException e) {s.setStatus(LaplaceSessionStatus.INITIALIZATION_FAILED);s.setEntryLocked(true);repository.saveAndFlush(s);throw e;}
    }
}
