package com.crypto.scanner.runner;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.KlineLoadResult;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.TechnicalSnapshotPair;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.model.MarketRegimeResult;
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
@Profile("manual-regime")
@RequiredArgsConstructor
public class MarketRegimeManualRunner implements CommandLineRunner {
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

    @Override
    public void run(String... args) {
        try {
            List<SymbolInfo> symbols = symbolUniverseService.loadTradableSymbols();
            List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            PreFilterResult preFilterResult = preFilterService.apply(symbols, tickers, bookTickers);

            List<SymbolInfo> inputSymbols = selectManualSymbols(preFilterResult.getPassedSymbols(), symbols);
            log.info("MANUAL_REGIME_CHECK inputSymbols={}", inputSymbols.stream().map(SymbolInfo::getSymbol).toList());

            KlineLoadResult klineLoadResult = klineService.loadForSymbols(inputSymbols);
            List<TechnicalSnapshotPair> technicalPairs = klineLoadResult.getReadyBundles().stream()
                    .map(indicatorService::calculatePair)
                    .toList();

            TechnicalSnapshotPair btcPair = findPair(technicalPairs, BTCUSDT);
            TechnicalSnapshotPair ethPair = findPair(technicalPairs, ETHUSDT);
            BigDecimal marketBreadthPct = marketBreadthService.calculateMarketBreadthPct(technicalPairs);
            BigDecimal btcFourHourChangePct = marketRegimeService.calculateBtcFourHourChangePct(
                    btcPair == null ? null : btcPair.getFourHour());
            Ticker24h btcTicker = findTicker(tickers, BTCUSDT);

            MarketRegimeResult result = marketRegimeService.calculate(
                    btcPair == null ? null : btcPair.getOneHour(),
                    btcPair == null ? null : btcPair.getFourHour(),
                    ethPair == null ? null : ethPair.getFourHour(),
                    btcTicker,
                    marketBreadthPct,
                    btcFourHourChangePct);

            log.info("MANUAL_REGIME_CHECK inputPairs={}", technicalPairs.size());
            log.info("MANUAL_REGIME_CHECK marketBreadthPct={}", marketBreadthPct);
            log.info("MANUAL_REGIME_CHECK btcFourHourChangePct={}", btcFourHourChangePct);
            log.info("MANUAL_REGIME_CHECK regime={}", result.getMarketRegime());
            log.info("MANUAL_REGIME_CHECK reasons={}", result.getReasonTags());
            log.info("MANUAL_REGIME_CHECK blockNewLong={}", result.getBlockNewLong());
            log.info("MANUAL_REGIME_CHECK blockNewShort={}", result.getBlockNewShort());
        } catch (Exception exception) {
            log.error("MANUAL_REGIME_CHECK_FAILED message={}", exception.getMessage(), exception);
        }
    }

    private List<SymbolInfo> selectManualSymbols(List<SymbolInfo> passedSymbols, List<SymbolInfo> allSymbols) {
        Map<String, SymbolInfo> selectedBySymbol = new LinkedHashMap<>();
        List<SymbolInfo> safePassedSymbols = passedSymbols == null ? List.of() : passedSymbols;
        safePassedSymbols.stream()
                .limit(MANUAL_SYMBOL_LIMIT)
                .forEach(symbolInfo -> selectedBySymbol.put(symbolInfo.getSymbol(), symbolInfo));

        Map<String, SymbolInfo> allBySymbol = allSymbols == null ? Map.of() : allSymbols.stream()
                .collect(Collectors.toMap(SymbolInfo::getSymbol, Function.identity(), (left, right) -> left));
        addRequiredSymbol(selectedBySymbol, allBySymbol, BTCUSDT);
        addRequiredSymbol(selectedBySymbol, allBySymbol, ETHUSDT);
        return new ArrayList<>(selectedBySymbol.values());
    }

    private void addRequiredSymbol(Map<String, SymbolInfo> selectedBySymbol, Map<String, SymbolInfo> allBySymbol, String symbol) {
        if (!selectedBySymbol.containsKey(symbol)) {
            selectedBySymbol.put(symbol, allBySymbol.getOrDefault(symbol, SymbolInfo.builder().symbol(symbol).build()));
        }
    }

    private TechnicalSnapshotPair findPair(List<TechnicalSnapshotPair> pairs, String symbol) {
        return pairs.stream()
                .filter(pair -> symbol.equals(pair.getSymbol()))
                .findFirst()
                .orElse(null);
    }

    private Ticker24h findTicker(List<Ticker24h> tickers, String symbol) {
        if (tickers == null) {
            return null;
        }
        return tickers.stream()
                .filter(ticker -> symbol.equals(ticker.getSymbol()))
                .findFirst()
                .orElse(null);
    }
}
