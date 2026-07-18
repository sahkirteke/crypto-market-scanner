package com.crypto.laplace.execution;

import java.math.BigDecimal;

public interface LaplaceExecutionPriceProvider {
    Price quote(String symbol, MarketExecutionAction action);

    record Price(
            BigDecimal value,
            BigDecimal bestBid,
            BigDecimal bestAsk,
            String executionPriceType,
            String source) {
    }
}
