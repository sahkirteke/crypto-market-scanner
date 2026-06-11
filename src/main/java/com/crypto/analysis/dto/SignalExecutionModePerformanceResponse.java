package com.crypto.analysis.dto;

import java.math.BigDecimal;

public record SignalExecutionModePerformanceResponse(
        String signalExecutionMode,
        Integer totalTrades,
        Integer winCount,
        Integer lossCount,
        Integer flatCount,
        BigDecimal winRatePct,
        BigDecimal totalRealizedPnlUsdt,
        BigDecimal avgRealizedPnlPct,
        BigDecimal avgMaxFavorableMovePct,
        BigDecimal avgMaxAdverseMovePct,
        BigDecimal avgMinutesHeld,
        BigDecimal avgBarsHeld,
        BigDecimal bestTradePct,
        BigDecimal worstTradePct
) {
}
