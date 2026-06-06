package com.crypto.domain.model;

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
public class FuturesSnapshot {
    private String symbol;
    private BigDecimal fundingRate;
    private BigDecimal openInterest;
    private Boolean longCrowded;
    private Boolean shortCrowded;
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
    private Instant snapshotTime;
}
