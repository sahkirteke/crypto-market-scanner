package com.crypto.binance.runner;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.binance.dto.BinanceFundingRateDto;
import com.crypto.binance.dto.BinanceOpenInterestDto;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-binance")
@RequiredArgsConstructor
public class BinanceManualTestRunner implements CommandLineRunner {
    private static final String BTC_USDT = "BTCUSDT";
    private static final String ONE_HOUR_INTERVAL = "1h";
    private static final int KLINE_LIMIT = 250;

    private final BinanceFuturesClient binanceFuturesClient;

    @Override
    public void run(String... args) {
        try {
            List<SymbolInfo> exchangeInfo = binanceFuturesClient.getExchangeInfo();
            log.info("MANUAL_BINANCE_CHECK exchangeInfoCount={}", exchangeInfo.size());

            List<Ticker24h> tickers24h = binanceFuturesClient.getAll24hTickers();
            log.info("MANUAL_BINANCE_CHECK ticker24hCount={}", tickers24h.size());

            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            log.info("MANUAL_BINANCE_CHECK bookTickerCount={}", bookTickers.size());

            List<Kline> btcKlines = binanceFuturesClient.getKlines(BTC_USDT, ONE_HOUR_INTERVAL, KLINE_LIMIT);
            log.info("MANUAL_BINANCE_CHECK btcKline1hCount={}", btcKlines.size());
            log.info("MANUAL_BINANCE_CHECK btcLatestClose={}", getLatestClose(btcKlines));

            List<BinanceFundingRateDto> fundingRates = binanceFuturesClient.getFundingRate(BTC_USDT);
            log.info("MANUAL_BINANCE_CHECK fundingRateCount={}", fundingRates.size());

            BinanceOpenInterestDto openInterest = binanceFuturesClient.getOpenInterest(BTC_USDT);
            log.info("MANUAL_BINANCE_CHECK openInterest={}",
                    openInterest == null ? null : openInterest.getOpenInterest());
        } catch (Exception exception) {
            log.error("MANUAL_BINANCE_CHECK_FAILED message={}", exception.getMessage(), exception);
        }
    }

    private BigDecimal getLatestClose(List<Kline> klines) {
        if (klines == null || klines.isEmpty()) {
            return null;
        }
        return klines.get(klines.size() - 1).getClose();
    }
}
