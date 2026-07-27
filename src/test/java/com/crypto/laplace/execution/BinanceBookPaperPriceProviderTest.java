package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BinanceBookPaperPriceProviderTest {
    @Test
    void marketActionsUseCounterpartyBookPriceAndNeverMid() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getBookTicker("BTCUSDT")).thenReturn(ticker("99", "101", "100"));
        BinanceBookPaperPriceProvider provider = new BinanceBookPaperPriceProvider(client);
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN).value()).isEqualByComparingTo("101");
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.LONG_CLOSE).value()).isEqualByComparingTo("99");
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.SHORT_OPEN).value()).isEqualByComparingTo("99");
        assertThat(provider.quote("BTCUSDT", MarketExecutionAction.SHORT_CLOSE).value()).isEqualByComparingTo("101");
        verify(client,times(4)).getBookTicker("BTCUSDT");
        verify(client,never()).getAllBookTickers();
    }

    @Test
    void invalidBookSideFailsClosed() {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getBookTicker("BTCUSDT")).thenReturn(ticker("0", "101", "100"));
        assertThatThrownBy(() -> new BinanceBookPaperPriceProvider(client)
                .quote("BTCUSDT", MarketExecutionAction.LONG_OPEN))
                .hasMessage("BOOK_TICKER_UNAVAILABLE");
    }

    @Test void multipleQuotesNeverUseBulkEndpoint() {
        BinanceFuturesClient client=mock(BinanceFuturesClient.class);
        when(client.getBookTicker(anyString())).thenAnswer(call->BookTicker.builder().symbol(call.getArgument(0)).bidPrice(new BigDecimal("99")).askPrice(new BigDecimal("100")).build());
        BinanceBookPaperPriceProvider provider=new BinanceBookPaperPriceProvider(client);provider.beginCycle();
        for(int i=0;i<37;i++)provider.quote("COIN"+i+"USDT",MarketExecutionAction.LONG_OPEN);
        verify(client,times(37)).getBookTicker(anyString());verify(client,never()).getAllBookTickers();
        assertThat(provider.requestMetrics().bookTickerRequestCount()).isEqualTo(37);
        assertThat(provider.requestMetrics().bookTickerFailureCount()).isZero();
        assertThat(provider.requestMetrics().bulkBookTickerRequestCount()).isZero();
    }

    private BookTicker ticker(String bid, String ask, String mid) {
        return BookTicker.builder().symbol("BTCUSDT").bidPrice(new BigDecimal(bid))
                .askPrice(new BigDecimal(ask)).midPrice(new BigDecimal(mid)).build();
    }
}
