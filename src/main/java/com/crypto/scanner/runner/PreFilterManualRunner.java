package com.crypto.scanner.runner;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.FilterDecision;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
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
@Profile("manual-prefilter")
@RequiredArgsConstructor
public class PreFilterManualRunner implements CommandLineRunner {
    private static final int LOG_SAMPLE_LIMIT = 10;

    private final SymbolUniverseService symbolUniverseService;
    private final BinanceFuturesClient binanceFuturesClient;
    private final PreFilterService preFilterService;

    @Override
    public void run(String... args) {
        try {
            List<SymbolInfo> symbols = symbolUniverseService.loadTradableSymbols();
            List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            PreFilterResult result = preFilterService.apply(symbols, tickers, bookTickers);

            log.info("MANUAL_PREFILTER_CHECK total={}", result.getTotalCount());
            log.info("MANUAL_PREFILTER_CHECK passed={}", result.getPassedCount());
            log.info("MANUAL_PREFILTER_CHECK eliminated={}", result.getEliminatedCount());
            log.info("MANUAL_PREFILTER_CHECK firstPassedSymbols={}", firstPassedSymbols(result));
            log.info("MANUAL_PREFILTER_CHECK firstEliminated={}", firstEliminated(result));
        } catch (Exception exception) {
            log.error("MANUAL_PREFILTER_CHECK_FAILED message={}", exception.getMessage(), exception);
        }
    }

    private List<String> firstPassedSymbols(PreFilterResult result) {
        return result.getPassedSymbols().stream()
                .map(SymbolInfo::getSymbol)
                .limit(LOG_SAMPLE_LIMIT)
                .toList();
    }

    private List<String> firstEliminated(PreFilterResult result) {
        return result.getEliminatedDecisions().stream()
                .limit(LOG_SAMPLE_LIMIT)
                .map(this::formatEliminated)
                .toList();
    }

    private String formatEliminated(FilterDecision decision) {
        return decision.getSymbol() + ":" + decision.getEliminatedReason();
    }
}
