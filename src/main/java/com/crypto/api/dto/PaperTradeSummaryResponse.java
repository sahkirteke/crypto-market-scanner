package com.crypto.api.dto;

import java.math.BigDecimal;

public record PaperTradeSummaryResponse(
        long openCount,
        long closedCount,
        long openLongCount,
        long openShortCount,
        BigDecimal totalRealizedPnlUsdt,
        Long totalWinCount,
        Long totalLossCount,
        BigDecimal winRatePct,
        BigDecimal avgRealizedPnlPct
) {
}
