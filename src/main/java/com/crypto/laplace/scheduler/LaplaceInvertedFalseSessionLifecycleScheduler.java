package com.crypto.laplace.scheduler;

import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import com.crypto.laplace.service.LaplaceStartupHistoryService;
import com.crypto.laplace.service.LaplaceTemporaryStateResetService;
import com.crypto.laplace.service.StartupMarketUniverseService;
import com.crypto.laplace.service.LaplaceMarketDataGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("false")
public class LaplaceInvertedFalseSessionLifecycleScheduler {
    private final LaplaceInvertedFalseRuntimeService runtime;
    private final LaplaceTemporaryStateResetService reset;
    private final StartupMarketUniverseService universe;
    private final LaplaceStartupHistoryService histories;
    private final LaplaceMarketDataGate marketDataGate;

    @Scheduled(fixedDelay = 60000)
    public void advance() {
        if (!marketDataGate.enabledFalse() || !runtime.beginInitializationIfDue()) return;
        try {
            reset.clearInvertedFalse();
            if (!universe.isReady()) universe.initialize();
            if (!universe.isReady()) throw new IllegalStateException("UNIVERSE_NOT_READY");
            universe.symbols().forEach(symbol -> { if (!histories.isReady(symbol)) histories.initializeSymbol(symbol); });
            if (histories.readySymbols().isEmpty()) throw new IllegalStateException("HISTORY_NOT_READY");
            runtime.activateNextSession();
        } catch (RuntimeException failure) {
            log.error("LAPLACE_SESSION_INITIALIZATION_FAILED variant=INVERTED_FALSE", failure);
        }
    }
}
