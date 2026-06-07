package com.crypto.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CoinScanResultResponse(
        Long id,
        Long scanRunId,
        String symbol,
        String directionBias,
        String classification,
        Integer score,
        Integer longScore,
        Integer shortScore,
        String riskLevel,
        BigDecimal lastPrice,
        BigDecimal priceChange24hPct,
        BigDecimal quoteVolume24h,
        BigDecimal spreadPct,
        BigDecimal fundingRate,
        BigDecimal openInterest,
        BigDecimal marketBreadthPct,
        List<String> reasons,
        List<String> warnings,
        String eliminatedReason,
        Instant createdAt
) {
}
