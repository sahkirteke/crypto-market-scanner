package com.crypto.scanner.runner;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.domain.model.KlineLoadResult;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.TechnicalSnapshotPair;
import com.crypto.domain.model.Ticker24h;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.scanner.model.CoinScoringInput;
import com.crypto.scanner.model.MarketRegimeResult;
import com.crypto.scanner.service.CoinScoringService;
import com.crypto.scanner.service.FuturesDataService;
import com.crypto.scanner.service.IndicatorService;
import com.crypto.scanner.service.KlineService;
import com.crypto.scanner.service.MarketBreadthService;
import com.crypto.scanner.service.MarketRegimeService;
import com.crypto.scanner.service.PreFilterService;
import com.crypto.scanner.service.SymbolUniverseService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-scoring")
@RequiredArgsConstructor
public class CoinScoringManualRunner implements CommandLineRunner {
    private static final int MANUAL_SYMBOL_LIMIT = 10;
    private static final String BTCUSDT = "BTCUSDT";
    private static final String ETHUSDT = "ETHUSDT";

    private final SymbolUniverseService symbolUniverseService;
    private final BinanceFuturesClient binanceFuturesClient;
    private final PreFilterService preFilterService;
    private final KlineService klineService;
    private final IndicatorService indicatorService;
    private final MarketBreadthService marketBreadthService;
    private final MarketRegimeService marketRegimeService;
    private final FuturesDataService futuresDataService;
    private final CoinScoringService coinScoringService;

    @Override
    public void run(String... args) {
        try {
            List<SymbolInfo> symbols = symbolUniverseService.loadTradableSymbols();
            List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            PreFilterResult preFilterResult = preFilterService.apply(symbols, tickers, bookTickers);
            List<SymbolInfo> selectedSymbols = selectManualSymbols(symbols, preFilterResult.getPassedSymbols());

            log.info("MANUAL_SCORING_CHECK inputCount={}", selectedSymbols.size());

            KlineLoadResult klineLoadResult = klineService.loadForSymbols(selectedSymbols);
            List<TechnicalSnapshotPair> pairs = klineLoadResult.getReadyBundles().stream()
                    .map(indicatorService::calculatePair)
                    .filter(pair -> Boolean.TRUE.equals(pair.getReady()))
                    .toList();
            Map<String, TechnicalSnapshotPair> pairBySymbol = pairs.stream()
                    .collect(Collectors.toMap(TechnicalSnapshotPair::getSymbol, Function.identity(), (left, right) -> left));

            BigDecimal marketBreadthPct = marketBreadthService.calculateMarketBreadthPct(pairs);
            TechnicalSnapshotPair btcPair = pairBySymbol.get(BTCUSDT);
            TechnicalSnapshotPair ethPair = pairBySymbol.get(ETHUSDT);
            Ticker24h btcTicker = tickerBySymbol(tickers).get(BTCUSDT);
            BigDecimal btcFourHourChangePct = btcPair == null
                    ? null
                    : marketRegimeService.calculateBtcFourHourChangePct(btcPair.getFourHour());
            MarketRegimeResult marketRegimeResult = marketRegimeService.calculate(
                    btcPair == null ? null : btcPair.getOneHour(),
                    btcPair == null ? null : btcPair.getFourHour(),
                    ethPair == null ? null : ethPair.getFourHour(),
                    btcTicker,
                    marketBreadthPct,
                    btcFourHourChangePct);

            log.info("MANUAL_SCORING_CHECK marketRegime={} marketBreadthPct={}",
                    marketRegimeResult.getMarketRegime(), marketRegimeResult.getMarketBreadthPct());

            List<String> selectedSymbolNames = selectedSymbols.stream().map(SymbolInfo::getSymbol).toList();
            Map<String, FuturesSnapshot> futuresBySymbol = futuresDataService.loadForSymbols(selectedSymbolNames);
            Map<String, Ticker24h> tickerBySymbol = tickerBySymbol(tickers);
            Map<String, BookTicker> bookTickerBySymbol = bookTickerBySymbol(bookTickers);

            for (String symbol : selectedSymbolNames) {
                TechnicalSnapshotPair pair = pairBySymbol.get(symbol);
                CoinScanResult result = coinScoringService.score(CoinScoringInput.builder()
                        .symbol(symbol)
                        .oneHour(pair == null ? null : pair.getOneHour())
                        .fourHour(pair == null ? null : pair.getFourHour())
                        .ticker24h(tickerBySymbol.get(symbol))
                        .bookTicker(bookTickerBySymbol.get(symbol))
                        .futuresSnapshot(futuresBySymbol.get(symbol))
                        .marketRegimeResult(marketRegimeResult)
                        .build());
                log.info("MANUAL_SCORING_CHECK symbol={} longScore={} shortScore={} directionBias={} classification={} riskLevel={} warnings={}",
                        result.getSymbol(), result.getLongScore(), result.getShortScore(), result.getDirectionBias(),
                        result.getClassification(), result.getRiskLevel(), result.getWarnings());
            }
        } catch (Exception exception) {
            log.error("MANUAL_SCORING_CHECK_FAILED message={}", exception.getMessage(), exception);
        }
    }

    private List<SymbolInfo> selectManualSymbols(List<SymbolInfo> allSymbols, List<SymbolInfo> passedSymbols) {
        Map<String, SymbolInfo> selected = new LinkedHashMap<>();
        List<SymbolInfo> safePassedSymbols = passedSymbols == null ? List.of() : passedSymbols;
        safePassedSymbols.stream()
                .limit(MANUAL_SYMBOL_LIMIT)
                .forEach(symbolInfo -> selected.put(symbolInfo.getSymbol(), symbolInfo));

        Map<String, SymbolInfo> allBySymbol = new LinkedHashMap<>();
        List<SymbolInfo> safeAllSymbols = allSymbols == null ? List.of() : allSymbols;
        safeAllSymbols.forEach(symbolInfo -> allBySymbol.put(symbolInfo.getSymbol(), symbolInfo));
        addIfAvailable(selected, allBySymbol, BTCUSDT);
        addIfAvailable(selected, allBySymbol, ETHUSDT);
        return new ArrayList<>(selected.values());
    }

    private void addIfAvailable(Map<String, SymbolInfo> selected, Map<String, SymbolInfo> allBySymbol, String symbol) {
        SymbolInfo symbolInfo = allBySymbol.get(symbol);
        if (symbolInfo != null) {
            selected.putIfAbsent(symbol, symbolInfo);
        }
    }

    private Map<String, Ticker24h> tickerBySymbol(List<Ticker24h> tickers) {
        List<Ticker24h> safeTickers = tickers == null ? List.of() : tickers;
        return safeTickers.stream()
                .filter(ticker -> ticker != null && ticker.getSymbol() != null)
                .collect(Collectors.toMap(Ticker24h::getSymbol, Function.identity(), (left, right) -> left));
    }

    private Map<String, BookTicker> bookTickerBySymbol(List<BookTicker> bookTickers) {
        List<BookTicker> safeBookTickers = bookTickers == null ? List.of() : bookTickers;
        return safeBookTickers.stream()
                .filter(bookTicker -> bookTicker != null && bookTicker.getSymbol() != null)
                .collect(Collectors.toMap(BookTicker::getSymbol, Function.identity(), (left, right) -> left));
    }
}
