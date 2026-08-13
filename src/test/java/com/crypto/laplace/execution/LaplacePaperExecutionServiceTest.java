package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.model.PositionManagementOutcome;
import com.crypto.laplace.model.LaplaceSignal;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.model.StartupState;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.persistence.LaplaceTradeEventRepository;
import com.crypto.laplace.service.StartupMarketUniverseService;
import com.crypto.laplace.service.LaplaceBreadthService;
import com.crypto.laplace.service.FiveMinuteKlineService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LaplacePaperExecutionServiceTest {
    @Test
    void springProductionConstructorIsExplicitlyAutowired() {
        assertThat(Arrays.stream(LaplacePaperExecutionService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count()).isEqualTo(1);
    }
    private LaplacePaperPositionRepository positions;
    private LaplaceExecutionPriceProvider prices;
    private LaplacePaperExecutionService service;
    private FiveMinuteKlineService fiveMinuteKlines;

    @BeforeEach
    void setUp() {
        positions = mock(LaplacePaperPositionRepository.class);
        LaplaceTradeEventRepository events = mock(LaplaceTradeEventRepository.class);
        prices = mock(LaplaceExecutionPriceProvider.class);
        LaplaceTradeJsonlWriter writer = mock(LaplaceTradeJsonlWriter.class);
        StartupMarketUniverseService universe = mock(StartupMarketUniverseService.class);
        when(writer.json(any())).thenReturn("{}");
        when(universe.symbols()).thenReturn(Set.of("BTCUSDT"));
        LaplaceStrategyProperties properties = new LaplaceStrategyProperties();
        fiveMinuteKlines = mock(FiveMinuteKlineService.class);
        service = new LaplacePaperExecutionService(positions, events, prices, new LaplacePnlCalculator(),
                properties, writer, universe, mock(LaplaceBreadthService.class), fiveMinuteKlines);
    }

    @Test
    void everyLongEntryUsesAskAndFixedSeventyFiveUsdtFifteenXSize() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN)).thenReturn(price("99", "100", "100", "ASK"));
        LaplaceSignalResult signal = signal();
        LaplacePaperPositionEntity opened = service.open(signal, PositionSide.LONG, null, "FLAT");
        assertThat(opened.getEntryExecutionPrice()).isEqualByComparingTo("100");
        assertThat(opened.getEntrySignalClosePrice()).isEqualByComparingTo("77");
        assertThat(opened.getNotional()).isEqualByComparingTo("75");
        assertThat(opened.getMargin()).isEqualByComparingTo("5");
        assertThat(opened.getLeverage()).isEqualTo(15);
        assertThat(opened.getQuantity()).isEqualByComparingTo("0.75");
        assertThat(opened.getEntryFee()).isEqualByComparingTo("0.030");
        assertThat(opened.getLastManagedFiveMinuteCandleCloseTime()).isEqualTo(signal.signalCandleCloseTime());
    }

    @Test
    void everyShortEntryUsesBidAndFixedSeventyFiveUsdtFifteenXSize() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.SHORT_OPEN)).thenReturn(price("50", "51", "50", "BID"));
        LaplacePaperPositionEntity opened = service.open(signal(), PositionSide.SHORT, null, "FLAT");
        assertThat(opened.getEntryExecutionPrice()).isEqualByComparingTo("50");
        assertThat(opened.getNotional()).isEqualByComparingTo("75");
        assertThat(opened.getMargin()).isEqualByComparingTo("5");
        assertThat(opened.getLeverage()).isEqualTo(15);
        assertThat(opened.getQuantity()).isEqualByComparingTo("1.5");
        assertThat(opened.getEntryFee()).isEqualByComparingTo("0.030");
    }

    @Test
    void rejectsEntryWhenOpenMarginsAndUnrealizedEntryFeesExhaustAvailableBalance() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        List<LaplacePaperPositionEntity> nineteenOpen = java.util.stream.IntStream.range(0, 19)
                .mapToObj(i -> LaplacePaperPositionEntity.builder().margin(new BigDecimal("5"))
                        .entryFee(new BigDecimal("0.030")).build()).toList();
        when(positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.CLOSED)).thenReturn(List.of());
        when(positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)).thenReturn(nineteenOpen);
        assertThat(service.availableBalance()).isEqualByComparingTo("4.430");
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN)).thenReturn(price("99", "100", "100", "ASK"));
        assertThatThrownBy(() -> service.open(signal(), PositionSide.LONG, null, "FLAT"))
                .isInstanceOf(IllegalStateException.class).hasMessage("INSUFFICIENT_PAPER_BALANCE");
    }

    @Test
    void longStopLossUsesExactFivePercentPriceAndExistingPnlFees() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.LONG);
        when(positions.findByIdForUpdate(open.getId())).thenReturn(java.util.Optional.of(open));
        Kline candle = stopCandle("94.99", "101");
        assertThat(service.closeAtStopLoss(open.getId(), candle)).isTrue();
        assertThat(open.getStatus()).isEqualTo(LaplacePositionStatus.CLOSED);
        assertThat(open.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo("95");
        assertThat(open.getGrossPnl()).isEqualByComparingTo("-2.5");
        assertThat(open.getExitFee()).isEqualByComparingTo("0.019");
        assertThat(open.getNetPnl()).isEqualByComparingTo("-2.539");
    }

    @Test
    void shortStopLossTriggersAtEqualityAndClosedPositionIsNeverProcessedAgain() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.SHORT);
        Kline candle = stopCandle("99", "105");
        when(positions.findByIdForUpdate(open.getId())).thenReturn(java.util.Optional.of(open));
        assertThat(service.closeAtStopLoss(open.getId(), candle)).isTrue();
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo("105");
        assertThat(open.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(service.closeAtStopLoss(open.getId(), candle)).isFalse();
    }

    @Test
    void candleThatDoesNotTouchStopKeepsPositionOpen() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.LONG);
        when(positions.findByIdForUpdate(open.getId())).thenReturn(java.util.Optional.of(open));
        assertThat(service.closeAtStopLoss(open.getId(), stopCandle("95.01", "110"))).isFalse();
        assertThat(open.getStatus()).isEqualTo(LaplacePositionStatus.OPEN);
    }

    @Test
    void longCloseUsesBidAndExitNotionalForTakerFee() {
        LaplacePaperPositionEntity open = openPosition(PositionSide.LONG);
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of(open));
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_CLOSE)).thenReturn(price("110", "111", "110", "BID"));
        service.reverse(signal(), open, PositionSide.SHORT, false);
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo("110");
        assertThat(open.getExitFee()).isEqualByComparingTo("0.022");
        assertThat(open.getGrossPnl()).isEqualByComparingTo("5");
        assertThat(open.getNetPnl()).isEqualByComparingTo("4.958");
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
    void longPartialThenOppositeSignalClosesOnlyRunnerWithTwoLegPnl() {
        LaplacePaperPositionEntity open = kural5Position(PositionSide.LONG);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        assertThat(service.evaluateClosedFiveMinuteCandle("position", managementCandle("99", "103", "2026-08-06T10:34:59.999Z")))
                .isEqualTo(PositionManagementOutcome.PARTIAL_EXIT);
        assertThat(open.getPartialTakeProfitPrice()).isEqualByComparingTo("103");
        assertThat(open.getPartialTakeProfitQuantity()).isEqualByComparingTo("0.1875");
        assertThat(open.getRemainingQuantity()).isEqualByComparingTo("0.5625");
        assertThat(open.getPartialGrossPnl()).isEqualByComparingTo("0.5625");
        assertThat(open.getPartialExitNotional()).isEqualByComparingTo("19.3125");
        assertThat(open.getPartialExitFee()).isEqualByComparingTo("0.007725");
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of(open));
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_CLOSE)).thenReturn(price("104", "105", "104", "BID"));
        service.closeForOppositeSignal(signal(), open, PositionSide.SHORT);
        assertThat(open.getGrossPnl()).isEqualByComparingTo("2.8125");
        assertThat(open.getExitFee()).isEqualByComparingTo("0.031125");
        assertThat(open.getNetPnl()).isEqualByComparingTo("2.751375");
        assertThat(open.getRemainingQuantity()).isZero();
        assertThat(open.getExitReason()).isEqualTo("OPPOSITE_CONFIRMED_LAPLACE_SIGNAL");
    }

    @Test
    void shortPartialThenOppositeSignalUsesExpectedTwoLegPnl() {
        LaplacePaperPositionEntity open = kural5Position(PositionSide.SHORT);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        service.evaluateClosedFiveMinuteCandle("position", managementCandle("97", "101", "2026-08-06T10:34:59.999Z"));
        assertThat(open.getPartialExitFee()).isEqualByComparingTo("0.007275");
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of(open));
        when(prices.quote("BTCUSDT", MarketExecutionAction.SHORT_CLOSE)).thenReturn(price("95", "96", "96", "ASK"));
        service.closeForOppositeSignal(signal(), open, PositionSide.LONG);
        assertThat(open.getGrossPnl()).isEqualByComparingTo("2.8125");
        assertThat(open.getExitFee()).isEqualByComparingTo("0.028875");
        assertThat(open.getNetPnl()).isEqualByComparingTo("2.753625");
    }

    @Test
    void hardStopHasPriorityWhenSameCandleAlsoTouchesPartialTarget() {
        LaplacePaperPositionEntity open = kural5Position(PositionSide.LONG);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        assertThat(service.evaluateClosedFiveMinuteCandle("position", managementCandle("94", "104", "2026-08-06T10:34:59.999Z")))
                .isEqualTo(PositionManagementOutcome.CLOSED_STOP_LOSS);
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo("95");
        assertThat(open.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(open.getPartialTakeProfitExecuted()).isFalse();
        assertThat(open.getPartialTakeProfitQuantity()).isNull();
    }

    @Test
    void breakEvenIsArmedOnTargetCandleAndRunsOnlyOnNextCandle() {
        LaplacePaperPositionEntity open = kural5Position(PositionSide.LONG);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        Kline target = managementCandle("99", "103", "2026-08-06T10:34:59.999Z");
        assertThat(service.evaluateClosedFiveMinuteCandle("position", target)).isEqualTo(PositionManagementOutcome.PARTIAL_EXIT);
        assertThat(open.getStatus()).isEqualTo(LaplacePositionStatus.OPEN);
        assertThat(open.getExitReason()).isNull();
        BigDecimal be = open.getBreakEvenStopPrice();
        Kline next = managementCandle(be.subtract(new BigDecimal("0.01")).toPlainString(), "104", "2026-08-06T10:39:59.999Z");
        assertThat(service.evaluateClosedFiveMinuteCandle("position", next)).isEqualTo(PositionManagementOutcome.CLOSED_BREAK_EVEN);
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo(be);
        assertThat(open.getExitReason()).isEqualTo("BREAK_EVEN_AFTER_PARTIAL");
        assertThat(open.getRemainingQuantity()).isZero();
    }

    @Test
    void repeatedPartialCandleIsIdempotent() {
        LaplacePaperPositionEntity open = kural5Position(PositionSide.LONG);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        Kline candle = managementCandle("99", "103", "2026-08-06T10:34:59.999Z");
        assertThat(service.evaluateClosedFiveMinuteCandle("position", candle)).isEqualTo(PositionManagementOutcome.PARTIAL_EXIT);
        BigDecimal fee = open.getPartialExitFee();
        assertThat(service.evaluateClosedFiveMinuteCandle("position", candle)).isEqualTo(PositionManagementOutcome.ALREADY_PROCESSED);
        assertThat(open.getRemainingQuantity()).isEqualByComparingTo("0.5625");
        assertThat(open.getPartialExitFee()).isEqualByComparingTo(fee);
    }

    @Test
    void shortBreakEvenUsesFeeAdjustedExactPriceOnLaterCandle() {
        LaplacePaperPositionEntity open = kural5Position(PositionSide.SHORT);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        service.evaluateClosedFiveMinuteCandle("position", managementCandle("97", "101", "2026-08-06T10:34:59.999Z"));
        BigDecimal expected = new BigDecimal("100").multiply(BigDecimal.ONE.subtract(new BigDecimal("0.0004")))
                .divide(BigDecimal.ONE.add(new BigDecimal("0.0004")), 12, java.math.RoundingMode.HALF_UP);
        assertThat(open.getBreakEvenStopPrice()).isEqualByComparingTo(expected);
        Kline next = managementCandle("96", expected.add(new BigDecimal("0.01")).toPlainString(), "2026-08-06T10:39:59.999Z");
        assertThat(service.evaluateClosedFiveMinuteCandle("position", next)).isEqualTo(PositionManagementOutcome.CLOSED_BREAK_EVEN);
        assertThat(open.getExitExecutionPrice()).isEqualByComparingTo(expected);
    }


    @Test
    void shortAcceptanceLoadsFullConfiguredHistory() {
        LaplacePaperPositionEntity open = kural5Position(PositionSide.SHORT);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        Kline candle=Kline.builder().closeTime(Instant.parse("2026-08-06T12:04:59.999Z")).low(new BigDecimal("99")).high(new BigDecimal("101")).close(new BigDecimal("101.6")).build();
        assertThatThrownBy(() -> service.evaluateClosedFiveMinuteCandle("position",candle)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(fiveMinuteKlines).loadLastClosedThrough("BTCUSDT",candle.getCloseTime(),500);
    }

    @Test
    void longFastExitDoesNotRequirePreviousNegativeSlope() {
        LaplacePaperPositionEntity open=kural5Position(PositionSide.LONG);open.setFastExitMode(true);
        when(positions.findByIdForUpdate("position")).thenReturn(java.util.Optional.of(open));
        when(prices.quote("BTCUSDT",MarketExecutionAction.LONG_CLOSE)).thenReturn(price("75","76","75","BID"));
        Instant now=Instant.parse("2026-08-06T12:00:00Z");LaplaceSignalResult signal=new LaplaceSignalResult(LaplacePaperExecutionService.STRATEGY,"1.0","BTCUSDT","30m","LAPLACE",14,"CLOSE",false,now.minusSeconds(1800),now,75,76,77,78,-1,1,10,10,-.02,.50,.03,.04,2,LaplaceSignal.NONE,LaplaceSignal.NONE,StartupState.ACTIVE,1,false,List.of());
        assertThat(service.closeLongFastRegime(signal,open)).isTrue();assertThat(open.getExitReason()).isEqualTo("EXIT_5C_LONG_FAST_REGIME");
    }

    private Kline stopCandle(String low, String high) {
        Instant close = Instant.parse("2026-08-06T10:05:00Z");
        return Kline.builder().symbol("BTCUSDT").interval("5m").openTime(close.minusSeconds(300))
                .closeTime(close).low(new BigDecimal(low)).high(new BigDecimal(high)).closed(true).build();
    }

    private Kline managementCandle(String low, String high, String closeTime) {
        Instant close = Instant.parse(closeTime);
        return Kline.builder().symbol("BTCUSDT").interval("5m").openTime(close.minusMillis(299_999))
                .closeTime(close).low(new BigDecimal(low)).high(new BigDecimal(high)).closed(true).build();
    }

    private LaplacePaperPositionEntity kural5Position(PositionSide side) {
        Instant entryClose = Instant.parse("2026-08-06T10:29:59.999Z");
        return LaplacePaperPositionEntity.builder().id("position").strategy(LaplacePaperExecutionService.STRATEGY)
                .strategyVersion("KURAL5_V1").symbol("BTCUSDT").side(side).status(LaplacePositionStatus.OPEN)
                .entrySignalId("entry").entryCandleCloseTime(entryClose).lastManagedFiveMinuteCandleCloseTime(entryClose)
                .entryTime(entryClose.minusSeconds(60)).entrySignalClosePrice(BigDecimal.valueOf(100))
                .entryExecutionPrice(BigDecimal.valueOf(100)).margin(new BigDecimal("5"))
                .quantity(new BigDecimal("0.75")).originalQuantity(new BigDecimal("0.75"))
                .remainingQuantity(new BigDecimal("0.75")).notional(new BigDecimal("75"))
                .originalNotional(new BigDecimal("75")).remainingEntryNotional(new BigDecimal("75"))
                .leverage(15).partialTakeProfitExecuted(false).cumulativeExitFee(BigDecimal.ZERO)
                .realizedGrossPnl(BigDecimal.ZERO).entryFeeRate(new BigDecimal("0.0004"))
                .entryFee(new BigDecimal("0.030")).build();
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
                .entryExecutionPrice(BigDecimal.valueOf(100)).margin(new BigDecimal("5"))
                .quantity(new BigDecimal("0.5")).notional(new BigDecimal("50")).leverage(10)
                .entryFeeRate(new BigDecimal("0.0004")).entryFee(new BigDecimal("0.020")).build();
    }

    private LaplaceSignalResult signal() {
        Instant now = Instant.now();
        return new LaplaceSignalResult(LaplacePaperExecutionService.STRATEGY, "1.0", "BTCUSDT", "30m",
                "LAPLACE", 14, "CLOSE", false, now.minusSeconds(1800), now, 77,
                76, 75, 74, 1, 1, 10, 10, .1, .1, .03, .04, 2,
                LaplaceSignal.LONG, LaplaceSignal.LONG, StartupState.ACTIVE, 1, true, List.of());
    }
}
