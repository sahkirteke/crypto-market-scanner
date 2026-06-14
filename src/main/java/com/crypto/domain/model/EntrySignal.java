package com.crypto.domain.model;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.FourHourAlignment;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.ScanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntrySignal {
    private Long scanRunId;
    private ScanType sourceScanType;
    private Long candidateId;
    private String symbol;
    private PositionSide side;
    private EntryAction action;
    private Integer score;
    private BigDecimal baseEntryScore;
    private BigDecimal bbScore;
    private BigDecimal finalEntryScore;
    private Integer longScore;
    private Integer shortScore;
    private CoinClassification sourceClassification;
    private DirectionBias directionBias;
    private RiskLevel riskLevel;
    private BigDecimal entryPrice;
    private BigDecimal lastPrice;
    private BigDecimal spreadPct;
    private BigDecimal priceChange24hPct;
    private BigDecimal quoteVolume24h;
    private BigDecimal fundingRate;
    private BigDecimal openInterest;
    private BigDecimal marketBreadthPct;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
    private String signalReason;
    private String blockReason;
    private Instant signalTime;

    private Integer entryPriorityScore;
    private Integer scannerScore;
    private MarketRegime marketRegime;
    private String entryTrigger;
    private BigDecimal close1h;
    private BigDecimal previousClose1h;
    private BigDecimal previous1hHigh;
    private BigDecimal previous1hLow;
    private BigDecimal ema20_1h;
    private BigDecimal ema50_1h;
    private BigDecimal ema200_1h;
    private BigDecimal macdHist_1h;
    private BigDecimal previousMacdHist_1h;
    private BigDecimal rsi14_1h;
    private BigDecimal previousRsi14_1h;
    private BigDecimal volumeRatio_1h;
    private BigDecimal atr14_1h;
    private BigDecimal close4h;
    private BigDecimal ema20_4h;
    private BigDecimal ema50_4h;
    private BigDecimal ema200_4h;
    private BigDecimal rsi14_4h;
    private BigDecimal macdHist_4h;
    private BigDecimal atr14_4h;
    private BigDecimal volumeRatio_4h;
    private FourHourAlignment fourHourAlignment;
    private Integer riskPenalty;
    private Integer spreadPenalty;
    private Integer fundingPenalty;
    private Integer sidePenalty;
    private Integer fourHourPenalty;
    private Integer symbolCooldownPenalty;
    private Integer volumeConfirmationBonus;
    private Integer marketRegimeAlignmentBonus;
    private Integer fourHourAlignmentBonus;
    private Boolean cooldownPenaltyApplied;
    private Integer openPositionCount;
    private Integer openShortPositionCount;
    private Integer newEntriesInCurrentScan;
    @Builder.Default
    private List<String> bbReasons = new ArrayList<>();
    private BigDecimal bbPercentB;
    private BigDecimal bbWidth;
    private BigDecimal bbUpper;
    private BigDecimal bbMiddle;
    private BigDecimal bbLower;
    private Boolean bbUpperTouched;
    private Boolean bbLowerTouched;
    private Boolean bbUpperClosedOutside;
    private Boolean bbLowerClosedOutside;
}
