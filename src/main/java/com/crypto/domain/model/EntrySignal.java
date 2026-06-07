package com.crypto.domain.model;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
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
    private String symbol;
    private PositionSide side;
    private EntryAction action;
    private Integer score;
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
    private BigDecimal close1h;
    private BigDecimal ema20_1h;
    private BigDecimal macdHist_1h;
    private BigDecimal previousMacdHist_1h;
    private BigDecimal rsi14_1h;
    private BigDecimal previousRsi14_1h;
    private BigDecimal volumeRatio_1h;
}
