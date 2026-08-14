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

    public boolean isEmpty() {
        return !universe.isReady() && histories.readySymbols().isEmpty();
    }
}
