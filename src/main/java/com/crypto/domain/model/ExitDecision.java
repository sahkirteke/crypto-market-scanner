package com.crypto.domain.model;

import com.crypto.common.enums.ExitReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.PositionStatus;
import com.crypto.common.enums.ReasonTag;
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
public class ExitDecision {
    private Long positionId;
    private String symbol;
    private PositionSide side;
    private PositionStatus statusBefore;
    private PositionStatus statusAfter;
    private ExitReason exitReason;
    private String triggerType;
    private Instant candleOpenTime;
    private Instant candleCloseTime;
    private BigDecimal candleHigh;
    private BigDecimal candleLow;
    private BigDecimal candleClose;
    private BigDecimal entryPrice;
    private BigDecimal entryPriceAdjusted;
    private BigDecimal exitPrice;
    private BigDecimal exitPriceAdjusted;
    private BigDecimal currentStopBefore;
    private BigDecimal currentStopAfter;
    private BigDecimal tp1;
    private BigDecimal tp2;
    private Boolean tp1HitBefore;
    private Boolean tp1HitAfter;
    private Boolean tp2HitBefore;
    private Boolean tp2HitAfter;
    private Boolean trailingActiveBefore;
    private Boolean trailingActiveAfter;
    private Instant trailingActivatedAtBarCloseTime;
    private BigDecimal highestPriceSinceEntry;
    private BigDecimal lowestPriceSinceEntry;
    private BigDecimal remainingPositionPctBefore;
    private BigDecimal remainingPositionPctAfter;
    private BigDecimal closedPositionPct;
    private BigDecimal rawPnlPct;
    private BigDecimal netPnlPct;
    private BigDecimal leveragedNetPnlPct;
    private BigDecimal feePct;
    private BigDecimal slippagePct;
    private Integer leverage;
    private Integer barsInPosition;
    private MarketRegime marketRegime;
    private BigDecimal marketBreadthPct;
    private Integer longScore;
    private Integer shortScore;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
    private Instant decisionTime;
}
