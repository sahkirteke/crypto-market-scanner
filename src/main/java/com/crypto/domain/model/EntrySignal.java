package com.crypto.domain.model;

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
    private String blockReason;
    private Integer entryPriorityScore;
    private Integer scannerScore;
    private RiskLevel riskLevel;
    private MarketRegime marketRegime;
    private BigDecimal marketBreadthPct;
    private BigDecimal close1h;
    private BigDecimal ema20_1h;
    private BigDecimal macdHist_1h;
    private BigDecimal previousMacdHist_1h;
    private BigDecimal rsi14_1h;
    private BigDecimal previousRsi14_1h;
    private BigDecimal volumeRatio_1h;
    private BigDecimal fundingRate;
    private BigDecimal spreadPct;
    private BigDecimal priceChange24hPct;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
    private Instant signalTime;
}
