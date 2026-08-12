package com.crypto.laplace.config;

import java.math.BigDecimal;
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
        if (laplace.startupClosedCandleCount != 20
                || laplace.initialCapitalUsdt.compareTo(new BigDecimal("100")) != 0
                || laplace.marginPerPositionUsdt.compareTo(new BigDecimal("5")) != 0
                || laplace.notionalUsdt.compareTo(new BigDecimal("75")) != 0
                || laplace.leverage != 15
                || laplace.stopLossPct.compareTo(new BigDecimal("0.05")) != 0
                || laplace.notionalUsdt.compareTo(laplace.marginPerPositionUsdt.multiply(BigDecimal.valueOf(laplace.leverage))) != 0
                || !"MARKET".equals(laplace.orderType)
                || laplace.takerFeeRate == null
                || laplace.takerFeeRate.signum() < 0
                || laplace.fiveMinuteHistoryLimit < 500 || laplace.volumeProfileWindowBars != 24
                || laplace.volumeProfileBins != 36 || laplace.longAtrThreshold.signum() < 0
                || laplace.longDistanceFromLowThresholdPct.signum() < 0 || laplace.shortEmaGapThresholdPct.signum() < 0
                || laplace.shortEma50RiseThresholdPct.signum() < 0 || laplace.shortRawSlopeThreshold.signum() < 0
                || laplace.volumeProfileMinGapPct.signum() < 0 || laplace.partialTakeProfitPct.signum() <= 0
                || laplace.partialCloseRatio.signum() <= 0 || laplace.partialCloseRatio.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalStateException("Invalid immutable Laplace paper execution configuration");
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
        private int startupClosedCandleCount = 20;
        private BigDecimal initialCapitalUsdt = new BigDecimal("100");
        private BigDecimal marginPerPositionUsdt = new BigDecimal("5");
        private BigDecimal notionalUsdt = new BigDecimal("75");
        private int leverage = 15;
        private BigDecimal stopLossPct = new BigDecimal("0.05");
        private String stopLossCron = "1 */5 * * * *";
        private String orderType = "MARKET";
        private BigDecimal takerFeeRate = new BigDecimal("0.0004");
        private int fiveMinuteHistoryLimit = 500;
        private boolean kural5Enabled = true;
        private BigDecimal longAtrThreshold = new BigDecimal("2.0");
        private BigDecimal longDistanceFromLowThresholdPct = new BigDecimal("0.50");
        private BigDecimal shortEmaGapThresholdPct = new BigDecimal("0.25");
        private BigDecimal shortEma50RiseThresholdPct = new BigDecimal("0.25");
        private BigDecimal shortRawSlopeThreshold = new BigDecimal("0.15");
        private int volumeProfileWindowBars = 24;
        private int volumeProfileBins = 36;
        private BigDecimal volumeProfileMinGapPct = new BigDecimal("0.10");
        private BigDecimal partialTakeProfitPct = new BigDecimal("0.03");
        private BigDecimal partialCloseRatio = new BigDecimal("0.25");
        private String diagnosticDirectory = "logs/laplace";
        private String tradeDirectory = "logs/laplace-trades";
    }
}
