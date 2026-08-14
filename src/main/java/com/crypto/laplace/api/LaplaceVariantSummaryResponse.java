package com.crypto.laplace.api;

import java.math.BigDecimal;

public record LaplaceVariantSummaryResponse(
        String paperVariant, long openPositionCount, long closedPositionCount,
        BigDecimal closedPositionsNetPnlUsdt, BigDecimal openPositionsCurrentPnlUsdt,
        BigDecimal totalPnlUsdt) {}
