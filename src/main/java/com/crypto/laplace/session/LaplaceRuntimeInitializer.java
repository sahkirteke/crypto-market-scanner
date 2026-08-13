package com.crypto.laplace.session;

import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.scheduler.LaplaceThirtyMinuteScheduler;
import com.crypto.laplace.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** The sole cold-start pipeline, shared by process startup and every session rollover. */
@Slf4j @Service @RequiredArgsConstructor
public class LaplaceRuntimeInitializer {
    private final StartupMarketUniverseService universe;
    private final LaplaceStartupHistoryService history;
    private final LaplacePaperTradeCoordinator coordinator;
    private final LaplaceThirtyMinuteScheduler scheduler;

    public synchronized void initializeForSession(LaplaceTradingSessionEntity session) {
        log.info("SESSION_INITIALIZING sessionId={} startTime={} entryCutoffTime={} endTime={}",
                session.getSessionId(), session.getStartTime(), session.getEntryCutoffTime(), session.getEndTime());
        coordinator.resetForSession(); scheduler.resetForSession(); history.resetForSession();
        universe.resetForSession(session.getSessionId());
        log.info("SESSION_RUNTIME_RESET sessionId={}", session.getSessionId());
        universe.initialize();
        if (!universe.isReady()) throw new IllegalStateException("Laplace universe initialization failed");
        universe.symbols().forEach(history::initializeSymbol);
        if (history.readySymbols().isEmpty()) throw new IllegalStateException("Laplace history initialization failed");
        log.info("SESSION_COLD_START_COMPLETED sessionId={} symbols={}", session.getSessionId(), history.readySymbols().size());
    }
}
