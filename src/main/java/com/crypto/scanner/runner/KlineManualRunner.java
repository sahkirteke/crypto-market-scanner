package com.crypto.scanner.runner;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.KlineBundle;
import com.crypto.domain.model.KlineLoadResult;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.service.KlineService;
import com.crypto.scanner.service.PreFilterService;
import com.crypto.scanner.service.SymbolUniverseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual")
@RequiredArgsConstructor
public class KlineManualRunner implements CommandLineRunner {
    private static final int MANUAL_SYMBOL_LIMIT = 5;

    private final SymbolUniverseService symbolUniverseService;
    private final BinanceFuturesClient binanceFuturesClient;
    private final PreFilterService preFilterService;
    private final KlineService klineService;

    @Override
    public void run(String... args) {
        try {
            List<SymbolInfo> symbols = symbolUniverseService.loadTradableSymbols();
            List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            PreFilterResult preFilterResult = preFilterService.apply(symbols, tickers, bookTickers);
            List<SymbolInfo> firstPassedSymbols = preFilterResult.getPassedSymbols().stream()
                    .limit(MANUAL_SYMBOL_LIMIT)
                    .toList();

            KlineLoadResult klineLoadResult = klineService.loadForSymbols(firstPassedSymbols);

            log.info("MANUAL_KLINE_CHECK inputCount={}", firstPassedSymbols.size());
            log.info("MANUAL_KLINE_CHECK ready={}", klineLoadResult.getReadyCount());
            log.info("MANUAL_KLINE_CHECK notReady={}", klineLoadResult.getNotReadyCount());
            log.info("MANUAL_KLINE_CHECK readySymbols={}", readySymbols(klineLoadResult));
            log.info("MANUAL_KLINE_CHECK notReadySymbols={}", notReadySymbols(klineLoadResult));
        } catch (Exception exception) {
            log.error("MANUAL_KLINE_CHECK_FAILED message={}", exception.getMessage(), exception);
        }
    }

    private List<String> readySymbols(KlineLoadResult result) {
        return result.getReadyBundles().stream()
                .map(KlineBundle::getSymbol)
                .toList();
    }

    private List<String> notReadySymbols(KlineLoadResult result) {
        return result.getNotReadyBundles().stream()
                .map(bundle -> bundle.getSymbol() + ":" + bundle.getEliminatedReason())
                .toList();
    }
}
