package com.crypto.scanner.runner;

import com.crypto.domain.model.SymbolInfo;
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
public class SymbolUniverseManualRunner implements CommandLineRunner {
    private static final String CRVUSDT = "CRVUSDT";
    private static final int FIRST_SYMBOLS_LIMIT = 10;

    private final SymbolUniverseService symbolUniverseService;

    @Override
    public void run(String... args) {
        List<SymbolInfo> tradableSymbols = symbolUniverseService.loadTradableSymbols();
        List<String> firstSymbols = tradableSymbols.stream()
                .map(SymbolInfo::getSymbol)
                .limit(FIRST_SYMBOLS_LIMIT)
                .toList();
        boolean containsCrvusdt = tradableSymbols.stream()
                .map(SymbolInfo::getSymbol)
                .anyMatch(CRVUSDT::equals);

        log.info("MANUAL_SYMBOL_UNIVERSE_CHECK tradableCount={}", tradableSymbols.size());
        log.info("MANUAL_SYMBOL_UNIVERSE_CHECK firstSymbols={}", firstSymbols);
        log.info("MANUAL_SYMBOL_UNIVERSE_CHECK containsCRVUSDT={}", containsCrvusdt);
    }
}
