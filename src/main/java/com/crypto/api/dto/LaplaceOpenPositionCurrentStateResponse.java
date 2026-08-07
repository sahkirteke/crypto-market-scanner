package com.crypto.api.dto;

import com.crypto.common.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;

public record LaplaceOpenPositionCurrentStateResponse(
        String positionId, String symbol, PositionSide side, Instant entryTime,
        BigDecimal entryPrice, BigDecimal currentPrice, BigDecimal priceMovePct,
        BigDecimal currentPnlUsdt) {}
