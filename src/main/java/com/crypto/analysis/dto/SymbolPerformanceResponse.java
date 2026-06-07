package com.crypto.analysis.dto;

import java.math.BigDecimal;

public record SymbolPerformanceResponse(
        String symbol,
        Integer totalTrades,
        Integer winCount,
        Integer lossCount,
        Integer flatCount,
        BigDecimal winRatePct,
        BigDecimal totalRealizedPnlUsdt,
        BigDecimal avgRealizedPnlPct,
        BigDecimal bestTradePct,
        BigDecimal worstTradePct,
        BigDecimal avgMaxFavorableMovePct,
        BigDecimal avgMaxAdverseMovePct
) {
}
