package com.crypto.laplace.service;

import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.scheduler.LaplaceStopLossScheduler;
import com.crypto.laplace.scheduler.LaplaceThirtyMinuteScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

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

    /** Clears TRUE execution state without discarding shared market history. */
    public void clearInvertedTrue() {
        coordinator.clearRuntimeState();
        stopLossScheduler.clearRuntimeState();
    }

    /** Compatibility no-op: FALSE has no production runtime state. */
    public void clearInvertedFalse() { }

    public void clear() {
        universe.clear();
        histories.clear();
        coordinator.clearRuntimeState();
        thirtyMinuteScheduler.clearRuntimeState();
        stopLossScheduler.clearRuntimeState();
        cacheManagers.orderedStream().forEach(cacheManager -> cacheManager.getCacheNames().stream()
                .filter(name -> name.toLowerCase().contains("laplace"))
                .map(cacheManager::getCache).filter(java.util.Objects::nonNull).forEach(org.springframework.cache.Cache::clear));
    }

    /** Clears transient strategy data while TRUE is not consuming market data. */
    public synchronized boolean clearSharedIfUnused() {
        if (marketDataGate.allowsMarketData()) return false;
        clear();
        return true;
    }

    public boolean isEmpty() {
        return !universe.isReady() && histories.readySymbols().isEmpty();
    }
}
