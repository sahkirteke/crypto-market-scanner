package com.crypto.api.dto;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EntryCandidateResponse(
        String symbol,
        PositionSide side,
        Integer score,
        Integer longScore,
        Integer shortScore,
        CoinClassification sourceClassification,
        DirectionBias directionBias,
        RiskLevel riskLevel,
        BigDecimal lastPrice,
        BigDecimal priceChange24hPct,
        BigDecimal quoteVolume24h,
        BigDecimal spreadPct,
        BigDecimal fundingRate,
        BigDecimal openInterest,
        BigDecimal marketBreadthPct,
        List<ReasonTag> reasons,
        List<ReasonTag> warnings,
        String candidateReason,
        Instant createdAt
) {
}
