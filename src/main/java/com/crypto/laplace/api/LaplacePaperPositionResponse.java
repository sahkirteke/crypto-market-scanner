package com.crypto.laplace.api;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplacePositionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Stable API projection shared by both paper variants (never exposes a JPA entity). */
public record LaplacePaperPositionResponse(
        String positionId, String symbol, PositionSide side, LaplacePositionStatus status,
        Instant entryTime, BigDecimal entryPrice, BigDecimal margin, BigDecimal quantity,
        BigDecimal notional, int leverage, BigDecimal entryFee, Instant exitTime,
        BigDecimal exitPrice, BigDecimal exitFee, BigDecimal netPnl, String exitReason) {}
