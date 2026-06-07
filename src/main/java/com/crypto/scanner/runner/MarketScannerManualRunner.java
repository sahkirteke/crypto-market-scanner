package com.crypto.scanner.runner;

import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.scanner.service.MarketScannerService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-scanner")
@RequiredArgsConstructor
public class MarketScannerManualRunner implements CommandLineRunner {
    private static final int TOP_RESULT_LIMIT = 5;

    private final MarketScannerService marketScannerService;

    @Override
    public void run(String... args) {
        MarketScanResult result = marketScannerService.runFourHourScan();

        log.info("MANUAL_SCANNER_CHECK scanType={}", result.getScanType());
        log.info("MANUAL_SCANNER_CHECK regime={}", result.getMarketRegime());
        log.info("MANUAL_SCANNER_CHECK breadth={}", result.getMarketBreadthPct());
        log.info("MANUAL_SCANNER_CHECK totalSymbols={}", result.getTotalSymbols());
        log.info("MANUAL_SCANNER_CHECK preFilterPassed={}", result.getPreFilterPassedCount());
        log.info("MANUAL_SCANNER_CHECK strongLong={}", result.getStrongLongCount());
        log.info("MANUAL_SCANNER_CHECK strongShort={}", result.getStrongShortCount());
        log.info("MANUAL_SCANNER_CHECK watchlist={}", result.getWatchlistCount());
        log.info("MANUAL_SCANNER_CHECK eliminated={}", result.getEliminatedCount());
        log.info("MANUAL_SCANNER_CHECK topStrongLong={}", topSymbolsWithScore(result.getStrongLong()));
        log.info("MANUAL_SCANNER_CHECK topStrongShort={}", topSymbolsWithScore(result.getStrongShort()));
        log.info("MANUAL_SCANNER_CHECK topWatchlist={}", topWatchlist(result.getWatchlist()));
    }

    private String topSymbolsWithScore(List<CoinScanResult> results) {
        return results.stream()
                .limit(TOP_RESULT_LIMIT)
                .map(result -> result.getSymbol() + ":" + result.getScore())
                .collect(Collectors.joining(","));
    }

    private String topWatchlist(List<CoinScanResult> results) {
        return results.stream()
                .limit(TOP_RESULT_LIMIT)
                .map(result -> result.getSymbol() + ":" + result.getClassification() + ":" + result.getScore())
                .collect(Collectors.joining(","));
    }
}
