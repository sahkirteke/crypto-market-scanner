package com.crypto.laplace.api;

import com.crypto.laplace.model.LaplaceRuntimeState;
import java.math.BigDecimal;
import java.time.Instant;

public record LaplaceVariantAnalysisSummaryResponse(
        String paperVariant, boolean signalInverted,
        String strategy, String strategyVersion, long tradeCount, long openPositionCount,
        long closedPositionCount, long longTradeCount, long shortTradeCount, long winCount,
        long lossCount, long breakEvenCount, BigDecimal winRate, BigDecimal grossPnl,
        BigDecimal totalEntryFee, BigDecimal totalExitFee, BigDecimal totalFee, BigDecimal netPnl,
        BigDecimal averageGrossPnl, BigDecimal averageNetPnl, BigDecimal averageNetPnlPct,
        BigDecimal bestTradePnl, BigDecimal worstTradePnl, BigDecimal longNetPnl,
        BigDecimal shortNetPnl, BigDecimal longWinRate, BigDecimal shortWinRate,
        Instant firstTradeTime, Instant lastTradeTime, Instant lastUpdatedAt,
        BigDecimal currentBalanceUsdt, BigDecimal marginPerPositionUsdt,
        boolean profitLockDown, LaplaceRuntimeState runtimeState, Instant reopenAt) {
}
