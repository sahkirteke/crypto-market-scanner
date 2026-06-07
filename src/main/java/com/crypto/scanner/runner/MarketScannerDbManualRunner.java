package com.crypto.scanner.runner;

import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.scanner.service.MarketScannerOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manual-scanner-db")
@RequiredArgsConstructor
public class MarketScannerDbManualRunner implements CommandLineRunner {
    private final MarketScannerOrchestratorService marketScannerOrchestratorService;

    @Override
    public void run(String... args) {
        log.info("MANUAL_SCANNER_DB_CHECK started");
        MarketScanResult result = marketScannerOrchestratorService.runScanAndPersist(ScanType.FOUR_HOUR);
        log.info("MANUAL_SCANNER_DB_CHECK scanRunId={}", result.getScanRunId());
        log.info("MANUAL_SCANNER_DB_CHECK regime={}", result.getMarketRegime());
        log.info("MANUAL_SCANNER_DB_CHECK strongLong={}", result.getStrongLongCount());
        log.info("MANUAL_SCANNER_DB_CHECK strongShort={}", result.getStrongShortCount());
        log.info("MANUAL_SCANNER_DB_CHECK watchlist={}", result.getWatchlistCount());
        log.info("MANUAL_SCANNER_DB_CHECK eliminated={}", result.getEliminatedCount());
    }
}
