package com.crypto.laplace.execution;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class BinanceBookPaperPriceProvider implements LaplaceExecutionPriceProvider {
    private final BinanceFuturesClient client;
    private final AtomicInteger requests=new AtomicInteger(),failures=new AtomicInteger();

    @Override
    public Price quote(String symbol, MarketExecutionAction action) {
        requests.incrementAndGet();
        BookTicker ticker;
        try { ticker=client.getBookTicker(symbol); }
        catch(RuntimeException error){failures.incrementAndGet();throw new IllegalStateException("BOOK_TICKER_UNAVAILABLE",error);}
        if(ticker==null){failures.incrementAndGet();throw new IllegalStateException("BOOK_TICKER_UNAVAILABLE");}
        BigDecimal bid;
        BigDecimal ask;
        try { bid=requirePositive(ticker.getBidPrice());ask=requirePositive(ticker.getAskPrice()); }
        catch(RuntimeException invalid){failures.incrementAndGet();throw new IllegalStateException("BOOK_TICKER_UNAVAILABLE",invalid);}
        boolean usesAsk = action == MarketExecutionAction.LONG_OPEN
                || action == MarketExecutionAction.SHORT_CLOSE;
        return new Price(usesAsk ? ask : bid, bid, ask, usesAsk ? "ASK" : "BID", "BOOK_TICKER");
    }

    @Override public void beginCycle(){requests.set(0);failures.set(0);}
    @Override public RequestMetrics requestMetrics(){return new RequestMetrics(requests.get(),failures.get(),0);}

    private BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE");
        }
        return value;
    }
}
