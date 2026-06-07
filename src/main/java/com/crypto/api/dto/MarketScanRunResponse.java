package com.crypto.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketScanRunResponse(
        Long id,
        String scanTime,
        String scanType,
        String marketRegime,
        BigDecimal marketBreadthPct,
        Integer totalSymbols,
        Integer preFilterPassedCount,
        Integer strongLongCount,
        Integer strongShortCount,
        Integer watchlistCount,
        Integer eliminatedCount,
        String status,
        String errorMessage,
        List<String> reasons,
        List<String> warnings,
        String createdAt
) {
}
