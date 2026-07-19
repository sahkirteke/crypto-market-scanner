package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LaplacePaperExecutionServiceTest {
    private LaplacePaperPositionRepository positions;
    private LaplaceExecutionPriceProvider prices;
    private LaplacePaperExecutionService service;

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
        service = new LaplacePaperExecutionService(positions, events, prices, new LaplacePnlCalculator(),
                properties, writer, universe);
    }

    @Test
    void everyLongEntryUsesAskAndFixedTwentyUsdtTwentyXSize() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.LONG_OPEN)).thenReturn(price("99", "100", "100", "ASK"));
        LaplacePaperPositionEntity opened = service.open(signal(), PositionSide.LONG, null, "FLAT");
        assertThat(opened.getEntryExecutionPrice()).isEqualByComparingTo("100");
        assertThat(opened.getEntrySignalClosePrice()).isEqualByComparingTo("77");
        assertThat(opened.getNotional()).isEqualByComparingTo("20");
        assertThat(opened.getMargin()).isEqualByComparingTo("1");
        assertThat(opened.getLeverage()).isEqualTo(20);
        assertThat(opened.getQuantity()).isEqualByComparingTo("0.2");
        assertThat(opened.getEntryFee()).isEqualByComparingTo("0.008");
    }

    @Test
    void everyShortEntryUsesBidAndFixedTwentyUsdtTwentyXSize() {
        when(positions.findOpenForUpdate(any(), any(), any())).thenReturn(List.of());
        when(prices.quote("BTCUSDT", MarketExecutionAction.SHORT_OPEN)).thenReturn(price("50", "51", "50", "BID"));
        LaplacePaperPositionEntity opened = service.open(signal(), PositionSide.SHORT, null, "FLAT");
        assertThat(opened.getEntryExecutionPrice()).isEqualByComparingTo("50");
        assertThat(opened.getNotional()).isEqualByComparingTo("20");
        assertThat(opened.getMargin()).isEqualByComparingTo("1");
        assertThat(opened.getLeverage()).isEqualTo(20);
        assertThat(opened.getQuantity()).isEqualByComparingTo("0.4");
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
                76, 75, 74, 1, 1, 10, 10, .1, .1, .03, .04, 2,
                LaplaceSignal.LONG, LaplaceSignal.LONG, StartupState.ACTIVE, 1, true, List.of());
    }
}
