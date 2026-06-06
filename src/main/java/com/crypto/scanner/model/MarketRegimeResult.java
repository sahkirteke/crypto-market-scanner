package com.crypto.scanner.model;

import com.crypto.common.enums.MarketRegime;
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
public class MarketRegimeResult {
    @Builder.Default
    private MarketRegime marketRegime = MarketRegime.CHOP;
    private BigDecimal marketBreadthPct;
    @Builder.Default
    private List<ReasonTag> reasonTags = new ArrayList<>();
    @Builder.Default
    private Boolean blockNewLong = false;
    @Builder.Default
    private Boolean blockNewShort = false;
    @Builder.Default
    private List<String> notes = new ArrayList<>();
}
