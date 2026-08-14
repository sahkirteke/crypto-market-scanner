package com.crypto.laplace.service;

import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.execution.LaplaceInvertedFalseTradeCoordinator;
import com.crypto.laplace.scheduler.LaplaceInvertedFalseStopLossScheduler;
import com.crypto.laplace.scheduler.LaplaceStopLossScheduler;
import com.crypto.laplace.scheduler.LaplaceThirtyMinuteScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@RequiredArgsConstructor
public class LaplaceTemporaryStateResetService {
    private final StartupMarketUniverseService universe;
    private final LaplaceStartupHistoryService histories;
    private final LaplacePaperTradeCoordinator coordinator;
    private final LaplaceThirtyMinuteScheduler thirtyMinuteScheduler;
    private final LaplaceStopLossScheduler stopLossScheduler;
    private final ObjectProvider<CacheManager> cacheManagers;
    private final LaplaceMarketDataGate marketDataGate;
    @Autowired private LaplaceInvertedFalseTradeCoordinator invertedFalseCoordinator;
    @Autowired private LaplaceInvertedFalseStopLossScheduler invertedFalseStopLossScheduler;

    /** Clears only TRUE execution state; shared signal history remains available to FALSE. */
    public void clearInvertedTrue() {
        coordinator.clearRuntimeState();
        stopLossScheduler.clearRuntimeState();
    }

    /** Clears only FALSE execution state; shared signal history remains available to TRUE. */
    public void clearInvertedFalse() {
        invertedFalseCoordinator.clearRuntimeState();
        invertedFalseStopLossScheduler.clearRuntimeState();
    }

    public void clear() {
        universe.clear();
        histories.clear();
        coordinator.clearRuntimeState();
        thirtyMinuteScheduler.clearRuntimeState();
        stopLossScheduler.clearRuntimeState();
        invertedFalseCoordinator.clearRuntimeState();
        invertedFalseStopLossScheduler.clearRuntimeState();
        cacheManagers.orderedStream().forEach(cacheManager -> cacheManager.getCacheNames().stream()
                .filter(name -> name.toLowerCase().contains("laplace"))
                .map(cacheManager::getCache).filter(java.util.Objects::nonNull).forEach(org.springframework.cache.Cache::clear));
    }

    /** Clears shared transient strategy data only when no enabled variant needs it. */
    public synchronized boolean clearSharedIfUnused() {
        if (marketDataGate.allowsMarketData()) return false;
        clear();
        return true;
    }

    public boolean isEmpty() {
        return !universe.isReady() && histories.readySymbols().isEmpty();
    }
}
