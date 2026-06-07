package com.crypto.analysis.dto;

import java.math.BigDecimal;

public record ClassificationPerformanceResponse(
        String classification,
        Integer totalTrades,
        Integer winCount,
        Integer lossCount,
        BigDecimal winRatePct,
        BigDecimal totalRealizedPnlUsdt,
        BigDecimal avgRealizedPnlPct
) {
}
