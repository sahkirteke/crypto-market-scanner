package com.crypto.laplace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.api.dto.LaplaceAnalysisSummaryResponse;
import com.crypto.api.dto.LaplaceOpenPaperPositionResponse;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.persistence.LaplaceTradeEventEntity;
import com.crypto.laplace.persistence.LaplaceTradeEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplacePaperApiServiceTest {
    private LaplacePaperPositionRepository repository;
    private LaplacePaperApiService service;
    private LaplaceTradeEventRepository events;

    @BeforeEach
    void setUp() {
        repository = mock(LaplacePaperPositionRepository.class);
        LaplaceStrategyProperties properties = new LaplaceStrategyProperties();
        properties.setActiveStrategy("LAPLACE_KERNEL_REGRESSION_30M");
        events = mock(LaplaceTradeEventRepository.class);
        when(events.findTop100ByEventTypeOrderByCreatedAtDesc("RISKY_ENTRY_SKIPPED")).thenReturn(List.of());
        service = new LaplacePaperApiService(repository, properties, events, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void openPositionsReturnsOnlyOpenLaplacePositionsSortedByEntryTime() {
        LaplacePaperPositionEntity btc = open("btc", "BTCUSDT", PositionSide.LONG, "2026-07-18T09:01:00Z");
        LaplacePaperPositionEntity sol = open("sol", "SOLUSDT", PositionSide.SHORT, "2026-07-18T09:00:00Z");
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(List.of(btc, sol));
        List<LaplaceOpenPaperPositionResponse> response = service.findOpenPositions();
        assertThat(response).extracting(LaplaceOpenPaperPositionResponse::symbol).containsExactly("SOLUSDT", "BTCUSDT");
        assertThat(response.get(0).side()).isEqualTo(PositionSide.SHORT);
        assertThat(response.get(0).status()).isEqualTo(LaplacePositionStatus.OPEN);
        assertThat(response.get(0).entryNotional()).isEqualByComparingTo("10");
        assertThat(response.get(0).margin()).isEqualByComparingTo("10");
        assertThat(response.get(0).leverage()).isOne();
        assertThat(response.get(0).orderType()).isEqualTo("MARKET");
        assertThat(response.get(0).entryExecutionPriceType()).isEqualTo("BID");
        assertThat(response.get(0).executionAction()).isEqualTo("SHORT_OPEN");
        assertThat(response.get(0).entryFee()).isEqualByComparingTo("0.004");
        assertThat(response.get(1).entryExecutionPriceType()).isEqualTo("ASK");
        assertThat(response.get(1).executionAction()).isEqualTo("LONG_OPEN");
    }

    @Test
    void summaryCountsOpenPositionsButExcludesThemFromTradeAndPnlMetrics() {
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(List.of(open("sol", "SOLUSDT", PositionSide.SHORT, "2026-07-18T09:00:00Z"), open("btc", "BTCUSDT", PositionSide.LONG, "2026-07-18T09:01:00Z")));
        when(repository.findByStrategyAndStatusIn(eq(LaplacePaperExecutionService.STRATEGY), anyCollection())).thenReturn(List.of());
        LaplaceAnalysisSummaryResponse summary = service.summary();
        assertThat(summary.openPositionCount()).isEqualTo(2);
        assertThat(summary.tradeCount()).isZero();
        assertThat(summary.closedPositionCount()).isZero();
        assertThat(summary.winCount()).isZero();
        assertThat(summary.lossCount()).isZero();
        assertThat(summary.winRate()).isEqualByComparingTo("0");
        assertThat(summary.netPnl()).isEqualByComparingTo("0");
    }

    @Test
    void summaryUsesClosedPersistedNetPnlForWinLossAndLongShortMetrics() {
        List<LaplacePaperPositionEntity> closed = List.of(
                closed("long-win", "SOLUSDT", PositionSide.LONG, "0.2", "0.004", "0.00408", "0.19192", "1.9192"),
                closed("long-loss", "BTCUSDT", PositionSide.LONG, "0.01", "0.02", "0.02", "-0.03", "-0.3"),
                closed("short-win", "ETHUSDT", PositionSide.SHORT, "0.2", "0.004", "0.00392", "0.19208", "1.9208"),
                closed("short-loss", "ADAUSDT", PositionSide.SHORT, "-0.1", "0.004", "0.00404", "-0.10804", "-1.0804"),
                closed("flat", "XRPUSDT", PositionSide.LONG, "0.008", "0.004", "0.004", "0", "0"));
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(List.of(open("open", "REUSDT", PositionSide.LONG, "2026-07-18T09:00:00Z")));
        when(repository.findByStrategyAndStatusIn(eq(LaplacePaperExecutionService.STRATEGY), anyCollection())).thenReturn(closed);
        LaplaceAnalysisSummaryResponse summary = service.summary();
        assertThat(summary.tradeCount()).isEqualTo(5);
        assertThat(summary.openPositionCount()).isEqualTo(1);
        assertThat(summary.closedPositionCount()).isEqualTo(5);
        assertThat(summary.winCount()).isEqualTo(2);
        assertThat(summary.lossCount()).isEqualTo(2);
        assertThat(summary.breakEvenCount()).isEqualTo(1);
        assertThat(summary.winRate()).isEqualByComparingTo("40");
        assertThat(summary.longTradeCount()).isEqualTo(3);
        assertThat(summary.shortTradeCount()).isEqualTo(2);
        assertThat(summary.totalFee()).isEqualByComparingTo("0.07204");
        assertThat(summary.netPnl()).isEqualByComparingTo("0.24604");
        assertThat(summary.longNetPnl()).isEqualByComparingTo("0.16192");
        assertThat(summary.shortNetPnl()).isEqualByComparingTo("0.08404");
        assertThat(summary.bestTradePnl()).isEqualByComparingTo("0.19208");
        assertThat(summary.worstTradePnl()).isEqualByComparingTo("-0.10804");
    }

    @Test
    void reversalClosedPositionCountsAsCompletedTradeButNewOpenTargetDoesNot() {
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(List.of(open("new-long", "SOLUSDT", PositionSide.LONG, "2026-07-18T10:00:00Z")));
        when(repository.findByStrategyAndStatusIn(eq(LaplacePaperExecutionService.STRATEGY), anyCollection()))
                .thenReturn(List.of(closed("old-short", "SOLUSDT", PositionSide.SHORT, "0.2", "0.004", "0.00392", "0.19208", "1.9208")));
        LaplaceAnalysisSummaryResponse summary = service.summary();
        assertThat(summary.tradeCount()).isEqualTo(1);
        assertThat(summary.shortTradeCount()).isEqualTo(1);
        assertThat(summary.longTradeCount()).isZero();
        assertThat(summary.openPositionCount()).isEqualTo(1);
    }

    @Test
    void summaryIncludesSignalAndStopLossClosedPositions() {
        LaplacePaperPositionEntity signalClosed = closed("signal", "BTCUSDT", PositionSide.LONG,
                "1", "0.02", "0.02", "0.96", "1.92");
        signalClosed.setStatus(LaplacePositionStatus.CLOSED_BY_SIGNAL);
        LaplacePaperPositionEntity stopClosed = closed("stop", "ETHUSDT", PositionSide.SHORT,
                "-2.75", "0.02", "0.0189", "-2.7889", "-5.5778");
        stopClosed.setStatus(LaplacePositionStatus.CLOSED_BY_STOP_LOSS);
        stopClosed.setExitReason("STOP_LOSS_5M");
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(List.of());
        when(repository.findByStrategyAndStatusIn(eq(LaplacePaperExecutionService.STRATEGY), anyCollection()))
                .thenReturn(List.of(signalClosed, stopClosed));

        LaplaceAnalysisSummaryResponse summary = service.summary();

        assertThat(summary.tradeCount()).isEqualTo(2);
        assertThat(summary.winCount()).isEqualTo(1);
        assertThat(summary.lossCount()).isEqualTo(1);
        assertThat(summary.netPnl()).isEqualByComparingTo("-1.8289");
    }

    @Test
    void summaryAddsOnlyRiskyEntrySkipsNewestFirstWithoutChangingTradeMetrics() {
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(List.of());
        when(repository.findByStrategyAndStatusIn(eq(LaplacePaperExecutionService.STRATEGY), anyCollection())).thenReturn(List.of());
        when(events.countByEventType("RISKY_ENTRY_SKIPPED")).thenReturn(2L);
        when(events.findTop100ByEventTypeOrderByCreatedAtDesc("RISKY_ENTRY_SKIPPED")).thenReturn(List.of(
                skip("new", "BTCUSDT", "2026-07-28T16:30:03.456Z", "LONG"),
                skip("old", "ETHUSDT", "2026-07-28T16:00:04.123Z", "SHORT")));

        LaplaceAnalysisSummaryResponse summary = service.summary();

        assertThat(summary.skippedEntryCount()).isEqualTo(2);
        assertThat(summary.skippedEntries()).extracting(e -> e.symbol()).containsExactly("BTCUSDT", "ETHUSDT");
        assertThat(summary.skippedEntries()).extracting(e -> e.effectiveExecutionSide()).containsExactly(PositionSide.LONG, PositionSide.SHORT);
        assertThat(summary.skippedEntries()).extracting(e -> e.entryPrice()).containsExactly(new BigDecimal("0.3422"), new BigDecimal("100.25"));
        assertThat(summary.skippedEntries()).allSatisfy(e -> assertThat(e.skipReasons()).containsExactly(com.crypto.laplace.execution.LaplaceEntrySkipReason.RISKY_ENTRY_FILTER));
        assertThat(summary.tradeCount()).isZero();
        assertThat(summary.netPnl()).isEqualByComparingTo("0");
    }

    @Test
    void summaryReturnsEmptySkipFieldsWhenNoRiskyEntriesExist() {
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(List.of());
        when(repository.findByStrategyAndStatusIn(eq(LaplacePaperExecutionService.STRATEGY), anyCollection())).thenReturn(List.of());
        LaplaceAnalysisSummaryResponse summary = service.summary();
        assertThat(summary.skippedEntryCount()).isZero();
        assertThat(summary.skippedEntries()).isEmpty();
    }

    private LaplaceTradeEventEntity skip(String id, String symbol, String time, String side) {
        return LaplaceTradeEventEntity.builder().eventId(id).eventType("RISKY_ENTRY_SKIPPED").symbol(symbol)
                .payloadJson("{\"symbol\":\"" + symbol + "\",\"skipTime\":\"" + time
                        + "\",\"effectiveExecutionSide\":\"" + side + "\",\"entryPrice\":"
                        + ("BTCUSDT".equals(symbol) ? "0.3422" : "100.25") + ",\"skipReasons\":[\"RISKY_ENTRY_FILTER\"]}")
                .createdAt(Instant.parse(time)).build();
    }

    private LaplacePaperPositionEntity open(String id, String symbol, PositionSide side, String entryTime) {
        return base(id, symbol, side, LaplacePositionStatus.OPEN, Instant.parse(entryTime)).exitTime(null).build();
    }

    private LaplacePaperPositionEntity closed(String id, String symbol, PositionSide side, String grossPnl,
                                              String entryFee, String exitFee, String netPnl, String netPnlPct) {
        Instant entry = Instant.parse("2026-07-18T09:00:00Z");
        return base(id, symbol, side, LaplacePositionStatus.CLOSED, entry)
                .entryFee(new BigDecimal(entryFee))
                .exitTime(entry.plusSeconds(1800))
                .exitExecutionPrice(new BigDecimal("102"))
                .exitFee(new BigDecimal(exitFee))
                .grossPnl(new BigDecimal(grossPnl))
                .grossPnlPct(new BigDecimal("2"))
                .netPnl(new BigDecimal(netPnl))
                .netPnlPct(new BigDecimal(netPnlPct))
                .exitReason("OPPOSITE_CONFIRMED_LAPLACE_SIGNAL")
                .build();
    }

    private LaplacePaperPositionEntity.LaplacePaperPositionEntityBuilder base(String id, String symbol, PositionSide side,
                                                                              LaplacePositionStatus status, Instant entryTime) {
        return LaplacePaperPositionEntity.builder()
                .id(id)
                .strategy(LaplacePaperExecutionService.STRATEGY)
                .strategyVersion(LaplacePaperExecutionService.VERSION)
                .symbol(symbol)
                .side(side)
                .status(status)
                .entrySignalId(id + "-signal")
                .entryCandleCloseTime(entryTime.minusMillis(1))
                .entryTime(entryTime)
                .entrySignalClosePrice(new BigDecimal("100"))
                .entryExecutionPrice(new BigDecimal("100"))
                .margin(BigDecimal.TEN)
                .quantity(new BigDecimal("0.1"))
                .notional(BigDecimal.TEN)
                .leverage(1)
                .entryFeeRate(new BigDecimal("0.0004"))
                .entryFee(new BigDecimal("0.004"));
    }
}
