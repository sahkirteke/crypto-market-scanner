package com.crypto.laplace.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "trading")
public class LaplaceStrategyProperties {
    private String activeStrategy = "LAPLACE_KERNEL_30M";
    private String executionMode = "PAPER";
    private boolean oldStrategyEnabled;
    private Laplace laplace = new Laplace();

    public void validatePhaseOne() {
        if (!"PAPER".equals(executionMode)) throw new IllegalStateException("Laplace execution mode must be PAPER; LIVE execution is forbidden");
        if (oldStrategyEnabled && laplace.enabled) throw new IllegalStateException("Old and Laplace strategies cannot be enabled together");
        if (oldStrategyEnabled && laplace.paperExecutionEnabled) throw new IllegalStateException("Legacy and Laplace execution cannot be enabled together");
        if (!"LAPLACE_KERNEL_30M".equals(activeStrategy) || !laplace.enabled) return;
        if (!"30m".equals(laplace.timeframe) || !"LAPLACE".equals(laplace.kernel)
                || laplace.bandwidth != 14 || !"CLOSE".equals(laplace.source) || laplace.repaint) {
            throw new IllegalStateException("Invalid immutable Laplace phase-one configuration");
        }
    }

    @Getter @Setter
    public static class Laplace {
        private boolean enabled = true;
        private boolean executionEnabled;
        private boolean paperExecutionEnabled;
        private String timeframe = "30m";
        private String kernel = "LAPLACE";
        private int bandwidth = 14;
        private String source = "CLOSE";
        private boolean repaint;
        private int klineLimit = 100;
        private String diagnosticDirectory = "logs/laplace";
    }
}
