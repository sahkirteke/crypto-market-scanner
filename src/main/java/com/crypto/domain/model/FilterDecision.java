package com.crypto.domain.model;

import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.ReasonTag;
import java.math.BigDecimal;
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
public class FilterDecision {
    private String symbol;
    @Builder.Default
    private Boolean passed = false;
    @Builder.Default
    private EliminationReason eliminatedReason = EliminationReason.NONE;
    private BigDecimal quoteVolume24h;
    private BigDecimal spreadPct;
    private BigDecimal priceChange24hPct;
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
}
