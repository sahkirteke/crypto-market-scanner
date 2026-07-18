package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BinanceBookPaperPriceProviderTest {
    @Test
    void marketActionsUseCounterpartyBookPriceAndNeverMid() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getAllBookTickers()).thenReturn(List.of(ticker("99", "101", "100")));
        BinanceBookPaperPriceProvider provider = new BinanceBookPaperPriceProvider(client);
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN).value()).isEqualByComparingTo("101");
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.LONG_CLOSE).value()).isEqualByComparingTo("99");
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.SHORT_OPEN).value()).isEqualByComparingTo("99");
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.SHORT_CLOSE).value()).isEqualByComparingTo("101");
    }

    @Test
    void invalidBookSideFailsClosed() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getAllBookTickers()).thenReturn(List.of(ticker("0", "101", "100")));
        assertThatThrownBy(() -> new BinanceBookPaperPriceProvider(client)
                .quote("BTCUSDT", MarketExecutionAction.LONG_OPEN))
                .hasMessage("EXECUTION_PRICE_UNAVAILABLE");
    }

    private BookTicker ticker(String bid, String ask, String mid) {
        return BookTicker.builder().symbol("BTCUSDT").bidPrice(new BigDecimal(bid))
                .askPrice(new BigDecimal(ask)).midPrice(new BigDecimal(mid)).build();
    }
}
