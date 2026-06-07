package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.EntrySignal;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntrySignalServiceTest {
    private ScannerProperties scannerProperties;
    private EntryCandidateService entryCandidateService;
    private EntrySignalService entrySignalService;

    @BeforeEach
    void setUp() {
        scannerProperties = new ScannerProperties();
        entryCandidateService = mock(EntryCandidateService.class);
        entrySignalService = new EntrySignalService(scannerProperties, entryCandidateService);
    }

    @Test
    void strongShortCandidateWithScore90ProducesEnterShort() {
        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "INJUSDT",
                PositionSide.SHORT,
                CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT,
                90,
                RiskLevel.LOW
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
        assertThat(signal.getSignalReason()).isEqualTo("STRONG_SHORT_ENTRY");
    }

    @Test
    void watchlistLongCandidateWithScore75ProducesEnterLongWhenWatchlistAllowed() {
        scannerProperties.getEntrySignal().setAllowWatchlistEntry(true);

        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "SOLUSDT",
                PositionSide.LONG,
                CoinClassification.WATCHLIST,
                DirectionBias.LONG,
                75,
                RiskLevel.LOW
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
        assertThat(signal.getSignalReason()).isEqualTo("WATCHLIST_LONG_ENTRY");
    }

    @Test
    void watchlistCandidateProducesNoEntryWhenWatchlistEntryDisabled() {
        scannerProperties.getEntrySignal().setAllowWatchlistEntry(false);

        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "SOLUSDT",
                PositionSide.LONG,
                CoinClassification.WATCHLIST,
                DirectionBias.LONG,
                75,
                RiskLevel.LOW
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("WATCHLIST_ENTRY_DISABLED");
    }

    @Test
    void scoreBelowMinEnterScoreProducesNoEntry() {
        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "BTCUSDT",
                PositionSide.LONG,
                CoinClassification.WATCHLIST,
                DirectionBias.LONG,
                74,
                RiskLevel.LOW
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("ENTRY_SCORE_TOO_LOW");
    }

    @Test
    void strongCandidateBelowMinStrongEnterScoreProducesNoEntry() {
        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "BTCUSDT",
                PositionSide.LONG,
                CoinClassification.STRONG_LONG,
                DirectionBias.LONG,
                79,
                RiskLevel.LOW
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("STRONG_SCORE_TOO_LOW");
    }

    @Test
    void highRiskCandidateProducesNoEntryWhenHighRiskEntryDisabled() {
        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "BTCUSDT",
                PositionSide.LONG,
                CoinClassification.STRONG_LONG,
                DirectionBias.LONG,
                90,
                RiskLevel.HIGH
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("HIGH_RISK_BLOCKED");
    }

    @Test
    void mediumRiskCandidateProducesNoEntryWhenMediumRiskEntryDisabled() {
        scannerProperties.getEntrySignal().setAllowMediumRiskEntry(false);

        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "BTCUSDT",
                PositionSide.LONG,
                CoinClassification.STRONG_LONG,
                DirectionBias.LONG,
                90,
                RiskLevel.MEDIUM
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("MEDIUM_RISK_BLOCKED");
    }

    @Test
    void spreadAboveMaxProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setSpreadPct(new BigDecimal("0.09"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("SPREAD_TOO_HIGH");
    }

    @Test
    void quoteVolumeBelowMinProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setQuoteVolume24h(new BigDecimal("29999999"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("VOLUME_TOO_LOW");
    }

    @Test
    void marketChopProducesNoEntryWhenBlockMarketChopEnabled() {
        scannerProperties.getEntrySignal().setBlockMarketChop(true);
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setWarnings(List.of(ReasonTag.MARKET_CHOP));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("MARKET_CHOP_BLOCKED");
    }

    @Test
    void missingVolumeConfirmedProducesNoEntryWhenVolumeConfirmedRequired() {
        scannerProperties.getEntrySignal().setRequireVolumeConfirmed(true);
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setReasons(List.of(ReasonTag.MARKET_BREADTH_OK));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("VOLUME_NOT_CONFIRMED");
    }

    @Test
    void longAboveMax24hChangeProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("18.01"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_TOO_PUMPED");
    }

    @Test
    void shortBelowMax24hDumpProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("-18.01"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("SHORT_TOO_DUMPED");
    }

    @Test
    void longCrowdedWarningBlocksLongCandidate() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setWarnings(List.of(ReasonTag.LONG_CROWDED));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_CROWDED_BLOCKED");
    }

    @Test
    void shortCrowdedWarningBlocksShortCandidate() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setWarnings(List.of(ReasonTag.SHORT_CROWDED));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("SHORT_CROWDED_BLOCKED");
    }

    @Test
    void generateSignalsFromLatestScanCallsEntryCandidateService() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        when(entryCandidateService.selectCandidatesFromLatestScan()).thenReturn(List.of(candidate));

        List<EntrySignal> signals = entrySignalService.generateSignalsFromLatestScan();

        assertThat(signals).hasSize(1);
        verify(entryCandidateService).selectCandidatesFromLatestScan();
    }

    private EntryCandidate candidate(
            String symbol,
            PositionSide side,
            CoinClassification classification,
            DirectionBias directionBias,
            int score,
            RiskLevel riskLevel
    ) {
        return EntryCandidate.builder()
                .symbol(symbol)
                .side(side)
                .score(score)
                .longScore(side == PositionSide.LONG ? score : 50)
                .shortScore(side == PositionSide.SHORT ? score : 50)
                .sourceClassification(classification)
                .directionBias(directionBias)
                .riskLevel(riskLevel)
                .lastPrice(BigDecimal.TEN)
                .priceChange24hPct(BigDecimal.ZERO)
                .quoteVolume24h(new BigDecimal("50000000"))
                .spreadPct(new BigDecimal("0.01"))
                .fundingRate(BigDecimal.ZERO)
                .openInterest(new BigDecimal("1000000"))
                .marketBreadthPct(new BigDecimal("50"))
                .reasons(List.of(ReasonTag.MARKET_BREADTH_OK, ReasonTag.VOLUME_CONFIRMED))
                .warnings(List.of())
                .build();
    }
}
