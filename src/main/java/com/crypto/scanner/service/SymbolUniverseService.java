package com.crypto.scanner.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.scanner.config.ScannerProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolUniverseService {
    private static final String QUOTE_ASSET_USDT = "USDT";
    private static final String CONTRACT_TYPE_PERPETUAL = "PERPETUAL";
    private static final String STATUS_TRADING = "TRADING";

    private final BinanceFuturesClient binanceFuturesClient;
    private final ScannerProperties scannerProperties;

    public List<SymbolInfo> loadTradableSymbols() {
        List<SymbolInfo> exchangeInfo = binanceFuturesClient.getExchangeInfo();
        if (exchangeInfo == null) {
            exchangeInfo = Collections.emptyList();
        }

        Set<String> blacklist = normalizedBlacklist();
        List<SymbolInfo> tradableSymbols = new ArrayList<>();
        int blacklistedCount = 0;

        for (SymbolInfo symbolInfo : exchangeInfo) {
            if (!hasRequiredFields(symbolInfo) || !matchesTradableUniverse(symbolInfo)) {
                continue;
            }

            if (blacklist.contains(normalize(symbolInfo.getSymbol()))) {
                blacklistedCount++;
                log.debug("SYMBOL_BLACKLISTED symbol={}", symbolInfo.getSymbol());
                continue;
            }

            tradableSymbols.add(symbolInfo);
        }

        tradableSymbols.sort(Comparator.comparing(SymbolInfo::getSymbol));
        log.info("SYMBOL_UNIVERSE_READY total={} tradable={} blacklisted={}",
                exchangeInfo.size(), tradableSymbols.size(), blacklistedCount);
        return tradableSymbols;
    }

    private Set<String> normalizedBlacklist() {
        List<String> configuredBlacklist = scannerProperties.getBlacklist();
        if (configuredBlacklist == null || configuredBlacklist.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> normalized = new HashSet<>();
        configuredBlacklist.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(symbol -> !symbol.isBlank())
                .forEach(normalized::add);
        return normalized;
    }

    private boolean hasRequiredFields(SymbolInfo symbolInfo) {
        return symbolInfo != null
                && symbolInfo.getSymbol() != null
                && symbolInfo.getQuoteAsset() != null
                && symbolInfo.getContractType() != null
                && symbolInfo.getStatus() != null;
    }

    private boolean matchesTradableUniverse(SymbolInfo symbolInfo) {
        return QUOTE_ASSET_USDT.equals(symbolInfo.getQuoteAsset())
                && CONTRACT_TYPE_PERPETUAL.equals(symbolInfo.getContractType())
                && STATUS_TRADING.equals(symbolInfo.getStatus());
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
