package com.crypto.api.dto;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplacePositionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record LaplaceOpenPaperPositionResponse(
        String positionId,
        String strategy,
        String strategyVersion,
        String symbol,
        PositionSide side,
        LaplacePositionStatus status,
        String rawEntrySignal,
        Boolean signalInverted,
        PositionSide effectiveExecutionSide,
        Instant signalCandleOpenTime,
        Instant signalCandleCloseTime,
        BigDecimal signalCandleClose,
        Instant entryTime,
        BigDecimal entryExecutionPrice,
        String entryExecutionPriceType,
        String executionAction,
        BigDecimal quantity,
        BigDecimal entryNotional,
        BigDecimal margin,
        Integer leverage,
        String orderType,
        BigDecimal takerFeeRate,
        BigDecimal entryFee,
        String reversalId,
        Instant createdAt,
        Instant updatedAt
) {
}
