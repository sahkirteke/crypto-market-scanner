package com.crypto.analysis.dto;

import java.math.BigDecimal;

public record ExitReasonPerformanceResponse(
        String exitReason,
        Integer totalTrades,
        Integer winCount,
        Integer lossCount,
        BigDecimal winRatePct,
        BigDecimal totalRealizedPnlUsdt,
        BigDecimal avgRealizedPnlPct
) {
}
