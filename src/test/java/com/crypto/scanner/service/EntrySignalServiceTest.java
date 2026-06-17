package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.EntryCandidate;
import com.crypto.domain.model.EntrySignal;
import com.crypto.domain.model.Kline;
import com.crypto.scanner.model.BollingerScoreResult;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EntrySignalServiceTest {
    private ScannerProperties scannerProperties;
    private EntryCandidateService entryCandidateService;
    private EntrySignalService entrySignalService;

    @BeforeEach
    void setUp() {
        scannerProperties = new ScannerProperties();
        entryCandidateService = mock(EntryCandidateService.class);
        entrySignalService = new EntrySignalService(scannerProperties, entryCandidateService, new BollingerScoreService(), new EntryPriorityService(scannerProperties));
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
    void watchlistLongCandidateProducesNoEntryEvenWhenWatchlistAllowed() {
        scannerProperties.getEntrySignal().setAllowWatchlistEntry(true);

        EntrySignal signal = entrySignalService.generateSignal(candidate(
                "SOLUSDT",
                PositionSide.LONG,
                CoinClassification.WATCHLIST,
                DirectionBias.LONG,
                75,
                RiskLevel.LOW
        ));

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("WATCHLIST_NOT_ENTRY_ELIGIBLE");
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
        assertThat(signal.getBlockReason()).isEqualTo("WATCHLIST_NOT_ENTRY_ELIGIBLE");
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
        assertThat(signal.getBlockReason()).isEqualTo("WATCHLIST_NOT_ENTRY_ELIGIBLE");
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

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
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

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
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

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void spreadAboveMaxProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setSpreadPct(new BigDecimal("0.09"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("HIGH_SPREAD");
    }

    @Test
    void quoteVolumeBelowMinProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setQuoteVolume24h(new BigDecimal("29999999"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void marketChopProducesNoEntryWhenBlockMarketChopEnabled() {
        scannerProperties.getEntrySignal().setBlockMarketChop(true);
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setWarnings(List.of(ReasonTag.MARKET_CHOP));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void missingVolumeConfirmedProducesNoEntryWhenVolumeConfirmedRequired() {
        scannerProperties.getEntrySignal().setRequireVolumeConfirmed(true);
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setReasons(List.of(ReasonTag.MARKET_BREADTH_OK));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void longAboveMax24hChangeProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("18.01"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void shortBelowMax24hDumpProducesNoEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("-18.01"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
    }

    @Test
    void longCrowdedWarningBlocksLongCandidate() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setWarnings(List.of(ReasonTag.LONG_CROWDED));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void shortCrowdedWarningBlocksShortCandidate() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setWarnings(List.of(ReasonTag.SHORT_CROWDED));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
    }

    @Test
    void longLateBbOutsideChaseBypassWhenPriceChangeAndPercentBOutside() {
        setBollingerResult(new BigDecimal("1.02"), false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.1"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_BYPASS_LATE_BB_OUTSIDE_CHASE");
        assertThat(signal.getWarnings()).contains(ReasonTag.LONG_LATE_BB_OUTSIDE_CHASE_BYPASS);
    }

    @Test
    void longLateBbOutsideChaseBypassDoesNotRunBelowPriceChangeThreshold() {
        setBollingerResult(new BigDecimal("1.05"), false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("4.9"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void longLateBbOutsideChaseBypassDoesNotRunWhenBollingerIsNotOutside() {
        setBollingerResult(new BigDecimal("0.95"), false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("8.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void longLateBbOutsideChaseBypassWhenUpperClosedOutside() {
        setBollingerResult(new BigDecimal("0.95"), true, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_BYPASS_LATE_BB_OUTSIDE_CHASE");
    }

    @Test
    void longLateBbOutsideChaseBypassWhenOutsideReasonExists() {
        setBollingerResult(new BigDecimal("0.95"), false, List.of("LONG_BB_OUTSIDE_CHASE"));
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_BYPASS_LATE_BB_OUTSIDE_CHASE");
    }

    @Test
    void longLateBbOutsideChaseBypassDoesNotApplyToShort() {
        setBollingerResult(new BigDecimal("1.05"), true, List.of("LONG_BB_OUTSIDE_CHASE"));
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
        assertThat(signal.getBlockReason()).isNull();
    }

    @Test
    void longLateBbOutsideChaseBypassDoesNotRunWhenPriceChangeIsNull() {
        setBollingerResult(new BigDecimal("1.05"), false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(null);

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void longLateBbOutsideChaseBypassDoesNotRunWhenBollingerDataIsNull() {
        setBollingerResult(null, null, null);
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("8.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void longOneHourShortRsiRejectsLongEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setSourceScanType(ScanType.ONE_HOUR);
        candidate.setReasons(List.of(ReasonTag.RSI_IDEAL_SHORT));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_ONE_HOUR_SHORT_RSI_OR_24H_REJECT");
    }

    @Test
    void longOneHourPriceChangeAboveFiveRejectsLongEntry() {
        setBollingerResult(new BigDecimal("0.50"), false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setSourceScanType(ScanType.ONE_HOUR);
        candidate.setPriceChange24hPct(new BigDecimal("5.1"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_ONE_HOUR_SHORT_RSI_OR_24H_REJECT");
    }

    @Test
    void longPriceChangeBetweenFiveAndEightRejectsLongEntry() {
        setBollingerResult(new BigDecimal("0.50"), false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.0"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_24H_CHANGE_5_TO_8_REJECT");
    }

    @Test
    void longRejectRulesDoNotApplyToShortEntry() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setSourceScanType(ScanType.ONE_HOUR);
        candidate.setReasons(List.of(ReasonTag.RSI_IDEAL_SHORT));
        candidate.setPriceChange24hPct(new BigDecimal("5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
        assertThat(signal.getBlockReason()).isNull();
    }

    @Test
    void longRejectRulesIgnoreNullPriceChangeAndNullReasons() {
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setSourceScanType(ScanType.ONE_HOUR);
        candidate.setReasons(null);
        candidate.setPriceChange24hPct(null);

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
        assertThat(signal.getBlockReason()).isNull();
    }

    @Test
    void shortLateBbDownChaseBypassWhenPriceChangeAndPercentBOutside() {
        setBollingerResult(new BigDecimal("-0.05"), false, false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("-5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("SHORT_LATE_BB_DOWN_CHASE_BYPASS");
        assertThat(signal.getWarnings()).contains(ReasonTag.SHORT_LATE_BB_DOWN_CHASE_BYPASS);
    }

    @Test
    void shortLateBbDownChaseBypassDoesNotRunAbovePriceChangeThreshold() {
        setBollingerResult(new BigDecimal("-0.05"), false, false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("-4.9"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
    }

    @Test
    void shortLateBbDownChaseBypassWhenLowerClosedOutside() {
        setBollingerResult(new BigDecimal("0.20"), false, true, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("-5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("SHORT_LATE_BB_DOWN_CHASE_BYPASS");
    }

    @Test
    void shortLateBbDownChaseBypassWhenOutsideReasonExists() {
        setBollingerResult(new BigDecimal("0.20"), false, false, List.of("SHORT_BB_OUTSIDE_CHASE"));
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("-5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("SHORT_LATE_BB_DOWN_CHASE_BYPASS");
    }

    @Test
    void shortLateBbDownChaseBypassDoesNotApplyToLong() {
        setBollingerResult(new BigDecimal("-0.05"), false, false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("-5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
    }

    @Test
    void shortBbUpperReversalBonusWhenPriceChangeAndPercentBOutside() {
        setBollingerResult(new BigDecimal("1.05"), false, false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
        assertThat(signal.getEntryPriorityScore()).isEqualTo(92);
        assertThat(signal.getFinalEntryScore()).isEqualByComparingTo("92");
        assertThat(signal.getReasons()).contains(ReasonTag.SHORT_BB_UPPER_REVERSAL_BONUS);
    }

    @Test
    void shortBbUpperReversalBonusWhenUpperClosedOutside() {
        setBollingerResult(new BigDecimal("0.80"), true, false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.SHORT, CoinClassification.STRONG_SHORT,
                DirectionBias.SHORT, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
        assertThat(signal.getEntryPriorityScore()).isEqualTo(92);
        assertThat(signal.getFinalEntryScore()).isEqualByComparingTo("92");
    }

    @Test
    void shortBbUpperReversalBonusDoesNotApplyToLong() {
        setBollingerResult(new BigDecimal("1.05"), false, false, List.of());
        EntryCandidate candidate = candidate("BTCUSDT", PositionSide.LONG, CoinClassification.STRONG_LONG,
                DirectionBias.LONG, 90, RiskLevel.LOW);
        candidate.setPriceChange24hPct(new BigDecimal("5.5"));

        EntrySignal signal = entrySignalService.generateSignal(candidate);

        assertThat(signal.getAction()).isEqualTo(EntryAction.NO_ENTRY);
        assertThat(signal.getBlockReason()).isEqualTo("LONG_BYPASS_LATE_BB_OUTSIDE_CHASE");
        assertThat(signal.getReasons()).doesNotContain(ReasonTag.SHORT_BB_UPPER_REVERSAL_BONUS);
    }

    @Test
    void fiveMinuteBbTimingPenaltyAddsLongMicroChaseWarningAndSubtractsThreePoints() {
        EntrySignal signal = timingSignal(PositionSide.LONG, 90);
        setFiveMinuteKlines(risingFiveMinuteKlines("100.40"));

        ReflectionTestUtils.invokeMethod(entrySignalService, "applyFiveMinuteBbTimingPenalty", signal);

        assertThat(signal.getEntryPriorityScore()).isEqualTo(85);
        assertThat(signal.getFinalEntryScore()).isEqualByComparingTo("85");
        assertThat(signal.getWarnings()).contains(ReasonTag.LONG_5M_BB_MICRO_CHASE_RISK);
        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_LONG);
        assertThat(signal.getBlockReason()).isNull();
    }

    @Test
    void fiveMinuteBbTimingPenaltyDoesNotApplyWhenBollingerOnlyWithoutFourCandleMove() {
        EntrySignal signal = timingSignal(PositionSide.LONG, 90);
        setFiveMinuteKlines(risingFiveMinuteKlines("100.20"));

        ReflectionTestUtils.invokeMethod(entrySignalService, "applyFiveMinuteBbTimingPenalty", signal);

        assertThat(signal.getEntryPriorityScore()).isEqualTo(90);
        assertThat(signal.getWarnings()).doesNotContain(ReasonTag.LONG_5M_BB_MICRO_CHASE_RISK);
        assertThat(signal.getBlockReason()).isNull();
    }

    @Test
    void fiveMinuteBbTimingPenaltyAddsShortMicroChaseWarningAndSubtractsThreePoints() {
        EntrySignal signal = timingSignal(PositionSide.SHORT, 90);
        setFiveMinuteKlines(fallingFiveMinuteKlines("99.50"));

        ReflectionTestUtils.invokeMethod(entrySignalService, "applyFiveMinuteBbTimingPenalty", signal);

        assertThat(signal.getEntryPriorityScore()).isEqualTo(85);
        assertThat(signal.getFinalEntryScore()).isEqualByComparingTo("85");
        assertThat(signal.getWarnings()).contains(ReasonTag.SHORT_5M_BB_MICRO_CHASE_RISK);
        assertThat(signal.getAction()).isEqualTo(EntryAction.ENTER_SHORT);
        assertThat(signal.getBlockReason()).isNull();
    }

    @Test
    void fiveMinuteBbTimingPenaltySkipsMissingClosedFiveMinuteWindow() {
        EntrySignal signal = timingSignal(PositionSide.SHORT, 90);
        setFiveMinuteKlines(List.of(kline("100", "100", false), kline("100", "99.5", false)));

        ReflectionTestUtils.invokeMethod(entrySignalService, "applyFiveMinuteBbTimingPenalty", signal);

        assertThat(signal.getEntryPriorityScore()).isEqualTo(90);
        assertThat(signal.getWarnings()).doesNotContain(
                ReasonTag.LONG_5M_BB_MICRO_CHASE_RISK,
                ReasonTag.LONG_5M_MICRO_WEAKNESS,
                ReasonTag.SHORT_5M_BB_MICRO_CHASE_RISK,
                ReasonTag.SHORT_5M_MICRO_WEAKNESS
        );
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

    private void setBollingerResult(BigDecimal bbPercentB, Boolean bbUpperClosedOutside, List<String> bbReasons) {
        setBollingerResult(bbPercentB, bbUpperClosedOutside, null, bbReasons);
    }

    private void setBollingerResult(
            BigDecimal bbPercentB,
            Boolean bbUpperClosedOutside,
            Boolean bbLowerClosedOutside,
            List<String> bbReasons
    ) {
        BollingerScoreService bollingerScoreService = mock(BollingerScoreService.class);
        when(bollingerScoreService.calculate(any(), any(), any(), any())).thenReturn(BollingerScoreResult.builder()
                .bbScore(BigDecimal.ZERO)
                .bbPercentB(bbPercentB)
                .bbUpperClosedOutside(bbUpperClosedOutside)
                .bbLowerClosedOutside(bbLowerClosedOutside)
                .bbReasons(bbReasons)
                .build());
        entrySignalService = new EntrySignalService(scannerProperties, entryCandidateService, bollingerScoreService, new EntryPriorityService(scannerProperties));
    }

    private void setFiveMinuteKlines(List<Kline> klines) {
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        when(client.getKlines("BTCUSDT", "5m", 25)).thenReturn(klines);
        ReflectionTestUtils.setField(entrySignalService, "binanceFuturesClient", client);
    }

    private EntrySignal timingSignal(PositionSide side, int score) {
        return EntrySignal.builder()
                .symbol("BTCUSDT")
                .side(side)
                .action(side == PositionSide.LONG ? EntryAction.ENTER_LONG : EntryAction.ENTER_SHORT)
                .entryPriorityScore(score)
                .finalEntryScore(BigDecimal.valueOf(score))
                .score(score)
                .warnings(new ArrayList<>())
                .reasons(new ArrayList<>())
                .build();
    }

    private List<Kline> risingFiveMinuteKlines(String finalClose) {
        List<Kline> klines = flatFiveMinuteKlines(21);
        klines.add(kline("100.00", "100.10", true));
        klines.add(kline("100.10", "100.20", true));
        klines.add(kline("100.20", "100.30", true));
        klines.add(kline("100.30", finalClose, true));
        return klines;
    }

    private List<Kline> fallingFiveMinuteKlines(String finalClose) {
        List<Kline> klines = flatFiveMinuteKlines(21);
        klines.add(kline("100.00", "99.90", true));
        klines.add(kline("99.90", "99.80", true));
        klines.add(kline("99.80", "99.70", true));
        klines.add(kline("99.70", finalClose, true));
        return klines;
    }

    private List<Kline> flatFiveMinuteKlines(int count) {
        List<Kline> klines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            klines.add(kline("100.00", "100.00", true));
        }
        return klines;
    }

    private Kline kline(String open, String close, boolean closed) {
        return Kline.builder()
                .symbol("BTCUSDT")
                .interval("5m")
                .openTime(Instant.parse("2026-06-07T10:00:00Z"))
                .open(new BigDecimal(open))
                .high(new BigDecimal(open).max(new BigDecimal(close)))
                .low(new BigDecimal(open).min(new BigDecimal(close)))
                .close(new BigDecimal(close))
                .closeTime(Instant.parse("2026-06-07T10:04:59Z"))
                .closed(closed)
                .build();
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
