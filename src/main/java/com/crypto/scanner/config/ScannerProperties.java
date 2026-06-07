package com.crypto.scanner.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "scanner")
public class ScannerProperties {
    private List<String> blacklist = new ArrayList<>();
    private Liquidity liquidity = new Liquidity();
    private Klines klines = new Klines();
    private MarketRegime marketRegime = new MarketRegime();
    private Funding funding = new Funding();
    private Scoring scoring = new Scoring();
    private Scheduler scheduler = new Scheduler();
    private EntryCandidate entryCandidate = new EntryCandidate();
    private EntrySignal entrySignal = new EntrySignal();
    private Paper paper = new Paper();

    @Getter
    @Setter
    public static class Scheduler {
        private Boolean enabled = false;
        private String oneHourCron = "0 2 * * * *";
        private String fourHourCron = "0 2 3,7,11,15,19,23 * * *";
        private String zone = "Europe/Istanbul";
    }

    @Getter
    @Setter
    public static class Liquidity {
        private BigDecimal minQuoteVolume24h = BigDecimal.valueOf(30_000_000L);
        private BigDecimal maxSpreadPct = new BigDecimal("0.08");
        private BigDecimal maxPump24hPct = BigDecimal.valueOf(25);
        private BigDecimal maxDump24hPct = BigDecimal.valueOf(-25);
    }

    @Getter
    @Setter
    public static class Klines {
        private Integer oneHourLimit = 250;
        private Integer fourHourLimit = 250;
        private Integer minClosedCandles = 220;
    }

    @Getter
    @Setter
    public static class Funding {
        private BigDecimal warningPositive = new BigDecimal("0.0005");
        private BigDecimal dangerPositive = new BigDecimal("0.001");
        private BigDecimal warningNegative = new BigDecimal("-0.0005");
        private BigDecimal dangerNegative = new BigDecimal("-0.001");
    }

    @Getter
    @Setter
    public static class Scoring {
        private Integer strongThreshold = 80;
        private Integer watchlistThreshold = 65;
        private Integer eliminatedThreshold = 50;
        private Integer directionDifferenceThreshold = 10;
    }

    @Getter
    @Setter
    public static class EntryCandidate {
        private Boolean enabled = true;
        private Integer maxCandidates = 10;
        private Integer minScore = 70;
        private Boolean allowWatchlist = true;
        private Boolean allowNeutralWatchlist = false;
        private Boolean allowHighRisk = false;
        private Integer maxLongCandidates = 5;
        private Integer maxShortCandidates = 5;
    }

    @Getter
    @Setter
    public static class EntrySignal {
        private Boolean enabled = true;
        private Integer minEnterScore = 75;
        private Integer minStrongEnterScore = 80;
        private Boolean allowWatchlistEntry = true;
        private Boolean allowMediumRiskEntry = true;
        private Boolean allowHighRiskEntry = false;
        private BigDecimal maxSpreadPct = new BigDecimal("0.08");
        private BigDecimal maxLong24hChangePct = BigDecimal.valueOf(18);
        private BigDecimal maxShort24hDumpPct = BigDecimal.valueOf(-18);
        private BigDecimal minQuoteVolume24h = BigDecimal.valueOf(30_000_000L);
        private Boolean blockMarketChop = false;
        private Boolean requireVolumeConfirmed = false;
    }

    @Getter
    @Setter
    public static class Paper {
        private Boolean enabled = true;
        private BigDecimal defaultNotionalUsdt = BigDecimal.valueOf(100);
        private Integer leverage = 3;
        private Boolean allowMultipleOpenSameSymbol = false;
        private Integer maxOpenPositions = 5;
        private Integer maxOpenLongPositions = 3;
        private Integer maxOpenShortPositions = 3;
        private Boolean allowMediumRisk = true;
        private Boolean allowHighRisk = false;
    }

    @Getter
    @Setter
    public static class MarketRegime {
        private BigDecimal breadthRiskOnMinPct = BigDecimal.valueOf(35);
        private BigDecimal breadthStrongPct = BigDecimal.valueOf(60);
        private BigDecimal breadthRiskOffMaxPct = BigDecimal.valueOf(20);
        private BigDecimal panicBtc24hDropPct = BigDecimal.valueOf(-5);
        private BigDecimal panicBtc4hDropPct = BigDecimal.valueOf(-3);
        private BigDecimal panicVolumeRatio1h = new BigDecimal("1.5");
        private BigDecimal panicVolumeRatio4hRule = new BigDecimal("1.8");
    }
}
