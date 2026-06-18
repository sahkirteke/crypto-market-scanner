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
    private Boolean safeMode = true;
    private List<String> blacklist = new ArrayList<>();
    private Liquidity liquidity = new Liquidity();
    private Klines klines = new Klines();
    private MarketRegime marketRegime = new MarketRegime();
    private Funding funding = new Funding();
    private Scoring scoring = new Scoring();
    private Scheduler scheduler = new Scheduler();
    private PaperAuto paperAuto = new PaperAuto();
    private EntryCandidate entryCandidate = new EntryCandidate();
    private EntrySignal entrySignal = new EntrySignal();
    private Paper paper = new Paper();
    private PaperExit paperExit = new PaperExit();
    private PaperRisk paperRisk = new PaperRisk();
    private PaperCost paperCost = new PaperCost();
    private Trading trading = new Trading();
    private Exit exit = new Exit();
    private Entry entry = new Entry();

    @Getter
    @Setter
    public static class Scheduler {
        private Boolean enabled = true;
        private String oneHourCron = "0 55 * * * *";
        private String fourHourCron = "0 55 2,6,10,14,18,22 * * *";
        private String zone = "Europe/Istanbul";
    }


    @Getter
    @Setter
    public static class PaperAuto {
        private Boolean enabled = true;
        private Boolean openAfterScan = true;
        private Boolean evaluateEnabled = true;
        private String evaluateCron = "0 */5 * * * *";
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
        private Boolean allowWatchlist = false;
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
        private Boolean allowWatchlistEntry = false;
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
    public static class PaperRisk {
        private BigDecimal atrStopMultiplier = new BigDecimal("1.2");
        private BigDecimal minStopDistancePct = new BigDecimal("0.007");
        private BigDecimal maxStopDistancePct = new BigDecimal("0.018");
        private BigDecimal tp1RMultiple = new BigDecimal("1.0");
        private BigDecimal tp2RMultiple = new BigDecimal("2.0");
        private BigDecimal tp1ClosePct = new BigDecimal("50");
        private BigDecimal tp2ClosePct = new BigDecimal("25");
        private BigDecimal trailingRemainingPct = new BigDecimal("25");
        private BigDecimal feeBufferPct = new BigDecimal("0.0005");
        private BigDecimal trailingAtrMultiplier = new BigDecimal("1.2");
    }

    @Getter
    @Setter
    public static class PaperCost {
        private Integer leverage = 3;
        private BigDecimal takerFeePct = new BigDecimal("0.0004");
        private BigDecimal slippagePct = new BigDecimal("0.0005");
    }

    @Getter
    @Setter
    public static class PaperExit {
        private Boolean enabled = true;
        private BigDecimal takeProfitPct = new BigDecimal("1.0");
        private BigDecimal stopLossPct = new BigDecimal("0.6");
        private Integer timeStopMinutes = 240;
        private Boolean timeStopCloseOnlyIfNonPositive = true;
        private Integer barMinutes = 60;
        private Boolean intrabarCheckEnabled = true;
        private String intrabarInterval = "5m";
        private String intrabarCron = "10 */5 * * * *";
        private String intrabarZone = "Europe/Istanbul";
        private Integer intrabarKlineLimit = 3;
    }


    @Getter
    @Setter
    public static class Trading {
        private BigDecimal initialBalanceUsdt = new BigDecimal("100");
        private BigDecimal marginPerTradeUsdt = new BigDecimal("10");
        private Integer leverage = 10;
        private BigDecimal makerFeeRate = new BigDecimal("0.0002");
        private BigDecimal takerFeeRate = new BigDecimal("0.0005");
        private Boolean useAdjustedPricesForPnl = true;
    }

    @Getter
    @Setter
    public static class Exit {
        private Tp tp = new Tp();
        private StopLoss stopLoss = new StopLoss();
        private String intrabarExecutionMode = "CONSERVATIVE";
        private BreakEvenAfterTp1 breakEvenAfterTp1 = new BreakEvenAfterTp1();
        private AfterTp2 afterTp2 = new AfterTp2();
        private EarlyExit earlyExit = new EarlyExit();
    }

    @Getter
    @Setter
    public static class Tp {
        private BigDecimal maxTp1Pct = new BigDecimal("1.40");
        private BigDecimal maxTp2Pct = new BigDecimal("2.00");
        private BigDecimal tp1CloseRatio = new BigDecimal("0.50");
        private BigDecimal tp2CloseRatio = new BigDecimal("0.25");
        private BigDecimal trailingCloseRatio = new BigDecimal("0.25");
    }

    @Getter
    @Setter
    public static class StopLoss {
        private BigDecimal maxSlPct = new BigDecimal("1.00");
        private String stopExitFeeType = "TAKER";
    }

    @Getter
    @Setter
    public static class BreakEvenAfterTp1 {
        private Boolean enabled = true;
        private Boolean includeFees = true;
        private BigDecimal extraSlippagePct = new BigDecimal("0.02");
    }

    @Getter
    @Setter
    public static class AfterTp2 {
        private Boolean moveSlToTp1 = true;
        private String exitReason = "TRAILING_STOP_AT_TP1_AFTER_TP2";
    }

    @Getter
    @Setter
    public static class EarlyExit {
        private Boolean enabled = true;
        private Boolean onlyBeforeTp1 = true;
        private String exitFeeType = "TAKER";
        private TimeNegative timeNegative = new TimeNegative();
        private AdversePct adversePct = new AdversePct();
    }

    @Getter
    @Setter
    public static class TimeNegative {
        private Boolean enabled = true;
        private Integer minutes = 15;
        private Boolean requireNegativePnl = true;
        private BigDecimal maxFavorablePct = new BigDecimal("0.40");
        private String exitReason = "EARLY_EXIT_TIME_NEGATIVE";
    }

    @Getter
    @Setter
    public static class AdversePct {
        private Boolean enabled = true;
        private BigDecimal adversePct = new BigDecimal("-0.80");
        private BigDecimal maxFavorablePct = new BigDecimal("0.50");
        private String exitReason = "EARLY_EXIT_ADVERSE_PCT";
    }

    @Getter
    @Setter
    public static class Entry {
        private Filters filters = new Filters();
    }

    @Getter
    @Setter
    public static class Filters {
        private Long24hChange long24hChange = new Long24hChange();
    }

    @Getter
    @Setter
    public static class Long24hChange {
        private Boolean enabled = true;
        private BigDecimal max24hChangePct = new BigDecimal("5.00");
        private String rejectReason = "LONG_24H_CHANGE_TOO_HIGH";
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
