package com.crypto.analysis.dto;

import java.math.BigDecimal;

public record PerformanceBreakdownResponse(
        String group,
        Integer totalTrades,
        Integer winCount,
        Integer lossCount,
        BigDecimal winRatePct,
        BigDecimal totalRealizedPnlUsdt,
        BigDecimal avgRealizedPnlPct,
        BigDecimal avgMaxFavorableMovePct,
        BigDecimal avgMaxAdverseMovePct
) {
}
