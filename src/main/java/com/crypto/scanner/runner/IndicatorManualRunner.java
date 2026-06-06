package com.crypto.scanner.runner;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.KlineBundle;
import com.crypto.domain.model.KlineLoadResult;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.TechnicalSnapshotPair;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.service.IndicatorService;
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
@Profile("manual-indicator")
@RequiredArgsConstructor
public class IndicatorManualRunner implements CommandLineRunner {
    private static final int MANUAL_SYMBOL_LIMIT = 3;

    private final SymbolUniverseService symbolUniverseService;
    private final BinanceFuturesClient binanceFuturesClient;
    private final PreFilterService preFilterService;
    private final KlineService klineService;
    private final IndicatorService indicatorService;

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

            log.info("MANUAL_INDICATOR_CHECK inputCount={}", firstPassedSymbols.size());

            KlineLoadResult klineLoadResult = klineService.loadForSymbols(firstPassedSymbols);
            List<TechnicalSnapshotPair> readyPairs = klineLoadResult.getReadyBundles().stream()
                    .limit(MANUAL_SYMBOL_LIMIT)
                    .map(indicatorService::calculatePair)
                    .filter(pair -> Boolean.TRUE.equals(pair.getReady()))
                    .toList();

            log.info("MANUAL_INDICATOR_CHECK readyPairs={}", readyPairs.size());
            readyPairs.forEach(this::logPair);
        } catch (Exception exception) {
            log.error("MANUAL_INDICATOR_CHECK_FAILED message={}", exception.getMessage(), exception);
        }
    }

    private void logPair(TechnicalSnapshotPair pair) {
        TechnicalSnapshot oneHour = pair.getOneHour();
        TechnicalSnapshot fourHour = pair.getFourHour();
        log.info("MANUAL_INDICATOR_CHECK symbol={} close1h={} ema20_1h={} rsi1h={} macdHist1h={} close4h={} ema20_4h={}",
                pair.getSymbol(),
                valueOrNull(oneHour, TechnicalSnapshot::getClose),
                valueOrNull(oneHour, TechnicalSnapshot::getEma20),
                valueOrNull(oneHour, TechnicalSnapshot::getRsi14),
                valueOrNull(oneHour, TechnicalSnapshot::getMacdHist),
                valueOrNull(fourHour, TechnicalSnapshot::getClose),
                valueOrNull(fourHour, TechnicalSnapshot::getEma20));
    }

    private Object valueOrNull(TechnicalSnapshot snapshot, java.util.function.Function<TechnicalSnapshot, Object> mapper) {
        return snapshot == null ? null : mapper.apply(snapshot);
    }
}
