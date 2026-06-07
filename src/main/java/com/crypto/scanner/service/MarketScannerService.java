package com.crypto.scanner.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.FilterDecision;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.domain.model.KlineBundle;
import com.crypto.domain.model.KlineLoadResult;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.domain.model.PreFilterResult;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.TechnicalSnapshotPair;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.model.CoinScoringInput;
import com.crypto.scanner.model.MarketRegimeResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketScannerService {
    private static final String BTCUSDT = "BTCUSDT";
    private static final String ETHUSDT = "ETHUSDT";
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter ISTANBUL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ISTANBUL_ZONE);

    private final SymbolUniverseService symbolUniverseService;
    private final BinanceFuturesClient binanceFuturesClient;
    private final PreFilterService preFilterService;
    private final KlineService klineService;
    private final IndicatorService indicatorService;
    private final MarketBreadthService marketBreadthService;
    private final MarketRegimeService marketRegimeService;
    private final FuturesDataService futuresDataService;
    private final CoinScoringService coinScoringService;

    public MarketScanResult runOneHourScan() {
        return runScan(ScanType.ONE_HOUR);
    }

    public MarketScanResult runFourHourScan() {
        return runScan(ScanType.FOUR_HOUR);
    }

    public MarketScanResult runScan(ScanType scanType) {
        Instant scanStartTime = Instant.now();
        log.info("SCAN_STARTED scanType={}", scanType);

        try {
            List<SymbolInfo> tradableSymbols = symbolUniverseService.loadTradableSymbols();
            requireNotEmpty(tradableSymbols, "tradable symbols");
            log.info("SCAN_STAGE_DONE stage=SYMBOL_UNIVERSE count={}", size(tradableSymbols));

            List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
            requireNotEmpty(tickers, "24h tickers");
            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            requireNotEmpty(bookTickers, "book tickers");
            Map<String, Ticker24h> tickerMap = toMap(tickers, Ticker24h::getSymbol);
            Map<String, BookTicker> bookTickerMap = toMap(bookTickers, BookTicker::getSymbol);

            PreFilterResult preFilterResult = preFilterService.apply(tradableSymbols, tickers, bookTickers);
            log.info("SCAN_STAGE_DONE stage=PREFILTER passed={} eliminated={}",
                    preFilterResult.getPassedCount(), preFilterResult.getEliminatedCount());

            KlineLoadResult klineLoadResult = klineService.loadForSymbols(preFilterResult.getPassedSymbols());
            log.info("SCAN_STAGE_DONE stage=KLINES ready={} notReady={}",
                    klineLoadResult.getReadyCount(), klineLoadResult.getNotReadyCount());

            Map<String, TechnicalSnapshotPair> technicalPairMap = calculateTechnicalPairs(klineLoadResult.getReadyBundles());
            ensureBenchmarkTechnicalPair(technicalPairMap, BTCUSDT);
            ensureBenchmarkTechnicalPair(technicalPairMap, ETHUSDT);
            List<TechnicalSnapshotPair> technicalPairs = readyPairs(technicalPairMap.values());
            log.info("SCAN_STAGE_DONE stage=INDICATORS readyPairs={}", technicalPairs.size());

            BigDecimal marketBreadthPct = marketBreadthService.calculateMarketBreadthPct(technicalPairs);
            MarketRegimeResult marketRegimeResult = calculateMarketRegime(technicalPairMap, tickerMap, marketBreadthPct);
            log.info("SCAN_STAGE_DONE stage=MARKET_REGIME regime={} breadth={}",
                    marketRegimeResult.getMarketRegime(), marketRegimeResult.getMarketBreadthPct());

            List<String> scoringSymbols = technicalPairs.stream()
                    .map(TechnicalSnapshotPair::getSymbol)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<String, FuturesSnapshot> futuresMap = loadFuturesData(scoringSymbols);
            log.info("SCAN_STAGE_DONE stage=FUTURES loaded={}", futuresMap.size());

            List<CoinScanResult> scoredResults = scoreTechnicalPairs(
                    technicalPairs, tickerMap, bookTickerMap, futuresMap, marketRegimeResult);
            log.info("SCAN_STAGE_DONE stage=SCORING scored={}", scoredResults.size());

            List<CoinScanResult> allResults = new ArrayList<>(scoredResults);
            allResults.addAll(klineNotReadyResults(klineLoadResult.getNotReadyBundles(), scanStartTime));
            allResults.addAll(technicalNotReadyResults(technicalPairMap.values(), scanStartTime));
            allResults.addAll(eliminatedPreFilterResults(preFilterResult.getEliminatedDecisions(), scanStartTime));

            MarketScanResult result = buildResult(scanType, scanStartTime, tradableSymbols, preFilterResult,
                    marketRegimeResult, allResults);
            log.info("SCAN_COMPLETED scanType={} regime={} strongLong={} strongShort={} watchlist={} eliminated={}",
                    scanType, result.getMarketRegime(), result.getStrongLongCount(), result.getStrongShortCount(),
                    result.getWatchlistCount(), result.getEliminatedCount());
            return result;
        } catch (RuntimeException exception) {
            log.error("SCAN_FAILED scanType={} message={}", scanType, exception.getMessage(), exception);
            throw exception;
        }
    }

    private Map<String, TechnicalSnapshotPair> calculateTechnicalPairs(List<KlineBundle> readyBundles) {
        Map<String, TechnicalSnapshotPair> technicalPairMap = new LinkedHashMap<>();
        for (KlineBundle bundle : safeList(readyBundles)) {
            if (bundle == null || bundle.getSymbol() == null) {
                continue;
            }
            try {
                TechnicalSnapshotPair pair = indicatorService.calculatePair(bundle);
                technicalPairMap.put(bundle.getSymbol(), pair);
            } catch (RuntimeException exception) {
                log.error("INDICATOR_SYMBOL_ERROR symbol={} message={}", bundle.getSymbol(), exception.getMessage(), exception);
                technicalPairMap.put(bundle.getSymbol(), notReadyTechnicalPair(bundle.getSymbol(),
                        List.of(ReasonTag.DATA_NOT_READY), bundle.getWarnings()));
            }
        }
        return technicalPairMap;
    }

    private void ensureBenchmarkTechnicalPair(Map<String, TechnicalSnapshotPair> technicalPairMap, String symbol) {
        TechnicalSnapshotPair existingPair = technicalPairMap.get(symbol);
        if (existingPair != null && Boolean.TRUE.equals(existingPair.getReady())) {
            return;
        }

        try {
            KlineBundle benchmarkBundle = klineService.loadForSymbol(symbol);
            TechnicalSnapshotPair benchmarkPair = indicatorService.calculatePair(benchmarkBundle);
            technicalPairMap.put(symbol, benchmarkPair);
        } catch (RuntimeException exception) {
            log.error("BENCHMARK_TECH_ERROR symbol={} message={}", symbol, exception.getMessage(), exception);
            technicalPairMap.put(symbol, notReadyTechnicalPair(symbol, List.of(ReasonTag.DATA_NOT_READY), List.of()));
        }
    }


    private TechnicalSnapshotPair notReadyTechnicalPair(String symbol, List<ReasonTag> reasons, List<ReasonTag> warnings) {
        return TechnicalSnapshotPair.builder()
                .symbol(symbol)
                .ready(false)
                .reasons(new ArrayList<>(safeList(reasons)))
                .warnings(new ArrayList<>(safeList(warnings)))
                .build();
    }

    private List<TechnicalSnapshotPair> readyPairs(Iterable<TechnicalSnapshotPair> pairs) {
        List<TechnicalSnapshotPair> readyPairs = new ArrayList<>();
        for (TechnicalSnapshotPair pair : pairs) {
            if (pair != null && Boolean.TRUE.equals(pair.getReady()) && pair.getSymbol() != null) {
                readyPairs.add(pair);
            }
        }
        return readyPairs;
    }

    private MarketRegimeResult calculateMarketRegime(
            Map<String, TechnicalSnapshotPair> technicalPairMap,
            Map<String, Ticker24h> tickerMap,
            BigDecimal marketBreadthPct
    ) {
        TechnicalSnapshotPair btcPair = technicalPairMap.get(BTCUSDT);
        TechnicalSnapshotPair ethPair = technicalPairMap.get(ETHUSDT);
        BigDecimal btcFourHourChangePct = marketRegimeService.calculateBtcFourHourChangePct(
                btcPair == null ? null : btcPair.getFourHour());

        MarketRegimeResult result = marketRegimeService.calculate(
                btcPair == null ? null : btcPair.getOneHour(),
                btcPair == null ? null : btcPair.getFourHour(),
                ethPair == null ? null : ethPair.getFourHour(),
                tickerMap.get(BTCUSDT),
                marketBreadthPct,
                btcFourHourChangePct);

        if (!hasReadyBenchmarkPair(btcPair) || !hasReadyBenchmarkPair(ethPair)) {
            result.setMarketRegime(MarketRegime.CHOP);
            addReason(result, ReasonTag.MARKET_CHOP);
            addReason(result, ReasonTag.DATA_NOT_READY);
        }
        if (result.getMarketBreadthPct() == null) {
            result.setMarketBreadthPct(marketBreadthPct == null ? BigDecimal.ZERO : marketBreadthPct);
        }
        return result;
    }

    private boolean hasReadyBenchmarkPair(TechnicalSnapshotPair pair) {
        return pair != null && Boolean.TRUE.equals(pair.getReady()) && pair.getOneHour() != null && pair.getFourHour() != null;
    }

    private Map<String, FuturesSnapshot> loadFuturesData(List<String> scoringSymbols) {
        try {
            return futuresDataService.loadForSymbols(scoringSymbols);
        } catch (RuntimeException exception) {
            log.error("FUTURES_LOAD_ERROR message={}", exception.getMessage(), exception);
            return Collections.emptyMap();
        }
    }

    private List<CoinScanResult> scoreTechnicalPairs(
            List<TechnicalSnapshotPair> technicalPairs,
            Map<String, Ticker24h> tickerMap,
            Map<String, BookTicker> bookTickerMap,
            Map<String, FuturesSnapshot> futuresMap,
            MarketRegimeResult marketRegimeResult
    ) {
        List<CoinScanResult> scoredResults = new ArrayList<>();
        for (TechnicalSnapshotPair pair : technicalPairs) {
            CoinScoringInput input = CoinScoringInput.builder()
                    .symbol(pair.getSymbol())
                    .oneHour(pair.getOneHour())
                    .fourHour(pair.getFourHour())
                    .ticker24h(tickerMap.get(pair.getSymbol()))
                    .bookTicker(bookTickerMap.get(pair.getSymbol()))
                    .futuresSnapshot(futuresMap.get(pair.getSymbol()))
                    .marketRegimeResult(marketRegimeResult)
                    .build();
            try {
                scoredResults.add(coinScoringService.score(input));
            } catch (RuntimeException exception) {
                log.error("SCORING_SYMBOL_ERROR symbol={} message={}", pair.getSymbol(), exception.getMessage(), exception);
                scoredResults.add(dataNotReadyResult(pair.getSymbol(), pair.getReasons(), pair.getWarnings(), null, null,
                        null, Instant.now()));
            }
        }
        return scoredResults;
    }

    private List<CoinScanResult> klineNotReadyResults(List<KlineBundle> bundles, Instant scanStartTime) {
        return safeList(bundles).stream()
                .filter(Objects::nonNull)
                .map(bundle -> dataNotReadyResult(bundle.getSymbol(), bundle.getReasons(), bundle.getWarnings(),
                        bundle.getEliminatedReason(), null, null, scanStartTime))
                .toList();
    }

    private List<CoinScanResult> technicalNotReadyResults(Iterable<TechnicalSnapshotPair> pairs, Instant scanStartTime) {
        List<CoinScanResult> results = new ArrayList<>();
        for (TechnicalSnapshotPair pair : pairs) {
            if (pair != null && !Boolean.TRUE.equals(pair.getReady())) {
                results.add(dataNotReadyResult(pair.getSymbol(), pair.getReasons(), pair.getWarnings(),
                        EliminationReason.DATA_NOT_READY, null, null, scanStartTime));
            }
        }
        return results;
    }

    private List<CoinScanResult> eliminatedPreFilterResults(List<FilterDecision> eliminatedDecisions, Instant scanStartTime) {
        return safeList(eliminatedDecisions).stream()
                .filter(Objects::nonNull)
                .map(decision -> dataNotReadyResult(decision.getSymbol(), decision.getReasons(), decision.getWarnings(),
                        decision.getEliminatedReason(), decision.getQuoteVolume24h(), decision.getSpreadPct(),
                        decision.getPriceChange24hPct(), scanStartTime))
                .toList();
    }

    private CoinScanResult dataNotReadyResult(
            String symbol,
            List<ReasonTag> reasons,
            List<ReasonTag> warnings,
            EliminationReason eliminatedReason,
            BigDecimal quoteVolume24h,
            BigDecimal spreadPct,
            BigDecimal priceChange24hPct,
            Instant scanTime
    ) {
        List<ReasonTag> safeReasons = new ArrayList<>(safeList(reasons));
        if (safeReasons.isEmpty()) {
            safeReasons.add(ReasonTag.DATA_NOT_READY);
        }
        return CoinScanResult.builder()
                .symbol(symbol)
                .directionBias(DirectionBias.NEUTRAL)
                .classification(CoinClassification.ELIMINATED)
                .score(0)
                .longScore(0)
                .shortScore(0)
                .eliminatedReason(eliminatedReason == null ? EliminationReason.DATA_NOT_READY : eliminatedReason)
                .reasons(safeReasons)
                .warnings(new ArrayList<>(safeList(warnings)))
                .quoteVolume24h(quoteVolume24h)
                .spreadPct(spreadPct)
                .priceChange24hPct(priceChange24hPct)
                .scanTime(scanTime)
                .build();
    }

    private CoinScanResult dataNotReadyResult(
            String symbol,
            List<ReasonTag> reasons,
            List<ReasonTag> warnings,
            EliminationReason eliminatedReason,
            BigDecimal quoteVolume24h,
            BigDecimal spreadPct,
            Instant scanTime
    ) {
        return dataNotReadyResult(symbol, reasons, warnings, eliminatedReason, quoteVolume24h, spreadPct, null, scanTime);
    }

    private MarketScanResult buildResult(
            ScanType scanType,
            Instant scanStartTime,
            List<SymbolInfo> tradableSymbols,
            PreFilterResult preFilterResult,
            MarketRegimeResult marketRegimeResult,
            List<CoinScanResult> allResults
    ) {
        List<CoinScanResult> strongLong = filterAndSortByScore(allResults, CoinClassification.STRONG_LONG);
        List<CoinScanResult> strongShort = filterAndSortByScore(allResults, CoinClassification.STRONG_SHORT);
        List<CoinScanResult> watchlist = filterAndSortByScore(allResults, CoinClassification.WATCHLIST);
        List<CoinScanResult> eliminated = allResults.stream()
                .filter(result -> result != null && result.getClassification() == CoinClassification.ELIMINATED)
                .sorted(Comparator.comparing(CoinScanResult::getSymbol, Comparator.nullsLast(String::compareTo)))
                .toList();

        return MarketScanResult.builder()
                .scanRunId(null)
                .scanType(scanType)
                .scanTimeUtc(scanStartTime)
                .scanTimeIstanbulText(ISTANBUL_FORMATTER.format(scanStartTime))
                .marketRegime(marketRegimeResult.getMarketRegime())
                .marketBreadthPct(marketRegimeResult.getMarketBreadthPct())
                .totalSymbols(size(tradableSymbols))
                .preFilterPassedCount(preFilterResult.getPassedCount())
                .strongLongCount(strongLong.size())
                .strongShortCount(strongShort.size())
                .watchlistCount(watchlist.size())
                .eliminatedCount(eliminated.size())
                .strongLong(strongLong)
                .strongShort(strongShort)
                .watchlist(watchlist)
                .eliminated(eliminated)
                .reasons(new ArrayList<>(safeList(marketRegimeResult.getReasonTags())))
                .warnings(new ArrayList<>())
                .build();
    }

    private List<CoinScanResult> filterAndSortByScore(List<CoinScanResult> allResults, CoinClassification classification) {
        return allResults.stream()
                .filter(result -> result != null && result.getClassification() == classification)
                .sorted(Comparator.comparing(this::safeScore).reversed())
                .toList();
    }

    private int safeScore(CoinScanResult result) {
        return Optional.ofNullable(result.getScore()).orElse(0);
    }

    private void addReason(MarketRegimeResult result, ReasonTag reasonTag) {
        List<ReasonTag> reasons = result.getReasonTags() == null
                ? new ArrayList<>()
                : new ArrayList<>(result.getReasonTags());
        if (!reasons.contains(reasonTag)) {
            reasons.add(reasonTag);
        }
        result.setReasonTags(reasons);
    }


    private void requireNotEmpty(List<?> values, String sourceName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(sourceName + " are required for market scan");
        }
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private <T> Map<String, T> toMap(List<T> values, Function<T, String> symbolExtractor) {
        return safeList(values).stream()
                .filter(Objects::nonNull)
                .filter(value -> symbolExtractor.apply(value) != null)
                .collect(Collectors.toMap(symbolExtractor, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }
}
