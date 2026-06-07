package com.crypto.analysis.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StrategyPerformanceSummaryResponse(
        Integer totalTrades,
        Integer closedTrades,
        Integer openTrades,
        Integer winCount,
        Integer lossCount,
        Integer flatCount,
        BigDecimal winRatePct,
        BigDecimal totalRealizedPnlUsdt,
        BigDecimal avgRealizedPnlPct,
        BigDecimal avgWinPct,
        BigDecimal avgLossPct,
        BigDecimal bestTradePct,
        BigDecimal worstTradePct,
        BigDecimal avgMaxFavorableMovePct,
        BigDecimal avgMaxAdverseMovePct,
        BigDecimal avgMinutesHeld,
        BigDecimal avgBarsHeld,
        Instant firstTradeTime,
        Instant lastTradeTime
) {
}
