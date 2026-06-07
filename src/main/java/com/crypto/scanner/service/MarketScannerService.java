package com.crypto.scanner.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.ScanType;
import com.crypto.common.time.IstanbulTimeUtil;
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
import java.util.ArrayList;
import java.util.Collection;
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
    private static final int KLINE_NOT_READY_LOG_LIMIT = 10;
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
        log.info("SCAN_STARTED scanType={} startedAt={}", scanType, IstanbulTimeUtil.format(scanStartTime));

        try {
            List<SymbolInfo> tradableSymbols = symbolUniverseService.loadTradableSymbols();
            requireNotEmpty(tradableSymbols, "tradable symbols");
            int totalSymbols = size(tradableSymbols);
            log.info("SCAN_STAGE_DONE stage=SYMBOL_UNIVERSE total={}", totalSymbols);

            List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
            requireNotEmpty(tickers, "24h tickers");
            List<BookTicker> bookTickers = binanceFuturesClient.getAllBookTickers();
            requireNotEmpty(bookTickers, "book tickers");
            Map<String, Ticker24h> tickerMap = toMap(tickers, Ticker24h::getSymbol);
            Map<String, BookTicker> bookTickerMap = toMap(bookTickers, BookTicker::getSymbol);

            PreFilterResult preFilterResult = preFilterService.apply(tradableSymbols, tickers, bookTickers);
            log.info("SCAN_STAGE_DONE stage=PREFILTER total={} passed={} eliminated={}",
                    preFilterResult.getTotalCount(), preFilterResult.getPassedCount(), preFilterResult.getEliminatedCount());

            KlineLoadResult klineLoadResult = klineService.loadForSymbols(preFilterResult.getPassedSymbols());
            log.info("SCAN_STAGE_DONE stage=KLINES total={} ready={} notReady={}",
                    klineLoadResult.getTotalCount(), klineLoadResult.getReadyCount(), klineLoadResult.getNotReadyCount());
            logKlineNotReadyDetails(klineLoadResult.getNotReadyBundles());

            IndicatorCalculationResult indicatorResult = calculateTechnicalPairs(klineLoadResult.getReadyBundles());
            log.info("SCAN_STAGE_DONE stage=INDICATORS inputBundles={} readyPairs={} failedPairs={}",
                    indicatorResult.inputBundleCount(), indicatorResult.readyPairCount(), indicatorResult.failedPairCount());

            int beforeEnsurePairs = indicatorResult.readyPairCount();
            ensureBenchmarkTechnicalPair(indicatorResult.technicalPairMap(), BTCUSDT);
            ensureBenchmarkTechnicalPair(indicatorResult.technicalPairMap(), ETHUSDT);
            List<TechnicalSnapshotPair> technicalPairs = readyPairs(indicatorResult.technicalPairMap().values());
            log.info("SCAN_STAGE_DONE stage=BTC_ETH_ENSURE beforePairs={} afterPairs={} btcPresent={} ethPresent={}",
                    beforeEnsurePairs, technicalPairs.size(), hasReadyPair(indicatorResult.technicalPairMap().get(BTCUSDT)),
                    hasReadyPair(indicatorResult.technicalPairMap().get(ETHUSDT)));

            BigDecimal marketBreadthPct = marketBreadthService.calculateMarketBreadthPct(technicalPairs);
            log.info("SCAN_STAGE_DONE stage=MARKET_BREADTH breadth={}", marketBreadthPct);
            MarketRegimeResult marketRegimeResult = calculateMarketRegime(
                    indicatorResult.technicalPairMap(), tickerMap, marketBreadthPct);
            log.info("SCAN_STAGE_DONE stage=MARKET_REGIME regime={} breadth={}",
                    marketRegimeResult.getMarketRegime(), marketRegimeResult.getMarketBreadthPct());

            List<TechnicalSnapshotPair> readyScoringPairs = scoringPairs(
                    preFilterResult.getPassedSymbols(), indicatorResult.technicalPairMap());
            List<String> scoringSymbols = readyScoringPairs.stream()
                    .map(TechnicalSnapshotPair::getSymbol)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<String, FuturesSnapshot> futuresMap = loadFuturesData(scoringSymbols);
            log.info("SCAN_STAGE_DONE stage=FUTURES requested={} loaded={}", scoringSymbols.size(), futuresMap.size());

            ScoringResult scoringResult = scorePassedSymbols(
                    preFilterResult.getPassedSymbols(), indicatorResult.technicalPairMap(),
                    klineNotReadyBySymbol(klineLoadResult.getNotReadyBundles()), tickerMap, bookTickerMap, futuresMap,
                    marketRegimeResult, scanStartTime);
            log.info("SCAN_STAGE_DONE stage=SCORING inputPairs={} scored={} skipped={}",
                    size(preFilterResult.getPassedSymbols()), scoringResult.scoredCount(), scoringResult.skippedCount());

            List<CoinScanResult> allResults = new ArrayList<>();
            allResults.addAll(scoringResult.results());
            allResults.addAll(eliminatedPreFilterResults(preFilterResult.getEliminatedDecisions(), scanStartTime));

            MarketScanResult result = buildResult(scanType, scanStartTime, tradableSymbols, preFilterResult,
                    marketRegimeResult, allResults);
            log.info("SCAN_STAGE_DONE stage=RESULT_SPLIT strongLong={} strongShort={} watchlist={} eliminated={}",
                    result.getStrongLongCount(), result.getStrongShortCount(), result.getWatchlistCount(),
                    result.getEliminatedCount());
            logConsistency(totalSymbols, preFilterResult, klineLoadResult, indicatorResult, scoringResult, result);
            log.info("SCAN_COMPLETED scanType={} scanTime={} regime={} strongLong={} strongShort={} watchlist={} eliminated={}",
                    scanType, IstanbulTimeUtil.format(scanStartTime), result.getMarketRegime(), result.getStrongLongCount(), result.getStrongShortCount(),
                    result.getWatchlistCount(), result.getEliminatedCount());
            return result;
        } catch (RuntimeException exception) {
            log.error("SCAN_FAILED scanType={} message={}", scanType, exception.getMessage(), exception);
            throw exception;
        }
    }

    private IndicatorCalculationResult calculateTechnicalPairs(List<KlineBundle> readyBundles) {
        Map<String, TechnicalSnapshotPair> technicalPairMap = new LinkedHashMap<>();
        int inputBundleCount = 0;
        int readyPairCount = 0;
        int failedPairCount = 0;

        for (KlineBundle bundle : safeList(readyBundles)) {
            if (bundle == null || bundle.getSymbol() == null) {
                continue;
            }
            inputBundleCount++;
            try {
                TechnicalSnapshotPair pair = indicatorService.calculatePair(bundle);
                technicalPairMap.put(bundle.getSymbol(), pair);
                if (hasReadyPair(pair)) {
                    readyPairCount++;
                } else {
                    failedPairCount++;
                    log.info("SCAN_INDICATOR_NOT_READY symbol={} reasons={}", bundle.getSymbol(),
                            pair == null ? List.of(ReasonTag.DATA_NOT_READY) : pair.getReasons());
                }
            } catch (RuntimeException exception) {
                failedPairCount++;
                log.warn("SCAN_INDICATOR_FAILED symbol={} message={}", bundle.getSymbol(), exception.getMessage(), exception);
                technicalPairMap.put(bundle.getSymbol(), notReadyTechnicalPair(bundle.getSymbol(),
                        List.of(ReasonTag.DATA_NOT_READY), bundle.getWarnings()));
            }
        }
        return new IndicatorCalculationResult(technicalPairMap, inputBundleCount, readyPairCount, failedPairCount);
    }

    private void ensureBenchmarkTechnicalPair(Map<String, TechnicalSnapshotPair> technicalPairMap, String symbol) {
        TechnicalSnapshotPair existingPair = technicalPairMap.get(symbol);
        if (hasReadyPair(existingPair)) {
            return;
        }

        try {
            KlineBundle benchmarkBundle = klineService.loadForSymbol(symbol);
            TechnicalSnapshotPair benchmarkPair = indicatorService.calculatePair(benchmarkBundle);
            if (!hasReadyPair(benchmarkPair)) {
                log.info("SCAN_INDICATOR_NOT_READY symbol={} reasons={}", symbol,
                        benchmarkPair == null ? List.of(ReasonTag.DATA_NOT_READY) : benchmarkPair.getReasons());
            }
            technicalPairMap.put(symbol, benchmarkPair);
        } catch (RuntimeException exception) {
            log.warn("SCAN_INDICATOR_FAILED symbol={} message={}", symbol, exception.getMessage(), exception);
            technicalPairMap.put(symbol, notReadyTechnicalPair(symbol, List.of(ReasonTag.DATA_NOT_READY), List.of()));
        }
    }

    private TechnicalSnapshotPair notReadyTechnicalPair(
            String symbol,
            List<ReasonTag> reasons,
            List<ReasonTag> warnings
    ) {
        return TechnicalSnapshotPair.builder()
                .symbol(symbol)
                .ready(false)
                .reasons(new ArrayList<>(safeList(reasons)))
                .warnings(new ArrayList<>(safeList(warnings)))
                .build();
    }

    private List<TechnicalSnapshotPair> readyPairs(Collection<TechnicalSnapshotPair> pairs) {
        List<TechnicalSnapshotPair> readyPairs = new ArrayList<>();
        for (TechnicalSnapshotPair pair : pairs) {
            if (hasReadyPair(pair)) {
                readyPairs.add(pair);
            }
        }
        return readyPairs;
    }

    private boolean hasReadyPair(TechnicalSnapshotPair pair) {
        return pair != null && Boolean.TRUE.equals(pair.getReady()) && pair.getSymbol() != null;
    }

    private List<TechnicalSnapshotPair> scoringPairs(
            List<SymbolInfo> passedSymbols,
            Map<String, TechnicalSnapshotPair> technicalPairMap
    ) {
        List<TechnicalSnapshotPair> scoringPairs = new ArrayList<>();
        for (SymbolInfo symbolInfo : safeList(passedSymbols)) {
            if (symbolInfo == null || symbolInfo.getSymbol() == null) {
                continue;
            }
            TechnicalSnapshotPair pair = technicalPairMap.get(symbolInfo.getSymbol());
            if (hasReadyPair(pair)) {
                scoringPairs.add(pair);
            }
        }
        return scoringPairs;
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
        return hasReadyPair(pair) && pair.getOneHour() != null && pair.getFourHour() != null;
    }

    private Map<String, FuturesSnapshot> loadFuturesData(List<String> scoringSymbols) {
        try {
            return futuresDataService.loadForSymbols(scoringSymbols);
        } catch (RuntimeException exception) {
            log.error("FUTURES_LOAD_ERROR message={}", exception.getMessage(), exception);
            return Collections.emptyMap();
        }
    }

    private ScoringResult scorePassedSymbols(
            List<SymbolInfo> passedSymbols,
            Map<String, TechnicalSnapshotPair> technicalPairMap,
            Map<String, KlineBundle> klineNotReadyMap,
            Map<String, Ticker24h> tickerMap,
            Map<String, BookTicker> bookTickerMap,
            Map<String, FuturesSnapshot> futuresMap,
            MarketRegimeResult marketRegimeResult,
            Instant scanStartTime
    ) {
        List<CoinScanResult> results = new ArrayList<>();
        int scoredCount = 0;
        int skippedCount = 0;

        for (SymbolInfo symbolInfo : safeList(passedSymbols)) {
            if (symbolInfo == null || symbolInfo.getSymbol() == null) {
                continue;
            }

            String symbol = symbolInfo.getSymbol();
            TechnicalSnapshotPair pair = technicalPairMap.get(symbol);
            if (!hasReadyPair(pair)) {
                skippedCount++;
                log.info("SCAN_SCORING_SKIPPED symbol={} reason={}", symbol, "MISSING_TECHNICAL_PAIR");
                results.add(missingTechnicalResult(symbol, pair, klineNotReadyMap.get(symbol), scanStartTime));
                continue;
            }

            Ticker24h ticker = tickerMap.get(symbol);
            BookTicker bookTicker = bookTickerMap.get(symbol);
            if (pair.getOneHour() == null || pair.getFourHour() == null || ticker == null || bookTicker == null) {
                skippedCount++;
                log.info("SCAN_SCORING_SKIPPED symbol={} reason={}", symbol, "MISSING_SCORING_INPUT");
                results.add(dataNotReadyResult(symbol, List.of(ReasonTag.DATA_NOT_READY), safeList(pair.getWarnings()),
                        EliminationReason.DATA_NOT_READY, null, null, scanStartTime));
                continue;
            }

            CoinScoringInput input = CoinScoringInput.builder()
                    .symbol(symbol)
                    .oneHour(pair.getOneHour())
                    .fourHour(pair.getFourHour())
                    .ticker24h(ticker)
                    .bookTicker(bookTicker)
                    .futuresSnapshot(futuresMap.get(symbol))
                    .marketRegimeResult(marketRegimeResult)
                    .build();
            if (input == null) {
                skippedCount++;
                log.info("SCAN_SCORING_SKIPPED symbol={} reason={}", symbol, "MISSING_SCORING_INPUT");
                results.add(dataNotReadyResult(symbol, List.of(ReasonTag.DATA_NOT_READY), safeList(pair.getWarnings()),
                        EliminationReason.DATA_NOT_READY, null, null, scanStartTime));
                continue;
            }
            try {
                CoinScanResult result = coinScoringService.score(input);
                results.add(result);
                scoredCount++;
            } catch (RuntimeException exception) {
                skippedCount++;
                log.warn("SCAN_SCORING_SKIPPED symbol={} reason={}", symbol, "SCORING_ERROR", exception);
                results.add(dataNotReadyResult(symbol, List.of(ReasonTag.DATA_NOT_READY), safeList(pair.getWarnings()),
                        EliminationReason.DATA_ERROR, null, null, scanStartTime));
            }
        }
        return new ScoringResult(results, scoredCount, skippedCount);
    }

    private CoinScanResult missingTechnicalResult(
            String symbol,
            TechnicalSnapshotPair pair,
            KlineBundle klineBundle,
            Instant scanStartTime
    ) {
        if (klineBundle != null) {
            return dataNotReadyResult(symbol, klineBundle.getReasons(), klineBundle.getWarnings(),
                    klineBundle.getEliminatedReason(), null, null, scanStartTime);
        }
        if (pair != null) {
            return dataNotReadyResult(symbol, pair.getReasons(), pair.getWarnings(),
                    EliminationReason.DATA_NOT_READY, null, null, scanStartTime);
        }
        return dataNotReadyResult(symbol, List.of(ReasonTag.DATA_NOT_READY), List.of(),
                EliminationReason.DATA_NOT_READY, null, null, scanStartTime);
    }

    private Map<String, KlineBundle> klineNotReadyBySymbol(List<KlineBundle> bundles) {
        return safeList(bundles).stream()
                .filter(Objects::nonNull)
                .filter(bundle -> bundle.getSymbol() != null)
                .collect(Collectors.toMap(KlineBundle::getSymbol, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private void logKlineNotReadyDetails(List<KlineBundle> bundles) {
        safeList(bundles).stream()
                .filter(Objects::nonNull)
                .limit(KLINE_NOT_READY_LOG_LIMIT)
                .forEach(bundle -> log.info("SCAN_KLINE_NOT_READY symbol={} reason={} reasons={}",
                        bundle.getSymbol(), bundle.getEliminatedReason(), bundle.getReasons()));
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
                .riskLevel(RiskLevel.MEDIUM)
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
        List<CoinScanResult> uniqueResults = uniqueBySymbol(allResults);
        List<CoinScanResult> strongLong = filterAndSortByScore(uniqueResults, CoinClassification.STRONG_LONG);
        List<CoinScanResult> strongShort = filterAndSortByScore(uniqueResults, CoinClassification.STRONG_SHORT);
        List<CoinScanResult> watchlist = filterAndSortByScore(uniqueResults, CoinClassification.WATCHLIST);
        List<CoinScanResult> eliminated = uniqueResults.stream()
                .filter(result -> result != null && result.getClassification() == CoinClassification.ELIMINATED)
                .sorted(Comparator.comparing(CoinScanResult::getSymbol, Comparator.nullsLast(String::compareTo)))
                .toList();

        return MarketScanResult.builder()
                .scanRunId(null)
                .scanType(scanType)
                .scanTimeUtc(scanStartTime)
                .scanTimeText(IstanbulTimeUtil.format(scanStartTime))
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

    private List<CoinScanResult> uniqueBySymbol(List<CoinScanResult> results) {
        Map<String, CoinScanResult> resultBySymbol = new LinkedHashMap<>();
        int anonymousIndex = 0;
        for (CoinScanResult result : safeList(results)) {
            if (result == null) {
                continue;
            }
            String key = result.getSymbol() == null ? "__anonymous_" + anonymousIndex++ : result.getSymbol();
            resultBySymbol.putIfAbsent(key, result);
        }
        return new ArrayList<>(resultBySymbol.values());
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

    private void logConsistency(
            int totalSymbols,
            PreFilterResult preFilterResult,
            KlineLoadResult klineLoadResult,
            IndicatorCalculationResult indicatorResult,
            ScoringResult scoringResult,
            MarketScanResult result
    ) {
        int finalListedTotal = result.getStrongLongCount()
                + result.getStrongShortCount()
                + result.getWatchlistCount()
                + result.getEliminatedCount();
        log.info("SCAN_CONSISTENCY totalSymbols={} preFilterPassed={} preFilterEliminated={} klineReady={} klineNotReady={} "
                        + "indicatorReady={} indicatorFailed={} scored={} scoringSkipped={} finalListedTotal={}",
                totalSymbols, preFilterResult.getPassedCount(), preFilterResult.getEliminatedCount(),
                klineLoadResult.getReadyCount(), klineLoadResult.getNotReadyCount(), indicatorResult.readyPairCount(),
                indicatorResult.failedPairCount(), scoringResult.scoredCount(), scoringResult.skippedCount(), finalListedTotal);
        if (finalListedTotal != totalSymbols) {
            log.warn("SCAN_CONSISTENCY_WARNING totalSymbols={} finalListedTotal={} difference={}",
                    totalSymbols, finalListedTotal, totalSymbols - finalListedTotal);
        }
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

    private record IndicatorCalculationResult(
            Map<String, TechnicalSnapshotPair> technicalPairMap,
            int inputBundleCount,
            int readyPairCount,
            int failedPairCount
    ) {
    }

    private record ScoringResult(List<CoinScanResult> results, int scoredCount, int skippedCount) {
    }
}
