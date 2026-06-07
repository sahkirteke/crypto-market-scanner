package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Ticker24h;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExitEngineServiceTest {

    @Test
    void calculatesLongPnlPct() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        BigDecimal pnlPct = service.calculateUnrealizedPnlPct(position(PositionSide.LONG), new BigDecimal("102"));

        assertThat(pnlPct).isEqualByComparingTo("2.00000000");
    }

    @Test
    void calculatesShortPnlPct() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        BigDecimal pnlPct = service.calculateUnrealizedPnlPct(position(PositionSide.SHORT), new BigDecimal("98"));

        assertThat(pnlPct).isEqualByComparingTo("2.00000000");
    }

    @Test
    void triggersLongStopLoss() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.LONG), new BigDecimal("99.3"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
    }

    @Test
    void triggersLongTakeProfit() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.LONG), new BigDecimal("101.1"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TAKE_PROFIT");
    }

    @Test
    void triggersShortStopLoss() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.SHORT), new BigDecimal("100.7"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
    }

    @Test
    void triggersShortTakeProfit() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));

        PaperPositionEntity result = service.evaluatePosition(position(PositionSide.SHORT), new BigDecimal("98.9"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TAKE_PROFIT");
    }

    @Test
    void timeStopClosesWhenPnlIsNonPositive() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.now().minusSeconds(300L * 60L));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("99.9"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TIME_STOP");
    }

    @Test
    void timeStopDoesNotClosePositivePnlWhenConfiguredToCloseOnlyNonPositive() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.now().minusSeconds(300L * 60L));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("100.1"));

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(result.getExitReason()).isNull();
    }

    @Test
    void calculatesLongFavorableAndAdverseMoves() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setHighestPrice(new BigDecimal("103"));
        position.setLowestPrice(new BigDecimal("99"));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("102"));

        assertThat(result.getMaxFavorableMovePct()).isEqualByComparingTo("3.00000000");
        assertThat(result.getMaxAdverseMovePct()).isEqualByComparingTo("-1.00000000");
    }

    @Test
    void calculatesShortFavorableAndAdverseMoves() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.SHORT);
        position.setHighestPrice(new BigDecimal("101"));
        position.setLowestPrice(new BigDecimal("97"));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("98"));

        assertThat(result.getMaxFavorableMovePct()).isEqualByComparingTo("3.00000000");
        assertThat(result.getMaxAdverseMovePct()).isEqualByComparingTo("-1.00000000");
    }

    @Test
    void calculatesMinutesHeldAndBarsHeld() {
        ExitEngineService service = service(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class));
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.now().minusSeconds(125L * 60L));

        PaperPositionEntity result = service.evaluatePosition(position, new BigDecimal("100.1"));

        assertThat(result.getMinutesHeld()).isBetween(125, 126);
        assertThat(result.getBarsHeld()).isEqualTo(result.getMinutesHeld() / 60);
    }

    @Test
    void evaluateOpenPositionsDoesNotClosePositionWhenPriceIsMissing() {
        PaperPositionRepository repository = mock(PaperPositionRepository.class);
        BinanceFuturesClient client = mock(BinanceFuturesClient.class);
        PaperPositionEntity position = position(PositionSide.LONG);
        when(repository.findByStatusInOrderByOpenedAtDesc(List.of(
                PaperPositionStatus.OPEN,
                PaperPositionStatus.PARTIALLY_CLOSED
        ))).thenReturn(List.of(position));
        when(client.getAll24hTickers()).thenReturn(List.of(Ticker24h.builder()
                .symbol("ETHUSDT")
                .lastPrice(new BigDecimal("200"))
                .build()));
        ExitEngineService service = service(repository, client);

        List<PaperPositionEntity> closed = service.evaluateOpenPositions();

        assertThat(closed).isEmpty();
        assertThat(position.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        verify(repository, never()).save(position);
    }

    private ExitEngineService service(PaperPositionRepository repository, BinanceFuturesClient client) {
        return new ExitEngineService(repository, client, properties());
    }

    private ScannerProperties properties() {
        ScannerProperties properties = new ScannerProperties();
        ScannerProperties.PaperExit paperExit = new ScannerProperties.PaperExit();
        paperExit.setTakeProfitPct(new BigDecimal("1.0"));
        paperExit.setStopLossPct(new BigDecimal("0.6"));
        paperExit.setTimeStopMinutes(240);
        paperExit.setTimeStopCloseOnlyIfNonPositive(true);
        paperExit.setBarMinutes(60);
        properties.setPaperExit(paperExit);
        return properties;
    }

    private PaperPositionEntity position(PositionSide side) {
        return PaperPositionEntity.builder()
                .id(1L)
                .symbol("BTCUSDT")
                .side(side)
                .status(PaperPositionStatus.OPEN)
                .entryAction(side == PositionSide.LONG ? EntryAction.ENTER_LONG : EntryAction.ENTER_SHORT)
                .entryPrice(new BigDecimal("100"))
                .quantity(BigDecimal.ONE)
                .notionalUsdt(new BigDecimal("100"))
                .leverage(3)
                .openedAt(Instant.now())
                .takeProfitPct(new BigDecimal("1.0"))
                .stopLossPct(new BigDecimal("0.6"))
                .timeStopMinutes(240)
                .build();
    }
}
