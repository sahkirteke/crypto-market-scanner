package com.crypto.scanner.service;

import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.FilterDecision;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreFilterService {
    private final ScannerProperties scannerProperties;

    public PreFilterResult apply(
            List<SymbolInfo> symbols,
            List<Ticker24h> tickers,
            List<BookTicker> bookTickers
    ) {
        List<SymbolInfo> safeSymbols = symbols == null ? Collections.emptyList() : symbols;
        Map<String, Ticker24h> tickerBySymbol = mapBySymbol(tickers, Ticker24h::getSymbol);
        Map<String, BookTicker> bookTickerBySymbol = mapBySymbol(bookTickers, BookTicker::getSymbol);

        List<SymbolInfo> passedSymbols = new ArrayList<>();
        List<FilterDecision> decisions = new ArrayList<>();
        List<FilterDecision> eliminatedDecisions = new ArrayList<>();

        for (SymbolInfo symbolInfo : safeSymbols) {
            if (symbolInfo == null) {
                continue;
            }

            FilterDecision decision = decide(symbolInfo, tickerBySymbol, bookTickerBySymbol);
            decisions.add(decision);

            if (Boolean.TRUE.equals(decision.getPassed())) {
                passedSymbols.add(symbolInfo);
            } else {
                eliminatedDecisions.add(decision);
                log.debug("PRE_FILTER_ELIMINATED symbol={} reason={}",
                        decision.getSymbol(), decision.getEliminatedReason());
            }
        }

        PreFilterResult result = PreFilterResult.builder()
                .passedSymbols(passedSymbols)
                .decisions(decisions)
                .eliminatedDecisions(eliminatedDecisions)
                .totalCount(decisions.size())
                .passedCount(passedSymbols.size())
                .eliminatedCount(eliminatedDecisions.size())
                .build();

        log.info("PRE_FILTER_DONE total={} passed={} eliminated={}",
                result.getTotalCount(), result.getPassedCount(), result.getEliminatedCount());
        return result;
    }

    private FilterDecision decide(
            SymbolInfo symbolInfo,
            Map<String, Ticker24h> tickerBySymbol,
            Map<String, BookTicker> bookTickerBySymbol
    ) {
        String symbol = symbolInfo.getSymbol();
        Ticker24h ticker = tickerBySymbol.get(symbol);
        BookTicker bookTicker = bookTickerBySymbol.get(symbol);

        FilterDecision decision = FilterDecision.builder()
                .symbol(symbol)
                .passed(true)
                .eliminatedReason(EliminationReason.NONE)
                .reasons(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();

        if (ticker == null) {
            eliminate(decision, EliminationReason.MISSING_TICKER, ReasonTag.MISSING_TICKER);
        } else {
            decision.setQuoteVolume24h(ticker.getQuoteVolume());
            decision.setPriceChange24hPct(ticker.getPriceChangePercent());
        }

        if (bookTicker == null) {
            eliminate(decision, EliminationReason.MISSING_BOOK_TICKER, ReasonTag.MISSING_BOOK_TICKER);
        } else {
            decision.setSpreadPct(bookTicker.getSpreadPct());
        }

        if (ticker != null && isLowVolume(ticker.getQuoteVolume())) {
            eliminate(decision, EliminationReason.LOW_VOLUME, ReasonTag.LOW_VOLUME);
        }

        if (bookTicker != null && isHighSpread(bookTicker.getSpreadPct())) {
            eliminate(decision, EliminationReason.HIGH_SPREAD, ReasonTag.HIGH_SPREAD);
        }

        addPumpDumpWarnings(decision, ticker);
        return decision;
    }

    private boolean isLowVolume(BigDecimal quoteVolume) {
        return quoteVolume == null
                || quoteVolume.compareTo(liquidity().getMinQuoteVolume24h()) < 0;
    }

    private boolean isHighSpread(BigDecimal spreadPct) {
        return spreadPct == null
                || spreadPct.compareTo(liquidity().getMaxSpreadPct()) > 0;
    }

    private void addPumpDumpWarnings(FilterDecision decision, Ticker24h ticker) {
        if (ticker == null || ticker.getPriceChangePercent() == null) {
            return;
        }

        BigDecimal priceChangePercent = ticker.getPriceChangePercent();
        if (priceChangePercent.compareTo(liquidity().getMaxPump24hPct()) > 0) {
            decision.getWarnings().add(ReasonTag.PUMPED_TOO_MUCH_WARNING);
        }
        if (priceChangePercent.compareTo(liquidity().getMaxDump24hPct()) < 0) {
            decision.getWarnings().add(ReasonTag.DUMPED_TOO_MUCH_WARNING);
        }
    }

    private void eliminate(FilterDecision decision, EliminationReason reason, ReasonTag reasonTag) {
        decision.setPassed(false);
        if (decision.getEliminatedReason() == EliminationReason.NONE) {
            decision.setEliminatedReason(reason);
        }
        decision.getReasons().add(reasonTag);
    }

    private ScannerProperties.Liquidity liquidity() {
        if (scannerProperties.getLiquidity() == null) {
            scannerProperties.setLiquidity(new ScannerProperties.Liquidity());
        }
        return scannerProperties.getLiquidity();
    }

    private <T> Map<String, T> mapBySymbol(List<T> items, Function<T, String> symbolExtractor) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> symbolExtractor.apply(item) != null)
                .collect(Collectors.toMap(
                        symbolExtractor,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }
}
