package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.api.dto.ManualClosePaperPositionRequest;
import com.crypto.api.exception.BadRequestException;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Ticker24h;
import com.crypto.paper.model.PaperExitReason;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperPositionManualCloseServiceTest {
    private PaperPositionRepository repository;
    private BinanceFuturesClient binanceFuturesClient;
    private PaperPositionManualCloseService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaperPositionRepository.class);
        binanceFuturesClient = mock(BinanceFuturesClient.class);
        ExitEngineService exitEngineService = new ExitEngineService(repository, binanceFuturesClient, new ScannerProperties());
        service = new PaperPositionManualCloseService(repository, binanceFuturesClient, exitEngineService);
        when(repository.save(any(PaperPositionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void manualCloseUsesRequestExitPriceWithoutCallingBinance() {
        PaperPositionEntity position = openPosition(PositionSide.LONG, "100", "1000");
        when(repository.findById(1L)).thenReturn(Optional.of(position));

        PaperPositionEntity closed = service.closeManually(
                1L,
                new ManualClosePaperPositionRequest(new BigDecimal("110"), "Manual test close")
        );

        assertThat(closed.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(closed.getExitPrice()).isEqualByComparingTo("110");
        assertThat(closed.getExitDetail()).isEqualTo("Manual test close");
        assertThat(closed.getExitReason()).isEqualTo(PaperExitReason.MANUAL_CLOSE.name());
        verify(binanceFuturesClient, never()).getAll24hTickers();
    }

    @Test
    void manualCloseUsesBinanceTickerPriceWhenRequestExitPriceMissing() {
        PaperPositionEntity position = openPosition(PositionSide.LONG, "100", "1000");
        when(repository.findById(1L)).thenReturn(Optional.of(position));
        when(binanceFuturesClient.getAll24hTickers()).thenReturn(List.of(
                Ticker24h.builder().symbol("ETHUSDT").lastPrice(new BigDecimal("2000")).build(),
                Ticker24h.builder().symbol("BTCUSDT").lastPrice(new BigDecimal("105")).build()
        ));

        PaperPositionEntity closed = service.closeManually(1L, new ManualClosePaperPositionRequest(null, null));

        assertThat(closed.getExitPrice()).isEqualByComparingTo("105");
        assertThat(closed.getExitDetail()).isEqualTo("Manual close");
        verify(binanceFuturesClient).getAll24hTickers();
    }

    @Test
    void manualCloseThrowsBadRequestForAlreadyClosedPosition() {
        PaperPositionEntity position = openPosition(PositionSide.LONG, "100", "1000");
        position.setStatus(PaperPositionStatus.CLOSED);
        when(repository.findById(1L)).thenReturn(Optional.of(position));

        assertThatThrownBy(() -> service.closeManually(1L, new ManualClosePaperPositionRequest(new BigDecimal("110"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Position already closed");
    }

    @Test
    void manualCloseThrowsBadRequestWhenCurrentPriceNotFound() {
        PaperPositionEntity position = openPosition(PositionSide.LONG, "100", "1000");
        when(repository.findById(1L)).thenReturn(Optional.of(position));
        when(binanceFuturesClient.getAll24hTickers()).thenReturn(List.of(
                Ticker24h.builder().symbol("ETHUSDT").lastPrice(new BigDecimal("2000")).build()
        ));

        assertThatThrownBy(() -> service.closeManually(1L, new ManualClosePaperPositionRequest(null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current price not found for symbol: BTCUSDT");
    }

    @Test
    void manualCloseCalculatesLongPnl() {
        PaperPositionEntity position = openPosition(PositionSide.LONG, "100", "1000");
        when(repository.findById(1L)).thenReturn(Optional.of(position));

        PaperPositionEntity closed = service.closeManually(
                1L,
                new ManualClosePaperPositionRequest(new BigDecimal("110"), null)
        );

        assertThat(closed.getRealizedPnlPct()).isEqualByComparingTo("10");
        assertThat(closed.getRealizedPnlUsdt()).isEqualByComparingTo("100");
    }

    @Test
    void manualCloseCalculatesShortPnl() {
        PaperPositionEntity position = openPosition(PositionSide.SHORT, "100", "1000");
        when(repository.findById(1L)).thenReturn(Optional.of(position));

        PaperPositionEntity closed = service.closeManually(
                1L,
                new ManualClosePaperPositionRequest(new BigDecimal("90"), null)
        );

        assertThat(closed.getRealizedPnlPct()).isEqualByComparingTo("10");
        assertThat(closed.getRealizedPnlUsdt()).isEqualByComparingTo("100");
    }

    private PaperPositionEntity openPosition(PositionSide side, String entryPrice, String notionalUsdt) {
        return PaperPositionEntity.builder()
                .id(1L)
                .symbol("BTCUSDT")
                .side(side)
                .status(PaperPositionStatus.OPEN)
                .entryAction(side == PositionSide.SHORT ? EntryAction.ENTER_SHORT : EntryAction.ENTER_LONG)
                .entryPrice(new BigDecimal(entryPrice))
                .quantity(BigDecimal.ONE)
                .notionalUsdt(new BigDecimal(notionalUsdt))
                .leverage(1)
                .openedAt(Instant.now().minusSeconds(3600))
                .build();
    }
}
