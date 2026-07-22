package com.crypto.api.dto;

import com.crypto.common.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;

public record LaplaceTradePnlResponse(
        String symbol,
        Instant entryTime,
        BigDecimal entryPrice,
        PositionSide effectiveExecutionSide,
        BigDecimal pnlAmount,
        String exitReason
) {
}
