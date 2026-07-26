package com.crypto.binance.client;

import com.crypto.binance.dto.BinanceBookTickerDto;
import com.crypto.binance.dto.BinanceExchangeInfoResponse;
import com.crypto.binance.dto.BinanceFilterDto;
import com.crypto.binance.dto.BinanceFundingRateDto;
import com.crypto.binance.dto.BinanceOpenInterestDto;
import com.crypto.binance.dto.BinanceSymbolDto;
import com.crypto.binance.dto.BinanceTicker24hDto;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class BinanceFuturesClient {
    private static final String PRICE_FILTER = "PRICE_FILTER";
    private static final String LOT_SIZE = "LOT_SIZE";
    private static final String MIN_NOTIONAL = "MIN_NOTIONAL";
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int CALCULATION_SCALE = 10;
    private static final int MAX_TRANSIENT_ATTEMPTS = 3;
    private static final long[] TRANSIENT_RETRY_BACKOFF_MILLIS = {300L, 700L};

    private final WebClient binanceWebClient;

    public BinanceFuturesClient(@Qualifier("binanceWebClient") WebClient binanceWebClient) {
        this.binanceWebClient = binanceWebClient;
    }

    public List<SymbolInfo> getExchangeInfo() {
        return execute("exchange info", () -> {
            BinanceExchangeInfoResponse response = binanceWebClient.get()
                    .uri("/fapi/v1/exchangeInfo")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::toClientException)
                    .bodyToMono(BinanceExchangeInfoResponse.class)
                    .block();

            List<SymbolInfo> symbolInfos;
            if (response == null || response.getSymbols() == null) {
                symbolInfos = Collections.emptyList();
            } else {
                symbolInfos = response.getSymbols().stream()
                        .filter(Objects::nonNull)
                        .map(this::mapSymbolInfo)
                        .toList();
            }
            log.info("BINANCE_EXCHANGE_INFO_READY count={}", symbolInfos.size());
            return symbolInfos;
        });
    }

    public List<Ticker24h> getAll24hTickers() {
        return execute("24h tickers", () -> {
            List<BinanceTicker24hDto> response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/24hr")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::toClientException)
                    .bodyToMono(new ParameterizedTypeReference<List<BinanceTicker24hDto>>() {
                    })
                    .block();

            List<Ticker24h> tickers = response == null ? Collections.emptyList() : response.stream()
                    .filter(Objects::nonNull)
                    .map(this::mapTicker24h)
                    .toList();
            log.info("BINANCE_24H_TICKERS_READY count={}", tickers.size());
            return tickers;
        });
    }

    public List<BookTicker> getAllBookTickers() {
        return execute("book tickers", () -> {
            List<BinanceBookTickerDto> response = binanceWebClient.get()
                    .uri("/fapi/v1/ticker/bookTicker")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::toClientException)
                    .bodyToMono(new ParameterizedTypeReference<List<BinanceBookTickerDto>>() {
                    })
                    .block();

            List<BookTicker> bookTickers = response == null ? Collections.emptyList() : response.stream()
                    .filter(Objects::nonNull)
                    .map(this::mapBookTicker)
                    .toList();
            log.info("BINANCE_BOOK_TICKERS_READY count={}", bookTickers.size());
            return bookTickers;
        });
    }

    public List<Kline> getKlines(String symbol, String interval, int limit) {
        return execute("klines", () -> {
            List<List<Object>> response = binanceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fapi/v1/klines")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::toClientException)
                    .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {
                    })
                    .block();

            List<Kline> klines = response == null ? Collections.emptyList() : response.stream()
                    .filter(Objects::nonNull)
                    .map(kline -> mapKline(symbol, interval, kline))
                    .toList();
            log.info("BINANCE_KLINES_READY symbol={} interval={} count={}", symbol, interval, klines.size());
            return klines;
        },
                exception -> log.warn("BINANCE_KLINES_TRANSIENT_ERROR symbol={} interval={} limit={} message={}",
                        symbol, interval, limit, rootMessage(exception)),
                exception -> log.error("BINANCE_KLINES_ERROR symbol={} interval={} limit={} message={}",
                        symbol, interval, limit, exception.getMessage(), exception),
                (exception, attempt) -> log.debug("BINANCE_KLINES_RETRY symbol={} interval={} attempt={} message={}",
                        symbol, interval, attempt, rootMessage(exception)));
    }

    public List<Kline> getKlines(String symbol, String interval, int limit, Instant startTime) {
        return execute("klines", () -> {
            List<List<Object>> response = binanceWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/fapi/v1/klines")
                            .queryParam("symbol", symbol).queryParam("interval", interval)
                            .queryParam("limit", limit).queryParam("startTime", startTime.toEpochMilli()).build())
                    .retrieve().onStatus(HttpStatusCode::isError, this::toClientException)
                    .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {}).block();
            return response == null ? Collections.emptyList() : response.stream().filter(Objects::nonNull)
                    .map(kline -> mapKline(symbol, interval, kline)).toList();
        });
    }

    public List<BinanceFundingRateDto> getFundingRate(String symbol) {
        return execute("funding rate for symbol " + symbol, () -> {
            List<BinanceFundingRateDto> response = binanceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fapi/v1/fundingRate")
                            .queryParam("symbol", symbol)
                            .queryParam("limit", 3)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::toClientException)
                    .bodyToMono(new ParameterizedTypeReference<List<BinanceFundingRateDto>>() {
                    })
                    .block();

            List<BinanceFundingRateDto> fundingRates = response == null ? Collections.emptyList() : response;
            log.info("BINANCE_FUNDING_RATE_READY symbol={} count={}", symbol, fundingRates.size());
            return fundingRates;
        });
    }

    public BinanceOpenInterestDto getOpenInterest(String symbol) {
        return execute("open interest for symbol " + symbol, () -> {
            BinanceOpenInterestDto openInterest = binanceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fapi/v1/openInterest")
                            .queryParam("symbol", symbol)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::toClientException)
                    .bodyToMono(BinanceOpenInterestDto.class)
                    .block();
            log.info("BINANCE_OPEN_INTEREST_READY symbol={} openInterest={}", symbol,
                    openInterest == null ? null : openInterest.getOpenInterest());
            return openInterest;
        });
    }

    private SymbolInfo mapSymbolInfo(BinanceSymbolDto dto) {
        BinanceFilterDto priceFilter = findFilter(dto, PRICE_FILTER);
        BinanceFilterDto lotSize = findFilter(dto, LOT_SIZE);
        BinanceFilterDto minNotional = findFilter(dto, MIN_NOTIONAL);

        return SymbolInfo.builder()
                .symbol(dto.getSymbol())
                .baseAsset(dto.getBaseAsset())
                .quoteAsset(dto.getQuoteAsset())
                .contractType(dto.getContractType())
                .status(dto.getStatus())
                .pricePrecision(dto.getPricePrecision())
                .quantityPrecision(dto.getQuantityPrecision())
                .tickSize(toBigDecimal(priceFilter == null ? null : priceFilter.getTickSize()))
                .stepSize(toBigDecimal(lotSize == null ? null : lotSize.getStepSize()))
                .minQty(toBigDecimal(lotSize == null ? null : lotSize.getMinQty()))
                .minNotional(toBigDecimal(getMinNotionalValue(minNotional)))
                .build();
    }

    private Ticker24h mapTicker24h(BinanceTicker24hDto dto) {
        return Ticker24h.builder()
                .symbol(dto.getSymbol())
                .lastPrice(toBigDecimal(dto.getLastPrice()))
                .priceChange(toBigDecimal(dto.getPriceChange()))
                .priceChangePercent(toBigDecimal(dto.getPriceChangePercent()))
                .quoteVolume(toBigDecimal(dto.getQuoteVolume()))
                .volume(toBigDecimal(dto.getVolume()))
                .highPrice(toBigDecimal(dto.getHighPrice()))
                .lowPrice(toBigDecimal(dto.getLowPrice()))
                .closeTime(toInstant(dto.getCloseTime()))
                .build();
    }

    private BookTicker mapBookTicker(BinanceBookTickerDto dto) {
        BigDecimal bidPrice = toBigDecimal(dto.getBidPrice());
        BigDecimal askPrice = toBigDecimal(dto.getAskPrice());

        return BookTicker.builder()
                .symbol(dto.getSymbol())
                .bidPrice(bidPrice)
                .bidQty(toBigDecimal(dto.getBidQty()))
                .askPrice(askPrice)
                .askQty(toBigDecimal(dto.getAskQty()))
                .midPrice(calculateMidPrice(bidPrice, askPrice))
                .spreadPct(calculateSpreadPct(bidPrice, askPrice))
                .eventTime(toInstant(dto.getTime()))
                .build();
    }

    private Kline mapKline(String symbol, String interval, List<Object> values) {
        Instant closeTime = toInstant(asLong(get(values, 6)));

        return Kline.builder()
                .symbol(symbol)
                .interval(interval)
                .openTime(toInstant(asLong(get(values, 0))))
                .open(toBigDecimal(asString(get(values, 1))))
                .high(toBigDecimal(asString(get(values, 2))))
                .low(toBigDecimal(asString(get(values, 3))))
                .close(toBigDecimal(asString(get(values, 4))))
                .volume(toBigDecimal(asString(get(values, 5))))
                .closeTime(closeTime)
                .quoteAssetVolume(toBigDecimal(asString(get(values, 7))))
                .numberOfTrades(asLong(get(values, 8)))
                .takerBuyBaseVolume(toBigDecimal(asString(get(values, 9))))
                .takerBuyQuoteVolume(toBigDecimal(asString(get(values, 10))))
                .closed(closeTime != null && !closeTime.isAfter(Instant.now()))
                .build();
    }

    private BinanceFilterDto findFilter(BinanceSymbolDto dto, String filterType) {
        if (dto.getFilters() == null) {
            return null;
        }

        return dto.getFilters().stream()
                .filter(Objects::nonNull)
                .filter(filter -> filterType.equals(filter.getFilterType()))
                .findFirst()
                .orElse(null);
    }

    private String getMinNotionalValue(BinanceFilterDto minNotional) {
        if (minNotional == null) {
            return null;
        }
        return minNotional.getNotional() != null ? minNotional.getNotional() : minNotional.getMinNotional();
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private Instant toInstant(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    private BigDecimal calculateMidPrice(BigDecimal bid, BigDecimal ask) {
        if (bid == null || ask == null) {
            return null;
        }
        return bid.add(ask).divide(TWO, CALCULATION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private BigDecimal calculateSpreadPct(BigDecimal bid, BigDecimal ask) {
        BigDecimal midPrice = calculateMidPrice(bid, ask);
        if (bid == null || ask == null || midPrice == null || BigDecimal.ZERO.compareTo(midPrice) == 0) {
            return null;
        }
        return ask.subtract(bid)
                .multiply(ONE_HUNDRED)
                .divide(midPrice, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private Object get(List<Object> values, int index) {
        return values.size() > index ? values.get(index) : null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Mono<? extends Throwable> toClientException(org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new BinanceClientException("Binance API request failed with status "
                        + response.statusCode().value() + ": " + body));
    }

    private <T> T execute(String operation, BinanceOperation<T> operationCallback) {
        return execute(operation,
                operationCallback,
                exception -> log.warn("BINANCE_HTTP_TRANSIENT_ERROR operation={} message={}", operation, rootMessage(exception)),
                exception -> log.error("BINANCE_HTTP_ERROR operation={} message={}", operation, exception.getMessage(), exception),
                (exception, attempt) -> log.debug("BINANCE_HTTP_RETRY operation={} attempt={} message={}",
                        operation, attempt, rootMessage(exception)));
    }

    private <T> T execute(String operation, BinanceOperation<T> operationCallback, ErrorLogger transientLogger,
            ErrorLogger errorLogger, RetryLogger retryLogger) {
        int attempt = 1;
        while (true) {
            try {
                return operationCallback.execute();
            } catch (Exception exception) {
                Throwable unwrapped = Exceptions.unwrap(exception);
                Throwable logException = unwrapped == null ? exception : unwrapped;
                boolean transientNetworkError = isTransientNetworkError(exception) || isTransientNetworkError(logException);
                if (transientNetworkError && attempt < MAX_TRANSIENT_ATTEMPTS) {
                    int nextAttempt = attempt + 1;
                    retryLogger.log(logException, nextAttempt);
                    sleepBeforeRetry(attempt);
                    attempt = nextAttempt;
                    continue;
                }
                if (transientNetworkError) {
                    transientLogger.log(logException);
                } else {
                    errorLogger.log(logException);
                }
                if (exception instanceof BinanceClientException binanceClientException) {
                    throw binanceClientException;
                }
                throw new BinanceClientException("Binance client error while fetching " + operation, logException);
            }
        }
    }

    private void sleepBeforeRetry(int failedAttempt) {
        int backoffIndex = Math.min(failedAttempt - 1, TRANSIENT_RETRY_BACKOFF_MILLIS.length - 1);
        try {
            Thread.sleep(TRANSIENT_RETRY_BACKOFF_MILLIS[backoffIndex]);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new BinanceClientException("Interrupted while retrying Binance request", interruptedException);
        }
    }

    private boolean isTransientNetworkError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketException
                    || current instanceof WebClientRequestException
                    || current instanceof TimeoutException
                    || "reactor.netty.http.client.PrematureCloseException".equals(current.getClass().getName())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("connection reset")
                        || lower.contains("connection prematurely closed")
                        || lower.contains("prematurely closed")
                        || lower.contains("read timed out")
                        || lower.contains("connection timed out")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        String message = root == null ? null : root.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable == null ? null : throwable.getMessage();
        }
        return message == null || message.isBlank() ? "unknown" : message;
    }

    @FunctionalInterface
    private interface BinanceOperation<T> {
        T execute();
    }

    @FunctionalInterface
    private interface ErrorLogger {
        void log(Throwable exception);
    }

    @FunctionalInterface
    private interface RetryLogger {
        void log(Throwable exception, int attempt);
    }
}
