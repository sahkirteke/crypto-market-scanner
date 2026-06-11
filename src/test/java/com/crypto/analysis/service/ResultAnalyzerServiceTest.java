package com.crypto.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.analysis.dto.StrategyAnalysisResponse;
import com.crypto.analysis.dto.SymbolPerformanceResponse;
import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ResultAnalyzerServiceTest {
    private PaperPositionRepository repository;
    private ResultAnalyzerService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaperPositionRepository.class);
        service = new ResultAnalyzerService(repository);
    }

    @Test
    void analyzeWithNoClosedTradesReturnsEmptySummaryAndObservation() {
        StrategyAnalysisResponse response = service.analyze(List.of());

        assertThat(response.summary().totalTrades()).isZero();
        assertThat(response.observations()).contains("No closed paper trades found.");
    }

    @Test
    void analyzeCountsWinLossFlatAndCalculatesWinRate() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "1.0", "1.0"),
                position("ETHUSDT", PositionSide.LONG, "-0.5", "-0.5"),
                position("SOLUSDT", PositionSide.SHORT, "0", "0")
        ));

        assertThat(response.summary().winCount()).isEqualTo(1);
        assertThat(response.summary().lossCount()).isEqualTo(1);
        assertThat(response.summary().flatCount()).isEqualTo(1);
        assertThat(response.summary().winRatePct()).isEqualByComparingTo("50");
    }

    @Test
    void analyzeSumsTotalRealizedPnlUsdt() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "1.5", "1.0"),
                position("ETHUSDT", PositionSide.SHORT, "-0.5", "-0.5")
        ));

        assertThat(response.summary().totalRealizedPnlUsdt()).isEqualByComparingTo("1.0");
    }

    @Test
    void analyzeCalculatesAverageRealizedPnlPct() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "1.5", "1.0"),
                position("ETHUSDT", PositionSide.SHORT, "-0.5", "-0.5"),
                position("SOLUSDT", PositionSide.SHORT, "0.0", "1.0")
        ));

        assertThat(response.summary().avgRealizedPnlPct()).isEqualByComparingTo("0.5");
    }

    @Test
    void analyzeBuildsLongShortBreakdown() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "1.0", "1.0"),
                position("ETHUSDT", PositionSide.LONG, "-0.5", "-0.5"),
                position("SOLUSDT", PositionSide.SHORT, "2.0", "2.0")
        ));

        assertThat(response.byDirection())
                .extracting("side", "totalTrades", "winCount", "lossCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("LONG", 2, 1, 1),
                        org.assertj.core.groups.Tuple.tuple("SHORT", 1, 1, 0)
                );
    }

    @Test
    void analyzeBuildsDirectionBreakdownFromExecutionSide() {
        PaperPositionEntity invertedShortToLong = position("ETHUSDT", PositionSide.LONG, "1.0", "1.0");
        invertedShortToLong.setSourceSignalSide(PositionSide.SHORT);
        invertedShortToLong.setExecutionSide(PositionSide.LONG);
        invertedShortToLong.setSignalInverted(true);
        invertedShortToLong.setInversionReason("SHORT_SIGNAL_INVERTED_TO_LONG");

        StrategyAnalysisResponse response = service.analyze(List.of(
                invertedShortToLong,
                position("SOLUSDT", PositionSide.SHORT, "2.0", "2.0")
        ));

        assertThat(response.byDirection())
                .extracting("side", "totalTrades", "winCount", "lossCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("LONG", 1, 1, 0),
                        org.assertj.core.groups.Tuple.tuple("SHORT", 1, 1, 0)
                );
    }

    @Test
    void analyzeSortsTopAndWorstSymbolsByTotalRealizedPnl() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "3.0", "1.0"),
                position("ETHUSDT", PositionSide.LONG, "-2.0", "-0.5"),
                position("SOLUSDT", PositionSide.SHORT, "1.0", "0.5")
        ));

        assertThat(response.topSymbols()).extracting(SymbolPerformanceResponse::symbol)
                .containsExactly("BTCUSDT", "SOLUSDT", "ETHUSDT");
        assertThat(response.worstSymbols()).extracting(SymbolPerformanceResponse::symbol)
                .containsExactly("ETHUSDT", "SOLUSDT", "BTCUSDT");
    }

    @Test
    void analyzeBuildsClassificationBreakdown() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "1.0", "1.0", CoinClassification.STRONG_LONG, RiskLevel.LOW, "TAKE_PROFIT"),
                position("ETHUSDT", PositionSide.SHORT, "-1.0", "-1.0", CoinClassification.STRONG_SHORT, RiskLevel.HIGH, "STOP_LOSS"),
                position("SOLUSDT", PositionSide.LONG, "0", "0", null, RiskLevel.MEDIUM, null)
        ));

        assertThat(response.byClassification())
                .extracting("classification", "totalTrades", "winCount", "lossCount")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("STRONG_LONG", 1, 1, 0),
                        org.assertj.core.groups.Tuple.tuple("STRONG_SHORT", 1, 0, 1),
                        org.assertj.core.groups.Tuple.tuple("UNKNOWN", 1, 0, 0)
                );
    }

    @Test
    void analyzeBuildsRiskLevelBreakdown() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "1.0", "1.0", CoinClassification.STRONG_LONG, RiskLevel.LOW, "TAKE_PROFIT"),
                position("ETHUSDT", PositionSide.SHORT, "-1.0", "-1.0", CoinClassification.STRONG_SHORT, RiskLevel.HIGH, "STOP_LOSS"),
                position("SOLUSDT", PositionSide.LONG, "0", "0", CoinClassification.WATCHLIST, null, null)
        ));

        assertThat(response.byRiskLevel())
                .extracting("riskLevel", "totalTrades", "winCount", "lossCount")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("LOW", 1, 1, 0),
                        org.assertj.core.groups.Tuple.tuple("HIGH", 1, 0, 1),
                        org.assertj.core.groups.Tuple.tuple("UNKNOWN", 1, 0, 0)
                );
    }

    @Test
    void analyzeBuildsExitReasonBreakdown() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                position("BTCUSDT", PositionSide.LONG, "1.0", "1.0", CoinClassification.STRONG_LONG, RiskLevel.LOW, "TAKE_PROFIT"),
                position("ETHUSDT", PositionSide.SHORT, "-1.0", "-1.0", CoinClassification.STRONG_SHORT, RiskLevel.HIGH, "STOP_LOSS"),
                position("SOLUSDT", PositionSide.LONG, "0", "0", CoinClassification.WATCHLIST, RiskLevel.MEDIUM, null)
        ));

        assertThat(response.byExitReason())
                .extracting("exitReason", "totalTrades", "winCount", "lossCount")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("TAKE_PROFIT", 1, 1, 0),
                        org.assertj.core.groups.Tuple.tuple("STOP_LOSS", 1, 0, 1),
                        org.assertj.core.groups.Tuple.tuple("UNKNOWN", 1, 0, 0)
                );
    }

    @Test
    void analyzeBuildsSignalExecutionModeBreakdown() {
        PaperPositionEntity normalLong = position("BTCUSDT", PositionSide.LONG, "1.0", "1.0");
        normalLong.setSourceSignalSide(PositionSide.LONG);
        normalLong.setExecutionSide(PositionSide.LONG);
        normalLong.setSignalInverted(false);
        PaperPositionEntity invertedShort = position("ETHUSDT", PositionSide.LONG, "-1.0", "-1.0");
        invertedShort.setSourceSignalSide(PositionSide.SHORT);
        invertedShort.setExecutionSide(PositionSide.LONG);
        invertedShort.setSignalInverted(true);
        invertedShort.setInversionReason("SHORT_SIGNAL_INVERTED_TO_LONG");
        PaperPositionEntity other = position("SOLUSDT", PositionSide.SHORT, "0", "0");
        other.setSourceSignalSide(PositionSide.SHORT);
        other.setExecutionSide(PositionSide.SHORT);
        other.setSignalInverted(false);

        StrategyAnalysisResponse response = service.analyze(List.of(normalLong, invertedShort, other));

        assertThat(response.bySignalExecutionMode())
                .extracting("signalExecutionMode", "totalTrades", "winCount", "lossCount", "flatCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("NORMAL_LONG", 1, 1, 0, 0),
                        org.assertj.core.groups.Tuple.tuple("INVERTED_SHORT_TO_LONG", 1, 0, 1, 0),
                        org.assertj.core.groups.Tuple.tuple("OTHER", 1, 0, 0, 1)
                );
        assertThat(response.bySignalExecutionMode().get(0).avgMaxFavorableMovePct()).isEqualByComparingTo("1.5");
        assertThat(response.bySignalExecutionMode().get(1).avgMaxAdverseMovePct()).isEqualByComparingTo("-0.5");
        assertThat(response.bySignalExecutionMode().get(0).avgMinutesHeld()).isEqualByComparingTo("10");
        assertThat(response.bySignalExecutionMode().get(0).avgBarsHeld()).isEqualByComparingTo("2");
        assertThat(response.bySignalExecutionMode().get(0).bestTradePct()).isEqualByComparingTo("1.0");
        assertThat(response.bySignalExecutionMode().get(1).worstTradePct()).isEqualByComparingTo("-1.0");
    }

    @Test
    void analyzeLastClosedTradesLimitsPageSizeToMaxOneThousand() {
        when(repository.findByStatusOrderByClosedAtDesc(eq(PaperPositionStatus.CLOSED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.analyzeLastClosedTrades(5000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStatusOrderByClosedAtDesc(eq(PaperPositionStatus.CLOSED), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1000);
    }

    @Test
    void analyzeAddsObservationWhenClosedTradesHaveNullRealizedPnlPct() {
        StrategyAnalysisResponse response = service.analyze(List.of(
                PaperPositionEntity.builder()
                        .symbol("BTCUSDT")
                        .status(PaperPositionStatus.CLOSED)
                        .side(PositionSide.LONG)
                        .realizedPnlUsdt(new BigDecimal("0"))
                        .realizedPnlPct(null)
                        .openedAt(Instant.parse("2026-06-07T00:00:00Z"))
                        .closedAt(Instant.parse("2026-06-07T00:05:00Z"))
                        .build()
        ));

        assertThat(response.summary().flatCount()).isEqualTo(1);
        assertThat(response.observations()).contains("Some closed trades have null realizedPnlPct");
    }

    private PaperPositionEntity position(String symbol, PositionSide side, String pnlUsdt, String pnlPct) {
        return position(symbol, side, pnlUsdt, pnlPct, CoinClassification.WATCHLIST, RiskLevel.MEDIUM, "UNKNOWN");
    }

    private PaperPositionEntity position(
            String symbol,
            PositionSide side,
            String pnlUsdt,
            String pnlPct,
            CoinClassification classification,
            RiskLevel riskLevel,
            String exitReason
    ) {
        return PaperPositionEntity.builder()
                .symbol(symbol)
                .status(PaperPositionStatus.CLOSED)
                .side(side)
                .sourceSignalSide(side)
                .executionSide(side)
                .signalInverted(false)
                .sourceClassification(classification)
                .riskLevel(riskLevel)
                .exitReason(exitReason)
                .realizedPnlUsdt(new BigDecimal(pnlUsdt))
                .realizedPnlPct(new BigDecimal(pnlPct))
                .maxFavorableMovePct(new BigDecimal("1.5"))
                .maxAdverseMovePct(new BigDecimal("-0.5"))
                .minutesHeld(10)
                .barsHeld(2)
                .openedAt(Instant.parse("2026-06-07T00:00:00Z"))
                .closedAt(Instant.parse("2026-06-07T00:05:00Z"))
                .build();
    }
}
