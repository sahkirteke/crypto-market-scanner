package com.crypto.laplace.config;

import java.math.BigDecimal;
import java.time.Duration;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
        if (laplace.startupClosedCandleCount != 20
                || laplace.initialCapitalUsdt.compareTo(new BigDecimal("350")) != 0
                || laplace.marginPerPositionUsdt.compareTo(new BigDecimal("5")) != 0
                || laplace.notionalUsdt.compareTo(new BigDecimal("75")) != 0
                || laplace.leverage != 15
                || laplace.stopLossPct.compareTo(new BigDecimal("0.05")) != 0
                || laplace.paper.invertedTrue.stopLossPct.compareTo(new BigDecimal("0.035")) != 0
                || laplace.paper.invertedFalse.stopLossPct.compareTo(laplace.stopLossPct) != 0
                || laplace.paper.invertedTrue.softFilter.atrMinPct != 5.0
                || laplace.paper.invertedTrue.softFilter.slopeStrengthMin != 0.16
                || !Duration.ofHours(4).equals(laplace.paper.invertedTrue.symbolStopLossCooldown)
                || laplace.notionalUsdt.compareTo(laplace.marginPerPositionUsdt.multiply(BigDecimal.valueOf(laplace.leverage))) != 0
                || !"MARKET".equals(laplace.orderType)
                || laplace.takerFeeRate == null
                || laplace.takerFeeRate.signum() < 0) {
            throw new IllegalStateException("Invalid immutable Laplace paper execution configuration");
        }
    }

    @Getter @Setter
    public static class Laplace {
        private boolean enabled = true;
        private boolean executionEnabled;
        /** Backward-compatible master switch: it gates both variants equally. */
        private boolean paperExecutionEnabled = true;
        private Paper paper = new Paper();
        private String timeframe = "30m";
        private String kernel = "LAPLACE";
        private int bandwidth = 14;
        private String source = "CLOSE";
        private boolean repaint;
        private int klineLimit = 100;
        private int startupClosedCandleCount = 20;
        private BigDecimal initialCapitalUsdt = new BigDecimal("350");
        private BigDecimal marginPerPositionUsdt = new BigDecimal("5");
        private BigDecimal notionalUsdt = new BigDecimal("75");
        private int leverage = 15;
        private BigDecimal profitTargetPct = new BigDecimal("5");
        private BigDecimal minimumLockedProfitPct = new BigDecimal("4.7");
        private BigDecimal stopLossPct = new BigDecimal("0.05");
        private String stopLossCron = "1 */5 * * * *";
        private String orderType = "MARKET";
        private BigDecimal takerFeeRate = new BigDecimal("0.0004");
        private String diagnosticDirectory = "logs/laplace";
        private String tradeDirectory = "logs/laplace-trades";

        @Getter @Setter
        public static class Paper {
            private Variant invertedTrue = new Variant(true,
                    "signals/laplace/inverted-true/trades", "signals/laplace/inverted-true/diagnostics");
            private Variant invertedFalse = new Variant(true,
                    "signals/laplace/inverted-false/trades", "signals/laplace/inverted-false/diagnostics");

            public Paper() { invertedTrue.setStopLossPct(new BigDecimal("0.035")); }
        }

        @Getter @Setter @NoArgsConstructor
        public static class Variant {
            private boolean enabled;
            private String tradeDirectory;
            private String diagnosticDirectory;
            private BigDecimal stopLossPct = new BigDecimal("0.05");
            private SoftFilter softFilter = new SoftFilter();
            private Duration symbolStopLossCooldown = Duration.ofHours(4);

            public Variant(boolean enabled, String tradeDirectory, String diagnosticDirectory) {
                this.enabled = enabled;
                this.tradeDirectory = tradeDirectory;
                this.diagnosticDirectory = diagnosticDirectory;
            }
        }

        @Getter @Setter
        public static class SoftFilter {
            private double atrMinPct = 5.0;
            private double slopeStrengthMin = 0.16;
        }
    }
}
