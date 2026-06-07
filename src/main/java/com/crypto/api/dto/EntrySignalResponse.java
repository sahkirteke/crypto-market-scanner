package com.crypto.api.dto;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import java.math.BigDecimal;
import java.util.List;

public record EntrySignalResponse(
        String symbol,
        PositionSide side,
        EntryAction action,
        Integer score,
        Integer longScore,
        Integer shortScore,
        CoinClassification sourceClassification,
        DirectionBias directionBias,
        RiskLevel riskLevel,
        BigDecimal entryPrice,
        BigDecimal lastPrice,
        BigDecimal spreadPct,
        BigDecimal priceChange24hPct,
        BigDecimal quoteVolume24h,
        BigDecimal fundingRate,
        BigDecimal openInterest,
        BigDecimal marketBreadthPct,
        List<ReasonTag> reasons,
        List<ReasonTag> warnings,
        String signalReason,
        String blockReason,
        String signalTime,
        String entryTrigger,
        BigDecimal close1h,
        BigDecimal previousClose1h,
        BigDecimal previous1hHigh,
        BigDecimal previous1hLow,
        BigDecimal ema20_1h,
        BigDecimal rsi14_1h,
        BigDecimal previousRsi14_1h,
        BigDecimal macdHist_1h,
        BigDecimal previousMacdHist_1h,
        BigDecimal volumeRatio_1h
) {
}
