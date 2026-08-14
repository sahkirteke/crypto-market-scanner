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
    private final LaplaceInvertedFalseRuntimeService invertedFalseRuntime;

    public boolean allowsMarketData() {
        var laplace = properties.getLaplace();
        if (!laplace.isEnabled() || !laplace.isPaperExecutionEnabled()) return false;
        return laplace.getPaper().getInvertedTrue().isEnabled() && invertedTrueRuntime.allowsMarketData()
                || laplace.getPaper().getInvertedFalse().isEnabled() && invertedFalseRuntime.allowsMarketData();
    }

    public boolean enabledTrue() {
        var laplace = properties.getLaplace();
        return laplace.isEnabled() && laplace.isPaperExecutionEnabled()
                && laplace.getPaper().getInvertedTrue().isEnabled();
    }

    public boolean enabledFalse() {
        var laplace = properties.getLaplace();
        return laplace.isEnabled() && laplace.isPaperExecutionEnabled()
                && laplace.getPaper().getInvertedFalse().isEnabled();
    }
}
