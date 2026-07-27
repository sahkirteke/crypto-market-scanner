package com.crypto.laplace.execution;

import java.math.BigDecimal;

public interface LaplaceExecutionPriceProvider {
    Price quote(String symbol, MarketExecutionAction action);
    default void beginCycle() {}
    default RequestMetrics requestMetrics() { return new RequestMetrics(0,0,0); }

    record RequestMetrics(int bookTickerRequestCount,int bookTickerFailureCount,int bulkBookTickerRequestCount) {}

    record Price(
            BigDecimal value,
            BigDecimal bestBid,
            BigDecimal bestAsk,
            String executionPriceType,
            String source) {
    }
}
