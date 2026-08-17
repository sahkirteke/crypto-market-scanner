package com.crypto.laplace.service;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Single authority for shared Binance strategy-data access. */
@Service
@RequiredArgsConstructor
public class LaplaceMarketDataGate {
    private final LaplaceStrategyProperties properties;
    private final LaplaceRuntimeService invertedTrueRuntime;

    public boolean allowsMarketData() {
        var laplace = properties.getLaplace();
        if (!laplace.isEnabled() || !laplace.isPaperExecutionEnabled()) return false;
        return laplace.getPaper().getInvertedTrue().isEnabled() && invertedTrueRuntime.allowsMarketData();
    }

    public boolean enabledTrue() {
        var laplace = properties.getLaplace();
        return laplace.isEnabled() && laplace.isPaperExecutionEnabled()
                && laplace.getPaper().getInvertedTrue().isEnabled();
    }

    /** FALSE production runtime is permanently disabled. */
    public boolean enabledFalse() { return false; }
}
