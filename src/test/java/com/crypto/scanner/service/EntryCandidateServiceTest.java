package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.MarketScanResult;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.entity.MarketScanRunEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.crypto.persistence.repository.CoinScanResultRepository;
import com.crypto.persistence.repository.MarketScanRunRepository;
import com.crypto.scanner.config.ScannerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntryCandidateServiceTest {
    private ScannerProperties scannerProperties;
    private MarketScanRunRepository marketScanRunRepository;
    private CoinScanResultRepository coinScanResultRepository;
    private EntryCandidateService entryCandidateService;

    @BeforeEach
    void setUp() {
        scannerProperties = new ScannerProperties();
        marketScanRunRepository = mock(MarketScanRunRepository.class);
        coinScanResultRepository = mock(CoinScanResultRepository.class);
        entryCandidateService = new EntryCandidateService(
                scannerProperties,
                marketScanRunRepository,
                coinScanResultRepository,
                new JsonTextMapper(new ObjectMapper()),
                new EntryPriorityService(scannerProperties)
        );
    }

    @Test
    void strongLongWithLongDirectionBecomesLongCandidate() {
        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(
                coin("BTCUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 90, RiskLevel.LOW)
        ), List.of(), List.of()));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getSide()).isEqualTo(PositionSide.LONG);
        assertThat(candidates.getFirst().getCandidateReason()).isEqualTo("STRONG_LONG_CANDIDATE");
    }

    @Test
    void strongShortWithShortDirectionBecomesShortCandidate() {
        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(), List.of(
                coin("ETHUSDT", CoinClassification.STRONG_SHORT, DirectionBias.SHORT, 90, RiskLevel.LOW)
        ), List.of()));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getSide()).isEqualTo(PositionSide.SHORT);
        assertThat(candidates.getFirst().getCandidateReason()).isEqualTo("STRONG_SHORT_CANDIDATE");
    }

    @Test
    void watchlistWithShortDirectionDoesNotBecomeCandidate() {
        scannerProperties.getEntryCandidate().setAllowWatchlist(true);

        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(), List.of(), List.of(
                coin("SOLUSDT", CoinClassification.WATCHLIST, DirectionBias.SHORT, 75, RiskLevel.LOW)
        )));

        assertThat(candidates).isEmpty();
    }

    @Test
    void neutralWatchlistIsBlockedByDefault() {
        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(), List.of(), List.of(
                coin("ADAUSDT", CoinClassification.WATCHLIST, DirectionBias.NEUTRAL, 75, RiskLevel.LOW)
        )));

        assertThat(candidates).isEmpty();
    }

    @Test
    void highRiskIsBlockedByDefault() {
        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(
                coin("BNBUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 90, RiskLevel.HIGH)
        ), List.of(), List.of()));

        assertThat(candidates).hasSize(1);
    }

    @Test
    void longCrowdedWarningBlocksLongCandidate() {
        CoinScanResult coin = coin("XRPUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 90, RiskLevel.LOW);
        coin.setWarnings(List.of(ReasonTag.LONG_CROWDED));

        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(coin), List.of(), List.of()));

        assertThat(candidates).hasSize(1);
    }

    @Test
    void shortCrowdedWarningBlocksShortCandidate() {
        CoinScanResult coin = coin("DOGEUSDT", CoinClassification.STRONG_SHORT, DirectionBias.SHORT, 90, RiskLevel.LOW);
        coin.setWarnings(List.of(ReasonTag.SHORT_CROWDED));

        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(), List.of(coin), List.of()));

        assertThat(candidates).hasSize(1);
    }

    @Test
    void rsiOverboughtWarningBlocksLongCandidate() {
        CoinScanResult coin = coin("LINKUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 90, RiskLevel.LOW);
        coin.setWarnings(List.of(ReasonTag.RSI_OVERBOUGHT));

        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(coin), List.of(), List.of()));

        assertThat(candidates).hasSize(1);
    }

    @Test
    void shortExtremeOversoldRiskWarningBlocksShortCandidate() {
        CoinScanResult coin = coin("AVAXUSDT", CoinClassification.STRONG_SHORT, DirectionBias.SHORT, 90, RiskLevel.LOW);
        coin.setWarnings(List.of(ReasonTag.SHORT_EXTREME_OVERSOLD_RISK));

        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(List.of(), List.of(coin), List.of()));

        assertThat(candidates).hasSize(1);
    }

    @Test
    void maxLongCandidatesAndMaxShortCandidatesLimitsAreApplied() {
        scannerProperties.getEntryCandidate().setMaxLongCandidates(1);
        scannerProperties.getEntryCandidate().setMaxShortCandidates(1);
        scannerProperties.getEntryCandidate().setMaxCandidates(10);

        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(
                List.of(
                        coin("BTCUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 95, RiskLevel.LOW),
                        coin("ETHUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 94, RiskLevel.LOW)
                ),
                List.of(
                        coin("SOLUSDT", CoinClassification.STRONG_SHORT, DirectionBias.SHORT, 93, RiskLevel.LOW),
                        coin("ADAUSDT", CoinClassification.STRONG_SHORT, DirectionBias.SHORT, 92, RiskLevel.LOW)
                ),
                List.of()
        ));

        assertThat(candidates).hasSize(2);
        assertThat(candidates).filteredOn(candidate -> candidate.getSide() == PositionSide.LONG).hasSize(1);
        assertThat(candidates).filteredOn(candidate -> candidate.getSide() == PositionSide.SHORT).hasSize(1);
    }

    @Test
    void maxCandidatesLimitIsAppliedAfterSideLimits() {
        scannerProperties.getEntryCandidate().setMaxLongCandidates(5);
        scannerProperties.getEntryCandidate().setMaxShortCandidates(5);
        scannerProperties.getEntryCandidate().setMaxCandidates(2);

        List<EntryCandidate> candidates = entryCandidateService.selectCandidates(scanResult(
                List.of(
                        coin("BTCUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 95, RiskLevel.LOW),
                        coin("ETHUSDT", CoinClassification.STRONG_LONG, DirectionBias.LONG, 94, RiskLevel.LOW)
                ),
                List.of(coin("SOLUSDT", CoinClassification.STRONG_SHORT, DirectionBias.SHORT, 93, RiskLevel.LOW)),
                List.of()
        ));

        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting(EntryCandidate::getSymbol).containsExactly("BTCUSDT", "ETHUSDT");
    }

    @Test
    void selectCandidatesFromLatestScanReturnsEmptyListWhenNoCompletedScanExists() {
        when(marketScanRunRepository.findTopByStatusOrderByScanTimeUtcDesc("COMPLETED"))
                .thenReturn(Optional.empty());

        List<EntryCandidate> candidates = entryCandidateService.selectCandidatesFromLatestScan();

        assertThat(candidates).isEmpty();
    }


    @Test
    void selectCandidatesFromScanRunUsesScanRunSnapshotWithoutReadingResultLazyRelation() {
        Long scanRunId = 42L;
        MarketScanRunEntity scanRun = scanRun(scanRunId);
        CoinScanResultEntity result = coinEntity(
                "BTCUSDT",
                CoinClassification.STRONG_LONG,
                DirectionBias.LONG,
                91,
                RiskLevel.LOW
        );
        result.setScanRun(null);
        when(marketScanRunRepository.findById(scanRunId)).thenReturn(Optional.of(scanRun));
        when(coinScanResultRepository.findByScanRunIdWithScanRun(scanRunId)).thenReturn(List.of(result));

        List<EntryCandidate> candidates = entryCandidateService.selectCandidatesFromScanRun(scanRunId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getScanRunId()).isEqualTo(scanRunId);
        assertThat(candidates.getFirst().getMarketRegime()).isEqualTo(MarketRegime.RISK_ON);
        assertThat(candidates.getFirst().getMarketBreadthPct()).isEqualByComparingTo("62.5");
    }

    @Test
    void selectCandidatesFromScanRunReturnsEmptyWhenScanRunDoesNotExist() {
        when(marketScanRunRepository.findById(404L)).thenReturn(Optional.empty());

        List<EntryCandidate> candidates = entryCandidateService.selectCandidatesFromScanRun(404L);

        assertThat(candidates).isEmpty();
    }

    private MarketScanResult scanResult(
            List<CoinScanResult> strongLong,
            List<CoinScanResult> strongShort,
            List<CoinScanResult> watchlist
    ) {
        return MarketScanResult.builder()
                .strongLong(strongLong)
                .strongShort(strongShort)
                .watchlist(watchlist)
                .build();
    }

    private MarketScanRunEntity scanRun(Long scanRunId) {
        MarketScanRunEntity scanRun = new MarketScanRunEntity();
        scanRun.setId(scanRunId);
        scanRun.setMarketRegime(MarketRegime.RISK_ON);
        scanRun.setMarketBreadthPct(new BigDecimal("62.5"));
        scanRun.setScanTimeUtc(Instant.parse("2026-06-07T21:02:00Z"));
        return scanRun;
    }

    private CoinScanResultEntity coinEntity(
            String symbol,
            CoinClassification classification,
            DirectionBias directionBias,
            int score,
            RiskLevel riskLevel
    ) {
        CoinScanResultEntity entity = new CoinScanResultEntity();
        entity.setSymbol(symbol);
        entity.setClassification(classification);
        entity.setDirectionBias(directionBias);
        entity.setScore(score);
        entity.setLongScore(score);
        entity.setShortScore(score);
        entity.setRiskLevel(riskLevel);
        entity.setLastPrice(new BigDecimal("100"));
        entity.setQuoteVolume24h(new BigDecimal("1000000"));
        entity.setSpreadPct(new BigDecimal("0.02"));
        entity.setMarketBreadthPct(null);
        entity.setReasonsJson("[\"MARKET_BREADTH_OK\"]");
        entity.setWarningsJson("[]");
        entity.setCreatedAt(Instant.parse("2026-06-07T21:02:05Z"));
        return entity;
    }

    private CoinScanResult coin(
            String symbol,
            CoinClassification classification,
            DirectionBias directionBias,
            int score,
            RiskLevel riskLevel
    ) {
        return CoinScanResult.builder()
                .symbol(symbol)
                .classification(classification)
                .directionBias(directionBias)
                .score(score)
                .longScore(score)
                .shortScore(score)
                .riskLevel(riskLevel)
                .quoteVolume24h(BigDecimal.valueOf(Math.abs(symbol.hashCode())))
                .reasons(List.of(ReasonTag.MARKET_BREADTH_OK))
                .warnings(List.of())
                .build();
    }
}
