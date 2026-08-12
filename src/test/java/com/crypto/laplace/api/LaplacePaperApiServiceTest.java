package com.crypto.laplace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.crypto.api.dto.LaplaceAnalysisSummaryResponse;
import com.crypto.api.dto.LaplaceOpenPaperPositionResponse;
import com.crypto.api.dto.LaplaceOpenPositionCurrentStateResponse;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplacePaperApiServiceTest {
    private LaplacePaperPositionRepository repository;
    private LaplacePaperApiService service;
    private BinanceFuturesClient binance;

    @BeforeEach
    void setUp() {
        repository = mock(LaplacePaperPositionRepository.class);
        LaplaceStrategyProperties properties = new LaplaceStrategyProperties();
        properties.setActiveStrategy("LAPLACE_KERNEL_REGRESSION_30M");
        binance = mock(BinanceFuturesClient.class);
        service = new LaplacePaperApiService(repository, properties, binance);
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
    void currentOpenPositionsUseSingleBookRequestAndBidAskExitPrices() {
        var longPosition = open("btc", "BTCUSDT", PositionSide.LONG, "2026-07-18T09:00:00Z");
        var shortPosition = open("sol", "SOLUSDT", PositionSide.SHORT, "2026-07-18T09:01:00Z");
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(List.of(longPosition, shortPosition));
        when(binance.getAllBookTickers()).thenReturn(List.of(ticker("BTCUSDT", "110", "111"), ticker("SOLUSDT", "89", "90")));
        List<LaplaceOpenPositionCurrentStateResponse> response = service.findCurrentOpenPositions();
        assertThat(response).hasSize(2);
        assertThat(response.get(0).currentPrice()).isEqualByComparingTo("110");
        assertThat(response.get(0).priceMovePct()).isPositive();
        assertThat(response.get(0).currentPnlUsdt()).isEqualByComparingTo("0.9916");
        assertThat(response.get(0).partialExit()).isFalse();
        assertThat(response.get(1).currentPrice()).isEqualByComparingTo("90");
        assertThat(response.get(1).priceMovePct()).isPositive();
        assertThat(response.get(1).currentPnlUsdt()).isEqualByComparingTo("0.9924");
        verify(binance, times(1)).getAllBookTickers();
    }

    @Test
    void emptyOpenPositionsDoNotRequestBinancePrices() {
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(List.of());
        assertThat(service.findCurrentOpenPositions()).isEmpty();
        verifyNoInteractions(binance);
    }

    @Test
    void partialPositionUsesOnlyRemainingRunnerAndPriorRealizedAmounts() {
        var position = open("btc", "BTCUSDT", PositionSide.LONG, "2026-07-18T09:00:00Z");
        position.setRemainingQuantity(new BigDecimal("0.075"));
        position.setRealizedGrossPnl(new BigDecimal("0.25"));
        position.setCumulativeExitFee(new BigDecimal("0.001"));
        position.setPartialTakeProfitExecuted(true);
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(List.of(position));
        when(binance.getAllBookTickers()).thenReturn(List.of(ticker("BTCUSDT", "110", "111")));
        LaplaceOpenPositionCurrentStateResponse response = service.findCurrentOpenPositions().getFirst();
        assertThat(response.currentPnlUsdt()).isEqualByComparingTo("0.9917");
        assertThat(response.partialExit()).isTrue();
    }

    @Test
    void missingBookTickerReturnsServiceUnavailable() {
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(List.of(open("btc", "BTCUSDT", PositionSide.LONG, "2026-07-18T09:00:00Z")));
        when(binance.getAllBookTickers()).thenReturn(List.of(ticker("SOLUSDT", "10", "11")));
        org.assertj.core.api.Assertions.assertThatThrownBy(service::findCurrentOpenPositions)
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(e -> ((org.springframework.web.server.ResponseStatusException)e).getStatusCode().value())
                .isEqualTo(503);
    }

    @Test
    void summaryCountsOpenPositionsButExcludesThemFromTradeAndPnlMetrics() {
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(List.of(open("sol", "SOLUSDT", PositionSide.SHORT, "2026-07-18T09:00:00Z"), open("btc", "BTCUSDT", PositionSide.LONG, "2026-07-18T09:01:00Z")));
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED)).thenReturn(List.of());
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
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED)).thenReturn(closed);
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
        when(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED))
                .thenReturn(List.of(closed("old-short", "SOLUSDT", PositionSide.SHORT, "0.2", "0.004", "0.00392", "0.19208", "1.9208")));
        LaplaceAnalysisSummaryResponse summary = service.summary();
        assertThat(summary.tradeCount()).isEqualTo(1);
        assertThat(summary.shortTradeCount()).isEqualTo(1);
        assertThat(summary.longTradeCount()).isZero();
        assertThat(summary.openPositionCount()).isEqualTo(1);
    }

    private LaplacePaperPositionEntity open(String id, String symbol, PositionSide side, String entryTime) {
        return base(id, symbol, side, LaplacePositionStatus.OPEN, Instant.parse(entryTime)).exitTime(null).build();
    }

    private BookTicker ticker(String symbol, String bid, String ask) {
        return BookTicker.builder().symbol(symbol).bidPrice(new BigDecimal(bid)).askPrice(new BigDecimal(ask)).build();
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
