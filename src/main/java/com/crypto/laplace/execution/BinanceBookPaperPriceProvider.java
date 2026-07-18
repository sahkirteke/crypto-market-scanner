package com.crypto.laplace.execution;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BinanceBookPaperPriceProvider implements LaplaceExecutionPriceProvider {
    private final BinanceFuturesClient client;

    @Override
    public Price quote(String symbol, MarketExecutionAction action) {
        BookTicker ticker = client.getAllBookTickers().stream()
                .filter(candidate -> symbol.equals(candidate.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE"));
        BigDecimal bid = requirePositive(ticker.getBidPrice());
        BigDecimal ask = requirePositive(ticker.getAskPrice());
        boolean usesAsk = action == MarketExecutionAction.LONG_OPEN
                || action == MarketExecutionAction.SHORT_CLOSE;
        return new Price(usesAsk ? ask : bid, bid, ask, usesAsk ? "ASK" : "BID", "BOOK_TICKER");
    }

    private BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE");
        }
        return value;
    }
}
