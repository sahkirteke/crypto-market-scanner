package com.crypto.scanner.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.binance.dto.BinanceFundingRateDto;
import com.crypto.binance.dto.BinanceOpenInterestDto;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuturesDataService {
    private final BinanceFuturesClient binanceFuturesClient;
    private final ScannerProperties scannerProperties;

    public FuturesSnapshot loadForSymbol(String symbol) {
        BigDecimal fundingRate = loadFundingRate(symbol);
        BigDecimal openInterest = loadOpenInterest(symbol);
        List<ReasonTag> warnings = new ArrayList<>();

        CrowdingFlags crowdingFlags = applyFundingWarnings(fundingRate, warnings);
        if (openInterest == null) {
            warnings.add(ReasonTag.OPEN_INTEREST_MISSING);
        }

        FuturesSnapshot snapshot = FuturesSnapshot.builder()
                .symbol(symbol)
                .fundingRate(fundingRate)
                .openInterest(openInterest)
                .longCrowded(crowdingFlags.longCrowded())
                .shortCrowded(crowdingFlags.shortCrowded())
                .warnings(warnings)
                .snapshotTime(Instant.now())
                .build();

        log.info("FUTURES_READY symbol={} funding={} openInterest={} longCrowded={} shortCrowded={}",
                snapshot.getSymbol(), snapshot.getFundingRate(), snapshot.getOpenInterest(),
                snapshot.getLongCrowded(), snapshot.getShortCrowded());
        return snapshot;
    }

    public Map<String, FuturesSnapshot> loadForSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("FUTURES_LOAD_DONE total=0 loaded=0");
            return Collections.emptyMap();
        }

        Map<String, FuturesSnapshot> snapshots = new LinkedHashMap<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            try {
                FuturesSnapshot snapshot = loadForSymbol(symbol);
                snapshots.put(symbol, snapshot);
            } catch (Exception exception) {
                log.error("FUTURES_SYMBOL_ERROR symbol={} message={}", symbol, exception.getMessage(), exception);
                snapshots.put(symbol, emptySnapshot(symbol));
            }
        }

        log.info("FUTURES_LOAD_DONE total={} loaded={}", symbols.size(), snapshots.size());
        return snapshots;
    }

    private BigDecimal loadFundingRate(String symbol) {
        try {
            List<BinanceFundingRateDto> fundingRates = binanceFuturesClient.getFundingRate(symbol);
            BinanceFundingRateDto latestFundingRate = latestFundingRate(fundingRates);
            return latestFundingRate == null ? null : toBigDecimal(latestFundingRate.getFundingRate());
        } catch (Exception exception) {
            log.error("FUTURES_FUNDING_ERROR symbol={} message={}", symbol, exception.getMessage(), exception);
            return null;
        }
    }

    private BigDecimal loadOpenInterest(String symbol) {
        try {
            BinanceOpenInterestDto openInterest = binanceFuturesClient.getOpenInterest(symbol);
            return openInterest == null ? null : toBigDecimal(openInterest.getOpenInterest());
        } catch (Exception exception) {
            log.error("FUTURES_OPEN_INTEREST_ERROR symbol={} message={}", symbol, exception.getMessage(), exception);
            return null;
        }
    }

    private BinanceFundingRateDto latestFundingRate(List<BinanceFundingRateDto> fundingRates) {
        if (fundingRates == null || fundingRates.isEmpty()) {
            return null;
        }
        return fundingRates.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(
                        BinanceFundingRateDto::getFundingTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private CrowdingFlags applyFundingWarnings(BigDecimal fundingRate, List<ReasonTag> warnings) {
        if (fundingRate == null) {
            return new CrowdingFlags(false, false);
        }

        ScannerProperties.Funding funding = funding();
        if (fundingRate.compareTo(funding.getDangerPositive()) >= 0) {
            warnings.add(ReasonTag.LONG_CROWDED);
            return new CrowdingFlags(true, false);
        }
        if (fundingRate.compareTo(funding.getWarningPositive()) >= 0) {
            warnings.add(ReasonTag.FUNDING_SLIGHTLY_HIGH);
            return new CrowdingFlags(false, false);
        }
        if (fundingRate.compareTo(funding.getDangerNegative()) <= 0) {
            warnings.add(ReasonTag.SHORT_CROWDED);
            return new CrowdingFlags(false, true);
        }
        if (fundingRate.compareTo(funding.getWarningNegative()) <= 0) {
            warnings.add(ReasonTag.FUNDING_SLIGHTLY_NEGATIVE);
            return new CrowdingFlags(false, false);
        }

        warnings.add(ReasonTag.FUNDING_NORMAL);
        return new CrowdingFlags(false, false);
    }

    private FuturesSnapshot emptySnapshot(String symbol) {
        List<ReasonTag> warnings = new ArrayList<>();
        warnings.add(ReasonTag.OPEN_INTEREST_MISSING);
        return FuturesSnapshot.builder()
                .symbol(symbol)
                .longCrowded(false)
                .shortCrowded(false)
                .warnings(warnings)
                .snapshotTime(Instant.now())
                .build();
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ScannerProperties.Funding funding() {
        if (scannerProperties.getFunding() == null) {
            scannerProperties.setFunding(new ScannerProperties.Funding());
        }
        return scannerProperties.getFunding();
    }

    private record CrowdingFlags(boolean longCrowded, boolean shortCrowded) {
    }
}
