package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.model.StartupState;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.persistence.LaplaceTradeEventRepository;
import com.crypto.laplace.service.StartupMarketUniverseService;
import com.crypto.domain.model.Kline;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplacePaperExecutionServiceTest {
    private LaplacePaperPositionRepository positions;
    private LaplaceExecutionPriceProvider prices;
    private LaplaceTradeEventRepository events;
    private LaplaceTradeJsonlWriter writer;
    private LaplacePaperExecutionService service;

    @BeforeEach
    void setUp() {
        positions = mock(LaplacePaperPositionRepository.class);
        events = mock(LaplaceTradeEventRepository.class);
        prices = mock(LaplaceExecutionPriceProvider.class);
        writer = mock(LaplaceTradeJsonlWriter.class);
        StartupMarketUniverseService universe = mock(StartupMarketUniverseService.class);
        when(writer.tryJson(any())).thenReturn(Optional.of("{}"));
        when(universe.symbols()).thenReturn(Set.of("BTCUSDT"));
        LaplaceStrategyProperties properties = new LaplaceStrategyProperties();
        service = new LaplacePaperExecutionService(positions, events, prices, new LaplacePnlCalculator(),
                properties, writer, universe);
    }

    @Test
    void everyLongEntryUsesAskAndConfiguredPaperSize() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN)).thenReturn(price("99", "100", "100", "ASK"));
        LaplacePaperPositionEntity opened = service.open(signal(), PositionSide.LONG, null, "FLAT");
        assertThat(opened.getEntryExecutionPrice()).isEqualByComparingTo("100");
        assertThat(opened.getEntrySignalClosePrice()).isEqualByComparingTo("77");
        assertThat(opened.getNotional()).isEqualByComparingTo("50");
        assertThat(opened.getMargin()).isEqualByComparingTo("5");
        assertThat(opened.getLeverage()).isEqualTo(10);
        assertThat(opened.getQuantity()).isEqualByComparingTo("0.5");
        assertThat(opened.getEntryFee()).isEqualByComparingTo("0.02");
        assertThat(opened.getStopPrice()).isEqualByComparingTo("95");
        assertThat(opened.getStopLossPct()).isEqualByComparingTo("0.05");
    }

    @Test
    void everyShortEntryUsesBidAndConfiguredPaperSize() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.SHORT_OPEN)).thenReturn(price("50", "51", "50", "BID"));
        LaplacePaperPositionEntity opened = service.open(signal(), PositionSide.SHORT, null, "FLAT");
        assertThat(opened.getEntryExecutionPrice()).isEqualByComparingTo("50");
        assertThat(opened.getNotional()).isEqualByComparingTo("50");
        assertThat(opened.getMargin()).isEqualByComparingTo("5");
        assertThat(opened.getLeverage()).isEqualTo(10);
        assertThat(opened.getQuantity()).isEqualByComparingTo("1");
        assertThat(opened.getStopPrice()).isEqualByComparingTo("52.5");
        assertThat(opened.getStopLossPct()).isEqualByComparingTo("0.05");
    }

    @Test
    void rawLongExecutionUsesAskAndLongStopWhileAuditKeepsShortFilteringSide() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN)).thenReturn(price("99", "100", "100", "ASK"));
        LaplacePaperPositionEntity opened=service.open(signal(),PositionSide.LONG,PositionSide.SHORT,null,"FLAT");
        assertThat(opened.getSide()).isEqualTo(PositionSide.LONG);
        assertThat(opened.getSignalInverted()).isTrue();
        assertThat(opened.getStopPrice()).isEqualByComparingTo("95");
        verify(writer).tryJson(org.mockito.ArgumentMatchers.argThat(value->{var payload=(java.util.Map<?,?>)value;return payload.get("rawEntrySignal")==LaplaceSignal.LONG&&payload.get("filteringSide")==PositionSide.SHORT&&payload.get("executionSide")==PositionSide.LONG;}));
    }

    @Test
    void rawShortExecutionUsesBidAndShortStopWhileAuditKeepsLongFilteringSide() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.SHORT_OPEN)).thenReturn(price("50", "51", "50", "BID"));
        LaplacePaperPositionEntity opened=service.open(signal(),PositionSide.SHORT,PositionSide.LONG,null,"FLAT");
        assertThat(opened.getSide()).isEqualTo(PositionSide.SHORT);
        assertThat(opened.getSignalInverted()).isTrue();
        assertThat(opened.getStopPrice()).isEqualByComparingTo("52.5");
    }

    @Test
    void entryAuditContainsAllOverlaySidesAndBtcContext() {
        when(positions.findOpenForUpdate(any(),any(),any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT",MarketExecutionAction.SHORT_OPEN)).thenReturn(price("99","100","99","BID"));
        Instant time=Instant.now();Kural4MarketContext market=new Kural4MarketContext(time,time,100,.05,-.03,.2,20);
        Kural4ExecutionDecision decision=new Kural4ExecutionDecision(true,PositionSide.LONG,PositionSide.SHORT,
                Kural4ExecutionAction.INVERTED,List.of(Kural4DecisionReason.K4_INVERT_RAW_LONG),market);
        service.open(signal(),PositionSide.SHORT,new LaplaceExecutionOverlayContext(PositionSide.LONG,
                PositionSide.SHORT,decision,true),null,"FLAT");
        verify(writer).tryJson(org.mockito.ArgumentMatchers.argThat(value->{Map<?,?> payload=(Map<?,?>)value;
            return payload.get("rawExecutionSide")==PositionSide.LONG
                    && payload.get("volumeProfileFilteringSide")==PositionSide.SHORT
                    && payload.get("kural4FinalExecutionSide")==PositionSide.SHORT
                    && payload.get("btcContextLastCompleted5mCloseTime").equals(time)
                    && payload.get("btcContextCompletedCandleCount").equals(20);
        }));
    }

    @Test
    void availableBalanceAccountsForOpenMarginsAndEntryFees() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        List<LaplacePaperPositionEntity> fiftyOpen = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> LaplacePaperPositionEntity.builder().margin(BigDecimal.ONE)
                        .entryFee(new BigDecimal("0.008")).build()).toList();
        when(positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED)).thenReturn(List.of());
        when(positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(fiftyOpen);
        assertThat(service.availableBalance()).isEqualByComparingTo("199.600");
    }

    @Test
    void opensEligiblePaperEntryEvenWhenReportedCapitalIsInsufficient() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN))
                .thenReturn(java.util.stream.IntStream.range(0, 249).mapToObj(i ->
                        LaplacePaperPositionEntity.builder().margin(BigDecimal.ONE).entryFee(BigDecimal.ZERO).build()).toList());
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN)).thenReturn(price("99", "100", "100", "ASK"));
        LaplacePaperPositionEntity opened = service.open(signal(), PositionSide.LONG, null, "FLAT");
        assertThat(opened.getStatus()).isEqualTo(LaplacePositionStatus.OPEN);
        verify(positions).saveAndFlush(opened);
    }

    @Test
    void longCloseUsesBidAndExitNotionalForTakerFee() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.LONG);
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of(open));
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_CLOSE)).thenReturn(price("110", "111", "110", "BID"));
        service.reverse(signal(), open, PositionSide.SHORT, false);
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo("110");
        assertThat(open.getExitFee()).isEqualByComparingTo("0.0088");
        assertThat(open.getGrossPnl()).isEqualByComparingTo("2");
        assertThat(open.getNetPnl()).isEqualByComparingTo("1.9832");
        verify(prices).quote("BTCUSDT", MarketExecutionAction.LONG_CLOSE);
    }

    @Test
    void shortCloseUsesAsk() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.SHORT);
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of(open));
        when(prices.quote("BTCUSDT", MarketExecutionAction.SHORT_CLOSE)).thenReturn(price("89", "90", "90", "ASK"));
        service.reverse(signal(), open, PositionSide.LONG, false);
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo("90");
    }

    @Test
    void reversalCloseFailureNeverAttemptsReplacement() {
        LaplacePaperPositionEntity open=openPosition(PositionSide.LONG);
        when(positions.findOpenForUpdate(any(),any(),any())).thenReturn(List.of(open));
        when(prices.quote("BTCUSDT",MarketExecutionAction.LONG_CLOSE)).thenThrow(new IllegalStateException("close failed"));
        Kural4ExecutionDecision raw=new Kural4ExecutionDecision(true,PositionSide.SHORT,PositionSide.SHORT,
                Kural4ExecutionAction.RAW,List.of(),null);
        LaplaceExecutionOverlayContext overlay=new LaplaceExecutionOverlayContext(PositionSide.SHORT,PositionSide.LONG,raw,true);
        assertThatThrownBy(()->service.reverse(signal(),open,PositionSide.SHORT,overlay,true))
                .isInstanceOf(IllegalStateException.class).hasMessage("close failed");
        assertThat(open.getStatus()).isEqualTo(LaplacePositionStatus.OPEN);
        verify(prices,never()).quote("BTCUSDT",MarketExecutionAction.SHORT_OPEN);
    }

    @Test
    void reversalOverlaySkipClosesPositionWithoutReplacementAndAuditsReason() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.LONG);
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of(open));
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_CLOSE)).thenReturn(price("99", "100", "99", "BID"));
        Instant time=Instant.now();
        Kural4MarketContext market=new Kural4MarketContext(time,time,100,.1,.1,.2,20);
        Kural4ExecutionDecision skipped=new Kural4ExecutionDecision(false,PositionSide.SHORT,null,
                Kural4ExecutionAction.SKIP,List.of(Kural4DecisionReason.EXT_B_SKIP_RAW_SHORT),market);
        LaplaceExecutionOverlayContext overlay=new LaplaceExecutionOverlayContext(PositionSide.SHORT,
                PositionSide.LONG,skipped,true);

        LaplacePaperExecutionService.ReversalOutcome outcome=service.reverse(signal(),open,PositionSide.SHORT,overlay,false);

        assertThat(outcome.closed().getStatus()).isEqualTo(LaplacePositionStatus.CLOSED_BY_SIGNAL);
        assertThat(outcome.opened()).isNull();
        verify(prices,never()).quote("BTCUSDT",MarketExecutionAction.SHORT_OPEN);
        verify(positions).saveAndFlush(open);
        verify(writer).tryJson(org.mockito.ArgumentMatchers.argThat(value->{Map<?,?> payload=(Map<?,?>)value;
            return payload.get("eventType").equals("EXIT")&&payload.get("kural4Action")==Kural4ExecutionAction.SKIP
                    && ((List<?>)payload.get("kural4Reasons")).contains(Kural4DecisionReason.EXT_B_SKIP_RAW_SHORT)
                    && payload.get("kural4FinalExecutionSide")==null;}));
    }

    @Test
    void stopCloseUsesExecutableBidWritesOneExitAndIsIdempotent() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.LONG);
        open.setStopLossPct(new BigDecimal("0.05"));
        open.setStopPrice(new BigDecimal("95.4"));
        when(positions.findByIdForUpdate("position")).thenReturn(Optional.of(open));
        Kline trigger = Kline.builder().openTime(Instant.now().minusSeconds(300)).closeTime(Instant.now())
                .open(new BigDecimal("95")).high(new BigDecimal("101")).low(new BigDecimal("95.4")).close(new BigDecimal("96")).build();

        service.closeByStop("position", trigger, new BigDecimal("95.4"), "FIVE_MINUTE_STOP_SIMULATION");
        service.closeByStop("position", trigger, new BigDecimal("95.4"), "FIVE_MINUTE_STOP_SIMULATION");

        assertThat(open.getStatus()).isEqualTo(LaplacePositionStatus.CLOSED_BY_STOP_LOSS);
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo("95.4");
        assertThat(open.getGrossPnl()).isEqualByComparingTo("-1.1");
        assertThat(open.getExitFee()).isEqualByComparingTo("0.00756");
        assertThat(open.getNetPnl()).isEqualByComparingTo("-1.11556");
        verify(events, times(1)).save(any());
    }

    private LaplaceExecutionPriceProvider.Price price(String bid, String ask, String value, String type) {
        return new LaplaceExecutionPriceProvider.Price(new BigDecimal(value), new BigDecimal(bid),
                new BigDecimal(ask), type, "BOOK_TICKER");
    }

    private LaplacePaperPositionEntity openPosition(PositionSide side) {
        return LaplacePaperPositionEntity.builder().id("position").strategy(LaplacePaperExecutionService.STRATEGY)
                .strategyVersion("1.0").symbol("BTCUSDT").side(side).status(LaplacePositionStatus.OPEN)
                .entrySignalId("entry").entryCandleCloseTime(Instant.now().minusSeconds(3600))
                .entryTime(Instant.now().minusSeconds(1800)).entrySignalClosePrice(BigDecimal.valueOf(77))
                .entryExecutionPrice(BigDecimal.valueOf(100)).margin(BigDecimal.ONE)
                .quantity(new BigDecimal("0.2")).notional(new BigDecimal("20")).leverage(20)
                .entryFeeRate(new BigDecimal("0.0004")).entryFee(new BigDecimal("0.008")).build();
    }

    private LaplaceSignalResult signal() {
        Instant now = Instant.now();
        return new LaplaceSignalResult(LaplacePaperExecutionService.STRATEGY, "1.0", "BTCUSDT", "30m",
                "LAPLACE", 14, "CLOSE", false, now.minusSeconds(1800), now, 77,
                76, 75, 74, 1, 1, 10, 10, 1.0, .10, .1, .1, .03, .04, 2,
                LaplaceSignal.LONG, LaplaceSignal.LONG, StartupState.ACTIVE, 1, true, List.of());
    }
}
