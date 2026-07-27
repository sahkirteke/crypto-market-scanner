package com.crypto.binance.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.SymbolInfo;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class BinanceFuturesClientTest {


    @Test
    void classifiesNestedConnectionResetAsTransientNetworkError() throws Exception {
        BinanceFuturesClient client = clientWithJson("[]");
        Method method = BinanceFuturesClient.class.getDeclaredMethod("isTransientNetworkError", Throwable.class);
        method.setAccessible(true);

        boolean transientNetworkError = (boolean) method.invoke(client,
                new RuntimeException("wrapper", new SocketException("Connection reset")));

        assertThat(transientNetworkError).isTrue();
    }

    @Test
    void getKlinesRetriesTransientConnectionResetBeforeFailing() {
        AtomicInteger attempts = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            attempts.incrementAndGet();
            return Mono.error(new SocketException("Connection reset"));
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(exchangeFunction)
                .build();
        BinanceFuturesClient client = new BinanceFuturesClient(webClient);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getKlines("BTCUSDT", "1h", 1))
                .isInstanceOf(BinanceClientException.class)
                .hasMessageContaining("klines");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void getAllBookTickersMapsPricesAndCalculatesMidPriceAndSpreadPct() {
        BinanceFuturesClient client = clientWithJson("""
                [
                  {
                    "symbol": "BTCUSDT",
                    "bidPrice": "100",
                    "bidQty": "1.25",
                    "askPrice": "101",
                    "askQty": "2.50",
                    "time": 1710000000000
                  }
                ]
                """);

        List<BookTicker> tickers = client.getAllBookTickers();

        assertThat(tickers).hasSize(1);
        BookTicker ticker = tickers.getFirst();
        assertThat(ticker.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(ticker.getBidPrice()).isEqualByComparingTo("100");
        assertThat(ticker.getAskPrice()).isEqualByComparingTo("101");
        assertThat(ticker.getBidQty()).isEqualByComparingTo("1.25");
        assertThat(ticker.getAskQty()).isEqualByComparingTo("2.50");
        assertThat(ticker.getMidPrice()).isEqualByComparingTo("100.5");
        assertThat(ticker.getSpreadPct()).isEqualByComparingTo("0.9950248756");
        assertThat(ticker.getEventTime()).isEqualTo(Instant.ofEpochMilli(1710000000000L));
    }

    @Test
    void getBookTickerRequestsOnlyTheRequestedSymbol() {
        java.util.concurrent.atomic.AtomicReference<String> uri=new java.util.concurrent.atomic.AtomicReference<>();
        ExchangeFunction exchange=request->{uri.set(request.url().toString());return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type",MediaType.APPLICATION_JSON_VALUE).body("{\"symbol\":\"BTCUSDT\",\"bidPrice\":\"99\",\"askPrice\":\"100\"}").build());};
        BinanceFuturesClient client=new BinanceFuturesClient(WebClient.builder().baseUrl("http://localhost").exchangeFunction(exchange).build());
        BookTicker ticker=client.getBookTicker("BTCUSDT");
        assertThat(uri.get()).contains("/fapi/v1/ticker/bookTicker?symbol=BTCUSDT");
        assertThat(ticker.getBidPrice()).isEqualByComparingTo("99");
        assertThat(ticker.getAskPrice()).isEqualByComparingTo("100");
    }

    @Test
    void getExchangeInfoMapsSymbolFilters() {
        BinanceFuturesClient client = clientWithJson("""
                {
                  "symbols": [
                    {
                      "symbol": "ETHUSDT",
                      "pair": "ETHUSDT",
                      "contractType": "PERPETUAL",
                      "status": "TRADING",
                      "baseAsset": "ETH",
                      "quoteAsset": "USDT",
                      "pricePrecision": 2,
                      "quantityPrecision": 3,
                      "filters": [
                        {"filterType": "PRICE_FILTER", "tickSize": "0.01"},
                        {"filterType": "LOT_SIZE", "stepSize": "0.001", "minQty": "0.001"},
                        {"filterType": "MIN_NOTIONAL", "notional": "5"}
                      ]
                    }
                  ]
                }
                """);

        List<SymbolInfo> symbols = client.getExchangeInfo();

        assertThat(symbols).hasSize(1);
        SymbolInfo symbol = symbols.getFirst();
        assertThat(symbol.getSymbol()).isEqualTo("ETHUSDT");
        assertThat(symbol.getBaseAsset()).isEqualTo("ETH");
        assertThat(symbol.getQuoteAsset()).isEqualTo("USDT");
        assertThat(symbol.getContractType()).isEqualTo("PERPETUAL");
        assertThat(symbol.getStatus()).isEqualTo("TRADING");
        assertThat(symbol.getPricePrecision()).isEqualTo(2);
        assertThat(symbol.getQuantityPrecision()).isEqualTo(3);
        assertThat(symbol.getTickSize()).isEqualByComparingTo("0.01");
        assertThat(symbol.getStepSize()).isEqualByComparingTo("0.001");
        assertThat(symbol.getMinQty()).isEqualByComparingTo("0.001");
        assertThat(symbol.getMinNotional()).isEqualByComparingTo("5");
    }

    @Test
    void getKlinesMapsArrayResponseToKlineModel() {
        long openTime = 1710000000000L;
        long closeTime = Instant.now().minusSeconds(60).toEpochMilli();
        BinanceFuturesClient client = clientWithJson("""
                [
                  [
                    %d,
                    "10.1",
                    "11.2",
                    "9.9",
                    "10.8",
                    "123.45",
                    %d,
                    "456.78",
                    99,
                    "12.34",
                    "56.78",
                    "0"
                  ]
                ]
                """.formatted(openTime, closeTime));

        List<Kline> klines = client.getKlines("BNBUSDT", "1h", 1);

        assertThat(klines).hasSize(1);
        Kline kline = klines.getFirst();
        assertThat(kline.getSymbol()).isEqualTo("BNBUSDT");
        assertThat(kline.getInterval()).isEqualTo("1h");
        assertThat(kline.getOpenTime()).isEqualTo(Instant.ofEpochMilli(openTime));
        assertThat(kline.getOpen()).isEqualByComparingTo("10.1");
        assertThat(kline.getHigh()).isEqualByComparingTo("11.2");
        assertThat(kline.getLow()).isEqualByComparingTo("9.9");
        assertThat(kline.getClose()).isEqualByComparingTo("10.8");
        assertThat(kline.getVolume()).isEqualByComparingTo("123.45");
        assertThat(kline.getCloseTime()).isEqualTo(Instant.ofEpochMilli(closeTime));
        assertThat(kline.getQuoteAssetVolume()).isEqualByComparingTo("456.78");
        assertThat(kline.getNumberOfTrades()).isEqualTo(99L);
        assertThat(kline.getTakerBuyBaseVolume()).isEqualByComparingTo("12.34");
        assertThat(kline.getTakerBuyQuoteVolume()).isEqualByComparingTo("56.78");
        assertThat(kline.getClosed()).isTrue();
    }

    private BinanceFuturesClient clientWithJson(String json) {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build());
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(exchangeFunction)
                .build();
        return new BinanceFuturesClient(webClient);
    }
}
