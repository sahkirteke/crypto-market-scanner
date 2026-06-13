package com.crypto.paper.model;

import java.math.BigDecimal;

public record V20PnlResult(
        BigDecimal rawPnlPct,
        BigDecimal entryPriceAdjusted,
        BigDecimal exitPriceAdjusted,
        BigDecimal unleveragedNotionalUsdt,
        BigDecimal leveragedNotionalUsdt,
        BigDecimal unleveragedQuantity,
        BigDecimal leveragedQuantity,
        BigDecimal unleveragedRawPnlUsdt,
        BigDecimal unleveragedEntryFeeUsdt,
        BigDecimal unleveragedExitFeeUsdt,
        BigDecimal unleveragedTotalFeeUsdt,
        BigDecimal unleveragedNetPnlUsdt,
        BigDecimal unleveragedNetPnlPct,
        BigDecimal leveragedRawPnlUsdt,
        BigDecimal leveragedEntryFeeUsdt,
        BigDecimal leveragedExitFeeUsdt,
        BigDecimal leveragedTotalFeeUsdt,
        BigDecimal leveragedNetPnlUsdt,
        BigDecimal leveragedNetPnlPct,
        BigDecimal feeRate,
        String feeMode,
        BigDecimal slippagePct
) {}
