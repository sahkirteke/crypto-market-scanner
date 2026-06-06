package com.crypto.scanner.runner;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.service.FuturesDataService;
import com.crypto.scanner.service.PreFilterService;
import com.crypto.scanner.service.SymbolUniverseService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-futures")
@RequiredArgsConstructor
public class FuturesDataManualRunner implements CommandLineRunner {
    private static final int SAMPLE_SYMBOL_LIMIT = 5;
    private static final String BTCUSDT = "BTCUSDT";
    private static final String ETHUSDT = "ETHUSDT";

    private final SymbolUniverseService symbolUniverseService;
    private final BinanceFuturesClient binanceFuturesClient;
    private final PreFilterService preFilterService;
    private final FuturesDataService futuresDataService;

    @Override
    public void run(String... args) {
        try {
            List<SymbolInfo> tradableSymbols = symbolUniverseService.loadTradableSymbols();
            List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            PreFilterResult preFilterResult = preFilterService.apply(tradableSymbols, tickers, bookTickers);
            List<String> symbols = selectSymbols(preFilterResult);

            log.info("MANUAL_FUTURES_CHECK inputCount={}", symbols.size());
            Map<String, FuturesSnapshot> snapshots = futuresDataService.loadForSymbols(symbols);
            log.info("MANUAL_FUTURES_CHECK loaded={}", snapshots.size());
            snapshots.values().forEach(this::logSnapshot);
        } catch (Exception exception) {
            log.error("MANUAL_FUTURES_CHECK_FAILED message={}", exception.getMessage(), exception);
        }
    }

    private List<String> selectSymbols(PreFilterResult preFilterResult) {
        Set<String> selectedSymbols = new LinkedHashSet<>();
        if (preFilterResult != null && preFilterResult.getPassedSymbols() != null) {
            preFilterResult.getPassedSymbols().stream()
                    .map(SymbolInfo::getSymbol)
                    .filter(symbol -> symbol != null && !symbol.isBlank())
                    .limit(SAMPLE_SYMBOL_LIMIT)
                    .forEach(selectedSymbols::add);
        }
        selectedSymbols.add(BTCUSDT);
        selectedSymbols.add(ETHUSDT);
        return new ArrayList<>(selectedSymbols);
    }

    private void logSnapshot(FuturesSnapshot snapshot) {
        log.info("MANUAL_FUTURES_CHECK symbol={} funding={} openInterest={} longCrowded={} shortCrowded={} warnings={}",
                snapshot.getSymbol(), snapshot.getFundingRate(), snapshot.getOpenInterest(),
                snapshot.getLongCrowded(), snapshot.getShortCrowded(), snapshot.getWarnings());
    }
}
