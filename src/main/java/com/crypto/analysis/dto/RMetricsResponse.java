package com.crypto.analysis.dto;

import java.math.BigDecimal;

public record RMetricsResponse(
        BigDecimal avgMfeInR,
        BigDecimal avgMaeInR,
        Integer reached0_8RBeforeStop,
        Integer reached1RBeforeStop,
        Integer stopAfterPositiveMoveCount
) {
}
