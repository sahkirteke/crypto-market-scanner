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
    private SingleTpSl singleTpSl = new SingleTpSl();
    private Trailing trailing = new Trailing();
    private PartialTakeProfit partialTakeProfit = new PartialTakeProfit();
    private IntrabarExit intrabarExit = new IntrabarExit();
    private String strategyVersion = "V20";
    private V20 v20 = new V20();

    @Getter
    @Setter
    public static class Scheduler {
        private Boolean enabled = true;
        private String oneHourCron = "0 2 * * * *";
        private String fourHourCron = "0 2 3,7,11,15,19,23 * * *";
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
        private String strategyVersion = "V20";
        private BigDecimal marginUsdt = BigDecimal.valueOf(100);
        private Integer leverage = 5;
        private BigDecimal positionNotionalUsdt = BigDecimal.valueOf(500);
        private BigDecimal notionalUsdt = BigDecimal.valueOf(500);
        private BigDecimal defaultNotionalUsdt = BigDecimal.valueOf(500);
        private Pnl pnl = new Pnl();
        private Fee fee = new Fee();
        private Boolean allowMultipleOpenSameSymbol = false;
        private Integer maxOpenPositions = 5;
        private Integer maxOpenLongPositions = 3;
        private Integer maxOpenShortPositions = 3;
        private Boolean allowMediumRisk = true;
        private Boolean allowHighRisk = false;
    }

    @Getter
    @Setter
    public static class Pnl {
        private Boolean writeUnleveragedAndLeveraged = true;
    }

    @Getter
    @Setter
    public static class Fee {
        private String mode = "MAKER";
        private BigDecimal makerFeePct = new BigDecimal("0.0002");
        private BigDecimal takerFeePct = new BigDecimal("0.0004");
        private BigDecimal slippagePct = new BigDecimal("0.0005");
        private String logZone = "Europe/Istanbul";
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
        private Integer leverage = 5;
        private BigDecimal takerFeePct = new BigDecimal("0.0004");
        private BigDecimal makerFeePct = new BigDecimal("0.0002");
        private BigDecimal slippagePct = new BigDecimal("0.0005");
    }

    @Getter
    @Setter
    public static class SingleTpSl {
        private Boolean enabled = true;
        private BigDecimal longTpPct = new BigDecimal("0.0200");
        private BigDecimal longSlPct = new BigDecimal("0.0140");
        private BigDecimal shortTpPct = new BigDecimal("0.0200");
        private BigDecimal shortSlPct = new BigDecimal("0.0140");
        private Boolean conservativeStopFirst = true;
    }

    @Getter
    @Setter
    public static class Trailing {
        private Boolean enabled = false;
    }

    @Getter
    @Setter
    public static class PartialTakeProfit {
        private Boolean enabled = false;
    }

    @Getter
    @Setter
    public static class IntrabarExit {
        private Boolean enabled = true;
        private String interval = "5m";
        private String cron = "10 */5 * * * *";
        private String zone = "Europe/Istanbul";
        private Integer klineLimit = 3;
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
    public static class MarketRegime {
        private BigDecimal breadthRiskOnMinPct = BigDecimal.valueOf(35);
        private BigDecimal breadthStrongPct = BigDecimal.valueOf(60);
        private BigDecimal breadthRiskOffMaxPct = BigDecimal.valueOf(20);
        private BigDecimal panicBtc24hDropPct = BigDecimal.valueOf(-5);
        private BigDecimal panicBtc4hDropPct = BigDecimal.valueOf(-3);
        private BigDecimal panicVolumeRatio1h = new BigDecimal("1.5");
        private BigDecimal panicVolumeRatio4hRule = new BigDecimal("1.8");
    }

    @Getter @Setter
    public static class V20 {
        private Boolean enabled = true;
        private String entryMode = "immediate";
        private Boolean allowMultipleEntriesPerScan = true;
        private Integer maxNewEntriesPerScan;
        private Integer maxOpenLongPositions;
        private Integer maxOpenShortPositions;
        private Boolean preventSameSymbolOppositePosition = true;
        private Boolean disableLayer2FiltersByDefault = true;
        private Boolean disableV18EarlyExit = true;
        private Boolean disableV19MarketRegimeShortFilter = true;
        private V20Long longConfig = new V20Long();
        private V20Short shortConfig = new V20Short();
        private V20Score score = new V20Score();
        public V20Long getLong() { return longConfig; }
        public void setLong(V20Long longConfig) { this.longConfig = longConfig; }
        public V20Short getShort() { return shortConfig; }
        public void setShort(V20Short shortConfig) { this.shortConfig = shortConfig; }
    }

    @Getter @Setter
    public static class V20Long {
        private Integer minSignalScore = 9;
        private BigDecimal rsiMin = new BigDecimal("38"); private BigDecimal rsiMax = new BigDecimal("62");
        private BigDecimal adxMin = new BigDecimal("16"); private BigDecimal adxMax = new BigDecimal("38");
        private BigDecimal atrPctMin = new BigDecimal("1.2"); private BigDecimal atrPctMax = new BigDecimal("3.4");
        private BigDecimal ema20Ema50CompPctMin = new BigDecimal("-3.0"); private BigDecimal ema20Ema50CompPctMax = new BigDecimal("2.0");
        private BigDecimal closeEma20DistPctMin = new BigDecimal("-3.0"); private BigDecimal closeEma20DistPctMax = new BigDecimal("2.5");
        private BigDecimal distFromLow20BaseMinPct = new BigDecimal("0.8"); private BigDecimal distFromLow20BaseMaxPct = new BigDecimal("9.0");
        private BigDecimal diDiffBaseMinExclusive = new BigDecimal("-12");
        private BigDecimal fourHourTakerBuyMinExclusive = new BigDecimal("0.47");
        private BigDecimal closePositionMinExclusive = new BigDecimal("0.20");
        private BigDecimal volumeRatio20Max = new BigDecimal("2.0"); private BigDecimal rangePctMax = new BigDecimal("4.2");
        private BigDecimal fundingNegativeThreshold = new BigDecimal("-0.00002000"); private BigDecimal fundingPositiveThreshold = new BigDecimal("0.00007500");
        private BigDecimal oneHourTakerBuyMinExclusive = new BigDecimal("0.49");
        private BigDecimal distFromLow20EntryMinPct = new BigDecimal("0.5"); private BigDecimal distFromLow20EntryMaxPct = new BigDecimal("6.0");
        private BigDecimal diDiffEntryMin = new BigDecimal("-5");
    }

    @Getter @Setter
    public static class V20Short {
        private Integer minSignalScore = 9;
        private BigDecimal rsiMin = new BigDecimal("38"); private BigDecimal rsiMax = new BigDecimal("62");
        private BigDecimal adxMin = new BigDecimal("16"); private BigDecimal adxMax = new BigDecimal("38");
        private BigDecimal atrPctMin = new BigDecimal("1.2"); private BigDecimal atrPctMax = new BigDecimal("3.4");
        private BigDecimal ema20Ema50CompPctMin = new BigDecimal("-2.0"); private BigDecimal ema20Ema50CompPctMax = new BigDecimal("3.0");
        private BigDecimal closeEma20DistPctMin = new BigDecimal("-2.5"); private BigDecimal closeEma20DistPctMax = new BigDecimal("3.0");
        private BigDecimal distFromHigh20BaseMinPct = new BigDecimal("0.8"); private BigDecimal distFromHigh20BaseMaxPct = new BigDecimal("9.0");
        private BigDecimal diDiffBaseMaxExclusive = new BigDecimal("12");
        private BigDecimal fourHourTakerBuyMaxExclusive = new BigDecimal("0.53");
        private BigDecimal closePositionMaxExclusive = new BigDecimal("0.80");
        private BigDecimal volumeRatio20Max = new BigDecimal("2.0"); private BigDecimal rangePctMax = new BigDecimal("4.2");
        private BigDecimal fundingNegativeThreshold = new BigDecimal("-0.00007500"); private BigDecimal fundingPositiveThreshold = new BigDecimal("0.00002000");
        private BigDecimal oneHourTakerBuyMaxExclusive = new BigDecimal("0.51");
        private BigDecimal distFromHigh20EntryMinPct = new BigDecimal("0.5"); private BigDecimal distFromHigh20EntryMaxPct = new BigDecimal("6.0");
        private BigDecimal diDiffEntryMax = new BigDecimal("5");
        private BigDecimal fundingMa3Min = new BigDecimal("0.000075");
        private BigDecimal fourHourTakerBuyEntryMax = new BigDecimal("0.47");
    }

    @Getter @Setter
    public static class V20Score {
        private Integer minSignalScore = 9;
        private BigDecimal adxIdealMin = new BigDecimal("18"); private BigDecimal adxIdealMax = new BigDecimal("32");
        private BigDecimal atrIdealMin = new BigDecimal("1.4"); private BigDecimal atrIdealMax = new BigDecimal("3.0");
        private BigDecimal volumeRatioIdealMin = new BigDecimal("0.50"); private BigDecimal volumeRatioIdealMax = new BigDecimal("1.50");
        private BigDecimal rangePctLowMax = new BigDecimal("3.2");
        private V20ScoreLong longConfig = new V20ScoreLong(); private V20ScoreShort shortConfig = new V20ScoreShort();
        public V20ScoreLong getLong() { return longConfig; } public void setLong(V20ScoreLong longConfig) { this.longConfig = longConfig; }
        public V20ScoreShort getShort() { return shortConfig; } public void setShort(V20ScoreShort shortConfig) { this.shortConfig = shortConfig; }
    }
    @Getter @Setter
    public static class V20ScoreLong {
        private BigDecimal distLow20IdealMinPct = new BigDecimal("1.0"); private BigDecimal distLow20IdealMaxPct = new BigDecimal("7.0");
        private BigDecimal closePositionIdealMin = new BigDecimal("0.25"); private BigDecimal closePositionIdealMax = new BigDecimal("0.85");
        private BigDecimal bbPositionIdealMin = new BigDecimal("0.25"); private BigDecimal bbPositionIdealMax = new BigDecimal("0.80");
        private BigDecimal fundingMa3PositiveSupport = new BigDecimal("0.000055"); private BigDecimal fundingMa3NegativeSupport = new BigDecimal("-0.00002000");
        private BigDecimal oneHourStrongTakerMin = new BigDecimal("0.51");
        private BigDecimal oneHourClosePositionIdealMin = new BigDecimal("0.30"); private BigDecimal oneHourClosePositionIdealMax = new BigDecimal("0.85");
    }
    @Getter @Setter
    public static class V20ScoreShort {
        private BigDecimal distHigh20IdealMinPct = new BigDecimal("1.0"); private BigDecimal distHigh20IdealMaxPct = new BigDecimal("7.0");
        private BigDecimal closePositionIdealMin = new BigDecimal("0.15"); private BigDecimal closePositionIdealMax = new BigDecimal("0.75");
        private BigDecimal bbPositionIdealMin = new BigDecimal("0.20"); private BigDecimal bbPositionIdealMax = new BigDecimal("0.75");
        private BigDecimal fundingMa3PositiveSupport = new BigDecimal("0.00002000"); private BigDecimal fundingMa3NegativeSupport = new BigDecimal("-0.000055");
        private BigDecimal oneHourStrongTakerBuyMax = new BigDecimal("0.49");
        private BigDecimal oneHourClosePositionIdealMin = new BigDecimal("0.15"); private BigDecimal oneHourClosePositionIdealMax = new BigDecimal("0.70");
    }

}
