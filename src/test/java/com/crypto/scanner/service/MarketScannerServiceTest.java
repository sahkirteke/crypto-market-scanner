package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.TechnicalSnapshotPair;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.model.CoinScoringInput;
import com.crypto.scanner.model.MarketRegimeResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketScannerServiceTest {
    private SymbolUniverseService symbolUniverseService;
    private BinanceFuturesClient binanceFuturesClient;
    private PreFilterService preFilterService;
    private KlineService klineService;
    private IndicatorService indicatorService;
    private MarketBreadthService marketBreadthService;
    private MarketRegimeService marketRegimeService;
    private FuturesDataService futuresDataService;
    private CoinScoringService coinScoringService;
    private MarketScannerService marketScannerService;

    @BeforeEach
    void setUp() {
        symbolUniverseService = mock(SymbolUniverseService.class);
        binanceFuturesClient = mock(BinanceFuturesClient.class);
        preFilterService = mock(PreFilterService.class);
        klineService = mock(KlineService.class);
        indicatorService = mock(IndicatorService.class);
        marketBreadthService = mock(MarketBreadthService.class);
        marketRegimeService = mock(MarketRegimeService.class);
        futuresDataService = mock(FuturesDataService.class);
        coinScoringService = mock(CoinScoringService.class);

        marketScannerService = new MarketScannerService(
                symbolUniverseService,
                binanceFuturesClient,
                preFilterService,
                klineService,
                indicatorService,
                marketBreadthService,
                marketRegimeService,
                futuresDataService,
                coinScoringService);
    }

    @Test
    void runScanReturnsMarketScanResultForMainFlow() {
        stubBaseFlow(
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT")),
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT")),
                List.of(eliminatedDecision("SOLUSDT", EliminationReason.LOW_VOLUME)),
                List.of(readyBundle("BTCUSDT"), readyBundle("ETHUSDT")),
                List.of(readyPair("BTCUSDT"), readyPair("ETHUSDT")),
                List.of(scored("BTCUSDT", CoinClassification.STRONG_LONG, 90),
                        scored("ETHUSDT", CoinClassification.WATCHLIST, 55)));

        MarketScanResult result = marketScannerService.runScan(ScanType.FOUR_HOUR);

        assertThat(result.getTotalSymbols()).isEqualTo(3);
        assertThat(result.getPreFilterPassedCount()).isEqualTo(2);
        assertThat(result.getStrongLongCount()).isEqualTo(1);
        assertThat(result.getWatchlistCount()).isEqualTo(1);
        assertThat(result.getEliminatedCount()).isEqualTo(1);
        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.RISK_ON);
        assertThat(result.getScanType()).isEqualTo(ScanType.FOUR_HOUR);
    }

    @Test
    void eliminatedPreFilterDecisionIsIncludedInMarketScanResult() {
        stubBaseFlow(
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("XRPUSDT")),
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT")),
                List.of(eliminatedDecision("XRPUSDT", EliminationReason.HIGH_SPREAD)),
                List.of(readyBundle("BTCUSDT"), readyBundle("ETHUSDT")),
                List.of(readyPair("BTCUSDT"), readyPair("ETHUSDT")),
                List.of(scored("BTCUSDT", CoinClassification.WATCHLIST, 60),
                        scored("ETHUSDT", CoinClassification.WATCHLIST, 50)));

        MarketScanResult result = marketScannerService.runScan(ScanType.ONE_HOUR);

        assertThat(result.getEliminated())
                .anySatisfy(eliminated -> {
                    assertThat(eliminated.getSymbol()).isEqualTo("XRPUSDT");
                    assertThat(eliminated.getEliminatedReason()).isEqualTo(EliminationReason.HIGH_SPREAD);
                    assertThat(eliminated.getQuoteVolume24h()).isEqualByComparingTo("1000");
                    assertThat(eliminated.getSpreadPct()).isEqualByComparingTo("1.5");
                    assertThat(eliminated.getPriceChange24hPct()).isEqualByComparingTo("2.5");
                });
    }

    @Test
    void missingBtcAndEthTechnicalPairsAreLoadedWithoutFailingScan() {
        stubBaseFlow(
                List.of(symbol("SOLUSDT"), symbol("ADAUSDT"), symbol("XRPUSDT")),
                List.of(symbol("SOLUSDT"), symbol("ADAUSDT")),
                List.of(eliminatedDecision("XRPUSDT", EliminationReason.LOW_VOLUME)),
                List.of(readyBundle("SOLUSDT"), readyBundle("ADAUSDT")),
                List.of(readyPair("SOLUSDT"), readyPair("ADAUSDT")),
                List.of(scored("SOLUSDT", CoinClassification.WATCHLIST, 50),
                        scored("ADAUSDT", CoinClassification.WATCHLIST, 45),
                        scored("BTCUSDT", CoinClassification.WATCHLIST, 40),
                        scored("ETHUSDT", CoinClassification.WATCHLIST, 35)));
        KlineBundle btcBenchmarkBundle = readyBundle("BTCUSDT");
        KlineBundle ethBenchmarkBundle = readyBundle("ETHUSDT");
        when(klineService.loadForSymbol("BTCUSDT")).thenReturn(btcBenchmarkBundle);
        when(klineService.loadForSymbol("ETHUSDT")).thenReturn(ethBenchmarkBundle);
        when(indicatorService.calculatePair(btcBenchmarkBundle)).thenReturn(readyPair("BTCUSDT"));
        when(indicatorService.calculatePair(ethBenchmarkBundle)).thenReturn(readyPair("ETHUSDT"));

        MarketScanResult result = marketScannerService.runScan(ScanType.FOUR_HOUR);

        assertThat(result.getWatchlistCount()).isEqualTo(2);
        assertThat(finalListedTotal(result)).isEqualTo(result.getTotalSymbols());
        verify(klineService).loadForSymbol("BTCUSDT");
        verify(klineService).loadForSymbol("ETHUSDT");
    }

    @Test
    void classificationListsAreSeparatedCorrectly() {
        stubBaseFlow(
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT"), symbol("ADAUSDT")),
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT"), symbol("ADAUSDT")),
                List.of(),
                List.of(readyBundle("BTCUSDT"), readyBundle("ETHUSDT"), readyBundle("SOLUSDT"), readyBundle("ADAUSDT")),
                List.of(readyPair("BTCUSDT"), readyPair("ETHUSDT"), readyPair("SOLUSDT"), readyPair("ADAUSDT")),
                List.of(scored("BTCUSDT", CoinClassification.STRONG_LONG, 90),
                        scored("ETHUSDT", CoinClassification.STRONG_SHORT, 88),
                        scored("SOLUSDT", CoinClassification.WATCHLIST, 60),
                        scored("ADAUSDT", CoinClassification.ELIMINATED, 0)));

        MarketScanResult result = marketScannerService.runScan(ScanType.FOUR_HOUR);

        assertThat(result.getStrongLong()).extracting(CoinScanResult::getSymbol).containsExactly("BTCUSDT");
        assertThat(result.getStrongShort()).extracting(CoinScanResult::getSymbol).containsExactly("ETHUSDT");
        assertThat(result.getWatchlist()).extracting(CoinScanResult::getSymbol).containsExactly("SOLUSDT");
        assertThat(result.getEliminated()).extracting(CoinScanResult::getSymbol).containsExactly("ADAUSDT");
    }

    @Test
    void resultListsAreSortedByScoreAndSymbol() {
        stubBaseFlow(
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT"), symbol("ADAUSDT"), symbol("BNBUSDT"),
                        symbol("AVAXUSDT"), symbol("XRPUSDT")),
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT"), symbol("ADAUSDT"), symbol("BNBUSDT")),
                List.of(eliminatedDecision("XRPUSDT", EliminationReason.HIGH_SPREAD),
                        eliminatedDecision("AVAXUSDT", EliminationReason.LOW_VOLUME)),
                List.of(readyBundle("BTCUSDT"), readyBundle("ETHUSDT"), readyBundle("SOLUSDT"), readyBundle("ADAUSDT"),
                        readyBundle("BNBUSDT")),
                List.of(readyPair("BTCUSDT"), readyPair("ETHUSDT"), readyPair("SOLUSDT"), readyPair("ADAUSDT"),
                        readyPair("BNBUSDT")),
                List.of(scored("BTCUSDT", CoinClassification.STRONG_LONG, 70),
                        scored("ETHUSDT", CoinClassification.STRONG_LONG, 95),
                        scored("SOLUSDT", CoinClassification.STRONG_SHORT, 80),
                        scored("ADAUSDT", CoinClassification.STRONG_SHORT, 90),
                        scored("BNBUSDT", CoinClassification.WATCHLIST, 45)));

        MarketScanResult result = marketScannerService.runScan(ScanType.FOUR_HOUR);

        assertThat(result.getStrongLong()).extracting(CoinScanResult::getSymbol).containsExactly("ETHUSDT", "BTCUSDT");
        assertThat(result.getStrongShort()).extracting(CoinScanResult::getSymbol).containsExactly("ADAUSDT", "SOLUSDT");
        assertThat(result.getWatchlist()).extracting(CoinScanResult::getSymbol).containsExactly("BNBUSDT");
        assertThat(result.getEliminated()).extracting(CoinScanResult::getSymbol).containsExactly("AVAXUSDT", "XRPUSDT");
    }

    @Test
    void klineNotReadyPassedSymbolIsIncludedInEliminatedResults() {
        KlineBundle solNotReady = notReadyBundle("SOLUSDT", EliminationReason.DATA_NOT_READY);
        stubBaseFlow(
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT"), symbol("XRPUSDT")),
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT")),
                List.of(eliminatedDecision("XRPUSDT", EliminationReason.LOW_VOLUME)),
                List.of(readyBundle("BTCUSDT"), readyBundle("ETHUSDT")),
                List.of(solNotReady),
                List.of(readyPair("BTCUSDT"), readyPair("ETHUSDT")),
                List.of(scored("BTCUSDT", CoinClassification.STRONG_LONG, 90),
                        scored("ETHUSDT", CoinClassification.WATCHLIST, 55)));

        MarketScanResult result = marketScannerService.runScan(ScanType.FOUR_HOUR);

        assertThat(result.getStrongLongCount() + result.getWatchlistCount()).isEqualTo(2);
        assertThat(result.getEliminated()).extracting(CoinScanResult::getSymbol).contains("SOLUSDT", "XRPUSDT");
        assertThat(result.getEliminated())
                .filteredOn(eliminated -> "SOLUSDT".equals(eliminated.getSymbol()))
                .first()
                .satisfies(eliminated -> assertThat(eliminated.getEliminatedReason())
                        .isEqualTo(EliminationReason.DATA_NOT_READY));
        assertThat(finalListedTotal(result)).isEqualTo(result.getTotalSymbols());
    }

    @Test
    void indicatorExceptionDoesNotFailScanAndAddsEliminatedResult() {
        KlineBundle solReady = readyBundle("SOLUSDT");
        stubBaseFlow(
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT"), symbol("XRPUSDT")),
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT")),
                List.of(eliminatedDecision("XRPUSDT", EliminationReason.LOW_VOLUME)),
                List.of(readyBundle("BTCUSDT"), readyBundle("ETHUSDT"), solReady),
                List.of(),
                List.of(readyPair("BTCUSDT"), readyPair("ETHUSDT"), readyPair("SOLUSDT")),
                List.of(scored("BTCUSDT", CoinClassification.STRONG_LONG, 90),
                        scored("ETHUSDT", CoinClassification.WATCHLIST, 55)));
        when(indicatorService.calculatePair(solReady)).thenThrow(new IllegalStateException("indicator failed"));

        MarketScanResult result = marketScannerService.runScan(ScanType.FOUR_HOUR);

        assertThat(result.getEliminated()).extracting(CoinScanResult::getSymbol).contains("SOLUSDT", "XRPUSDT");
        assertThat(result.getEliminated())
                .filteredOn(eliminated -> "SOLUSDT".equals(eliminated.getSymbol()))
                .first()
                .satisfies(eliminated -> {
                    assertThat(eliminated.getEliminatedReason()).isIn(
                            EliminationReason.DATA_NOT_READY, EliminationReason.DATA_ERROR);
                    assertThat(eliminated.getReasons()).contains(ReasonTag.DATA_NOT_READY);
                });
        assertThat(finalListedTotal(result)).isEqualTo(result.getTotalSymbols());
    }

    @Test
    void scoringExceptionDoesNotFailScanAndAddsEliminatedResult() {
        stubBaseFlow(
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT"), symbol("XRPUSDT")),
                List.of(symbol("BTCUSDT"), symbol("ETHUSDT"), symbol("SOLUSDT")),
                List.of(eliminatedDecision("XRPUSDT", EliminationReason.LOW_VOLUME)),
                List.of(readyBundle("BTCUSDT"), readyBundle("ETHUSDT"), readyBundle("SOLUSDT")),
                List.of(),
                List.of(readyPair("BTCUSDT"), readyPair("ETHUSDT"), readyPair("SOLUSDT")),
                List.of(scored("BTCUSDT", CoinClassification.STRONG_LONG, 90),
                        scored("ETHUSDT", CoinClassification.WATCHLIST, 55)));
        when(coinScoringService.score(argThat(input -> input != null && "SOLUSDT".equals(input.getSymbol()))))
                .thenThrow(new IllegalStateException("scoring failed"));

        MarketScanResult result = marketScannerService.runScan(ScanType.FOUR_HOUR);

        assertThat(result.getEliminated()).extracting(CoinScanResult::getSymbol).contains("SOLUSDT", "XRPUSDT");
        assertThat(result.getEliminated())
                .filteredOn(eliminated -> "SOLUSDT".equals(eliminated.getSymbol()))
                .first()
                .satisfies(eliminated -> assertThat(eliminated.getEliminatedReason())
                        .isEqualTo(EliminationReason.DATA_ERROR));
        verify(coinScoringService, never()).score(isNull());
        assertThat(finalListedTotal(result)).isEqualTo(result.getTotalSymbols());
    }

    private void stubBaseFlow(
            List<SymbolInfo> tradableSymbols,
            List<SymbolInfo> passedSymbols,
            List<FilterDecision> eliminatedDecisions,
            List<KlineBundle> readyBundles,
            List<TechnicalSnapshotPair> technicalPairs,
            List<CoinScanResult> scoredResults
    ) {
        stubBaseFlow(tradableSymbols, passedSymbols, eliminatedDecisions, readyBundles, List.of(), technicalPairs, scoredResults);
    }

    private void stubBaseFlow(
            List<SymbolInfo> tradableSymbols,
            List<SymbolInfo> passedSymbols,
            List<FilterDecision> eliminatedDecisions,
            List<KlineBundle> readyBundles,
            List<KlineBundle> notReadyBundles,
            List<TechnicalSnapshotPair> technicalPairs,
            List<CoinScanResult> scoredResults
    ) {
        when(symbolUniverseService.loadTradableSymbols()).thenReturn(tradableSymbols);
        when(binanceFuturesClient.getAll24hTickers()).thenReturn(tickersFor(tradableSymbols));
        when(binanceFuturesClient.getAllBookTickers()).thenReturn(bookTickersFor(tradableSymbols));
        when(preFilterService.apply(anyList(), anyList(), anyList())).thenReturn(PreFilterResult.builder()
                .passedSymbols(passedSymbols)
                .eliminatedDecisions(eliminatedDecisions)
                .totalCount(tradableSymbols.size())
                .passedCount(passedSymbols.size())
                .eliminatedCount(eliminatedDecisions.size())
                .build());
        when(klineService.loadForSymbols(passedSymbols)).thenReturn(KlineLoadResult.builder()
                .readyBundles(readyBundles)
                .notReadyBundles(notReadyBundles)
                .totalCount(readyBundles.size() + notReadyBundles.size())
                .readyCount(readyBundles.size())
                .notReadyCount(notReadyBundles.size())
                .build());
        for (int i = 0; i < readyBundles.size(); i++) {
            when(indicatorService.calculatePair(readyBundles.get(i))).thenReturn(technicalPairs.get(i));
        }
        when(marketBreadthService.calculateMarketBreadthPct(anyList())).thenReturn(BigDecimal.valueOf(50));
        when(marketRegimeService.calculateBtcFourHourChangePct(any())).thenReturn(BigDecimal.ONE);
        when(marketRegimeService.calculate(any(), any(), any(), any(), eq(BigDecimal.valueOf(50)), eq(BigDecimal.ONE)))
                .thenReturn(MarketRegimeResult.builder()
                        .marketRegime(MarketRegime.RISK_ON)
                        .marketBreadthPct(BigDecimal.valueOf(50))
                        .reasonTags(List.of(ReasonTag.MARKET_RISK_ON))
                        .build());
        when(futuresDataService.loadForSymbols(anyList())).thenReturn(Map.of(
                "BTCUSDT", FuturesSnapshot.builder().symbol("BTCUSDT").build(),
                "ETHUSDT", FuturesSnapshot.builder().symbol("ETHUSDT").build()));

        when(coinScoringService.score(argThat(input -> input != null))).thenAnswer(invocation -> {
            CoinScoringInput input = invocation.getArgument(0, CoinScoringInput.class);
            String symbol = input.getSymbol();
            return scoredResults.stream()
                    .filter(result -> result.getSymbol().equals(symbol))
                    .findFirst()
                    .orElse(scored(symbol, CoinClassification.WATCHLIST, 50));
        });
    }

    private SymbolInfo symbol(String symbol) {
        return SymbolInfo.builder().symbol(symbol).build();
    }

    private Ticker24h ticker(String symbol) {
        return Ticker24h.builder()
                .symbol(symbol)
                .lastPrice(BigDecimal.valueOf(100))
                .priceChangePercent(BigDecimal.ONE)
                .quoteVolume(BigDecimal.valueOf(1_000_000))
                .build();
    }

    private List<Ticker24h> tickersFor(List<SymbolInfo> symbols) {
        return symbols.stream()
                .map(SymbolInfo::getSymbol)
                .map(this::ticker)
                .toList();
    }

    private List<BookTicker> bookTickersFor(List<SymbolInfo> symbols) {
        return symbols.stream()
                .map(SymbolInfo::getSymbol)
                .map(symbol -> BookTicker.builder().symbol(symbol).spreadPct(new BigDecimal("0.01")).build())
                .toList();
    }

    private FilterDecision eliminatedDecision(String symbol, EliminationReason reason) {
        return FilterDecision.builder()
                .symbol(symbol)
                .eliminatedReason(reason)
                .quoteVolume24h(BigDecimal.valueOf(1000))
                .spreadPct(new BigDecimal("1.5"))
                .priceChange24hPct(new BigDecimal("2.5"))
                .reasons(List.of(ReasonTag.LOW_VOLUME))
                .warnings(List.of())
                .build();
    }

    private KlineBundle readyBundle(String symbol) {
        return KlineBundle.builder().symbol(symbol).ready(true).build();
    }

    private KlineBundle notReadyBundle(String symbol, EliminationReason reason) {
        return KlineBundle.builder()
                .symbol(symbol)
                .ready(false)
                .eliminatedReason(reason)
                .reasons(List.of(ReasonTag.DATA_NOT_READY))
                .build();
    }

    private int finalListedTotal(MarketScanResult result) {
        return result.getStrongLongCount()
                + result.getStrongShortCount()
                + result.getWatchlistCount()
                + result.getEliminatedCount();
    }

    private TechnicalSnapshotPair readyPair(String symbol) {
        TechnicalSnapshot oneHour = TechnicalSnapshot.builder()
                .symbol(symbol)
                .close(BigDecimal.valueOf(101))
                .previousClose(BigDecimal.valueOf(100))
                .volumeRatio(BigDecimal.ONE)
                .build();
        TechnicalSnapshot fourHour = TechnicalSnapshot.builder()
                .symbol(symbol)
                .close(BigDecimal.valueOf(102))
                .previousClose(BigDecimal.valueOf(100))
                .ema20(BigDecimal.valueOf(99))
                .macdHist(BigDecimal.ONE)
                .build();
        return TechnicalSnapshotPair.builder()
                .symbol(symbol)
                .oneHour(oneHour)
                .fourHour(fourHour)
                .ready(true)
                .build();
    }

    private CoinScanResult scored(String symbol, CoinClassification classification, int score) {
        return CoinScanResult.builder()
                .symbol(symbol)
                .classification(classification)
                .directionBias(DirectionBias.NEUTRAL)
                .score(score)
                .longScore(score)
                .shortScore(0)
                .eliminatedReason(classification == CoinClassification.ELIMINATED
                        ? EliminationReason.SCORE_BELOW_THRESHOLD
                        : EliminationReason.NONE)
                .build();
    }
}
