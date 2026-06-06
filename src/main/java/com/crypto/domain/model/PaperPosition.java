package com.crypto.domain.model;

import com.crypto.common.enums.ExitReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.PositionStatus;
import java.math.BigDecimal;
import java.time.Instant;
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
public class PaperPosition {
    private Long id;
    private String symbol;
    private PositionSide side;
    private PositionStatus status;
    private BigDecimal entryPrice;
    private BigDecimal entryPriceAdjusted;
    private Instant entryTimeUtc;
    private BigDecimal exitPrice;
    private BigDecimal exitPriceAdjusted;
    private Instant exitTimeUtc;
    private Long sourceScanRunId;
    private Long sourceCandidateId;
    private Integer entryScore;
    private Integer entryPriorityScore;
    private MarketRegime marketRegimeAtEntry;
    private MarketRegime marketRegimeAtExit;
    private BigDecimal marketBreadthPctAtEntry;
    private BigDecimal initialStop;
    private BigDecimal currentStop;
    private BigDecimal riskPerUnit;
    private BigDecimal tp1;
    private BigDecimal tp2;
    @Builder.Default
    private Boolean tp1Hit = false;
    @Builder.Default
    private Boolean tp2Hit = false;
    @Builder.Default
    private Boolean trailingActive = false;
    private Instant trailingActivatedAtBarCloseTime;
    private BigDecimal highestPriceSinceEntry;
    private BigDecimal lowestPriceSinceEntry;
    @Builder.Default
    private BigDecimal remainingPositionPct = BigDecimal.valueOf(100);
    @Builder.Default
    private BigDecimal rawRealizedPnlPct = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal netRealizedPnlPct = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal leveragedNetRealizedPnlPct = BigDecimal.ZERO;
    private BigDecimal rawUnrealizedPnlPct;
    private BigDecimal netUnrealizedPnlPct;
    private BigDecimal leveragedNetUnrealizedPnlPct;
    private BigDecimal totalFeePct;
    private BigDecimal totalSlippagePct;
    private Integer leverage;
    private BigDecimal maxFavorableMovePct;
    private BigDecimal maxAdverseMovePct;
    @Builder.Default
    private Integer barsInPosition = 0;
    @Builder.Default
    private ExitReason exitReason = ExitReason.NONE;
    private Instant createdAt;
    private Instant updatedAt;
}
