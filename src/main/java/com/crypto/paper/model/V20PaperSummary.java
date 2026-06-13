package com.crypto.paper.model;

import java.math.BigDecimal;

public record V20PaperSummary(
        String side,
        int tradeCount,
        int winCount,
        int lossCount,
        BigDecimal winRate,
        int takeProfitCount,
        int stopLossCount,
        BigDecimal unleveragedTotalFeeUsdt,
        BigDecimal unleveragedNetPnlUsdt,
        BigDecimal unleveragedAvgNetPnlUsdt,
        BigDecimal unleveragedNetPnlPctOnMargin,
        BigDecimal leveragedTotalFeeUsdt,
        BigDecimal leveragedNetPnlUsdt,
        BigDecimal leveragedAvgNetPnlUsdt,
        BigDecimal leveragedNetPnlPctOnMargin
) {}
