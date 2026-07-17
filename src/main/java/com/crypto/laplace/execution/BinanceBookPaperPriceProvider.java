package com.crypto.laplace.execution;
import com.crypto.binance.client.BinanceFuturesClient;import com.crypto.domain.model.BookTicker;import java.math.BigDecimal;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class BinanceBookPaperPriceProvider implements LaplaceExecutionPriceProvider {
 private final BinanceFuturesClient client;
 public Price quote(String symbol){BookTicker ticker=client.getAllBookTickers().stream().filter(x->symbol.equals(x.getSymbol())).findFirst().orElseThrow(()->new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE"));BigDecimal p=ticker.getMidPrice();if(p==null||p.signum()<=0)throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE");return new Price(p,"BINANCE_FUTURES_BOOK_MID");}
}
