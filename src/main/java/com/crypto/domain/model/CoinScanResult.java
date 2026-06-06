package com.crypto.domain.model;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
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
public class CoinScanResult {
    private Long scanRunId;
    private String symbol;
    private DirectionBias directionBias;
    private CoinClassification classification;
    private Integer score;
    private Integer longScore;
    private Integer shortScore;
    private RiskLevel riskLevel;
    private BigDecimal lastPrice;
    private BigDecimal priceChange24hPct;
    private BigDecimal quoteVolume24h;
    private BigDecimal spreadPct;
    private BigDecimal fundingRate;
    private BigDecimal openInterest;
    private BigDecimal marketBreadthPct;
    private EliminationReason eliminatedReason;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
    private Instant scanTime;
}
