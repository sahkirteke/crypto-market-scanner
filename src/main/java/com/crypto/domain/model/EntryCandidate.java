package com.crypto.domain.model;

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
public class EntryCandidate {
    private Long id;
    private Long scanRunId;
    private String symbol;
    private PositionSide side;
    private Integer score;
    private Integer entryPriorityScore;
    private RiskLevel riskLevel;
    private MarketRegime marketRegime;
    private BigDecimal marketBreadthPct;
    private Instant validFromUtc;
    private Instant validUntilUtc;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
}
