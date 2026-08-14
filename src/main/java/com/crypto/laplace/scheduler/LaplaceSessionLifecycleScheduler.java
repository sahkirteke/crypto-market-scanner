package com.crypto.laplace.scheduler;

import com.crypto.laplace.service.LaplaceRuntimeService;
import com.crypto.laplace.service.LaplaceStartupHistoryService;
import com.crypto.laplace.service.LaplaceTemporaryStateResetService;
import com.crypto.laplace.service.StartupMarketUniverseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class LaplaceSessionLifecycleScheduler {
    private final LaplaceRuntimeService runtime;
    private final LaplaceTemporaryStateResetService reset;
    private final StartupMarketUniverseService universe;
    private final LaplaceStartupHistoryService histories;

    @Scheduled(fixedDelay = 60000)
    public void advance() {
        if (!runtime.beginInitializationIfDue()) return;
        try {
            reset.clear();
            if (!reset.isEmpty()) throw new IllegalStateException("TEMPORARY_STATE_NOT_EMPTY");
            universe.initialize();
            if (!universe.isReady()) throw new IllegalStateException("UNIVERSE_NOT_READY");
            universe.symbols().forEach(histories::initializeSymbol);
            if (histories.readySymbols().isEmpty()) throw new IllegalStateException("HISTORY_NOT_READY");
            runtime.activateNextSession();
        } catch (RuntimeException failure) {
            log.error("LAPLACE_SESSION_INITIALIZATION_FAILED", failure);
        }
    }
}
