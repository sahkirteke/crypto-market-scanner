package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.service.JsonlDecisionLogService;
import com.crypto.paper.model.KlineCandle;
import com.crypto.paper.model.PaperPositionEventType;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.entity.PaperPositionEventEntity;
import com.crypto.persistence.repository.PaperPositionEventRepository;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ExitEngineServiceIntrabarTest {
    private final Instant candleOpen = Instant.parse("2026-06-07T10:00:00Z");
    private final Instant candleClose = Instant.parse("2026-06-07T10:04:59Z");

    @Test
    void longCandleLowAtStopClosesWithStopLoss() {
        PaperPositionEntity result = service().evaluatePositionWithCandle(position(PositionSide.LONG), candle("105", "99", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(result.getRemainingPositionPct()).isEqualByComparingTo("0");
    }

    @Test
    void shortCandleHighAtStopClosesWithStopLoss() {
        PaperPositionEntity result = service().evaluatePositionWithCandle(position(PositionSide.SHORT), candle("101", "95", "98"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
    }

    @Test
    void longCandleHighAtTp1PartiallyCloses() {
        PaperPositionEntity result = service().evaluatePositionWithCandle(position(PositionSide.LONG), candle("102", "100", "101"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.PARTIALLY_CLOSED);
        assertThat(result.getTp1Hit()).isTrue();
        assertThat(result.getRemainingPositionPct()).isEqualByComparingTo("50");
    }

    @Test
    void shortCandleLowAtTp1PartiallyCloses() {
        PaperPositionEntity result = service().evaluatePositionWithCandle(position(PositionSide.SHORT), candle("100", "98", "99"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.PARTIALLY_CLOSED);
        assertThat(result.getTp1Hit()).isTrue();
        assertThat(result.getRemainingPositionPct()).isEqualByComparingTo("50");
    }

    @Test
    void longStopAndTpInSameCandleUsesStopFirstWithoutTp1Event() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        ExitEngineService service = service(events, mock(JsonlDecisionLogService.class));

        PaperPositionEntity result = service.evaluatePositionWithCandle(position(PositionSide.LONG), candle("102", "99", "101"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(result.getTp1Hit()).isFalse();
        ArgumentCaptor<PaperPositionEventEntity> captor = ArgumentCaptor.forClass(PaperPositionEventEntity.class);
        verify(events, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PaperPositionEventEntity::getEventType)
                .doesNotContain(PaperPositionEventType.PARTIAL_TP1);
    }

    @Test
    void shortStopAndTpInSameCandleUsesStopFirstWithoutTp1Event() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        ExitEngineService service = service(events, mock(JsonlDecisionLogService.class));

        PaperPositionEntity result = service.evaluatePositionWithCandle(position(PositionSide.SHORT), candle("101", "98", "99"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(result.getTp1Hit()).isFalse();
        ArgumentCaptor<PaperPositionEventEntity> captor = ArgumentCaptor.forClass(PaperPositionEventEntity.class);
        verify(events, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PaperPositionEventEntity::getEventType)
                .doesNotContain(PaperPositionEventType.PARTIAL_TP1);
    }

    @Test
    void tp1ActivatesTrailingAtCandleCloseTime() {
        PaperPositionEntity result = service().evaluatePositionWithCandle(position(PositionSide.LONG), candle("102", "100", "101"), "5m");

        assertThat(result.getTrailingActive()).isTrue();
        assertThat(result.getTrailingActivatedAtBarCloseTime()).isEqualTo(candleClose);
    }

    @Test
    void trailingDoesNotExitOnActivationCandle() {
        PaperPositionEntity p = position(PositionSide.LONG);
        PaperPositionEntity result = service().evaluatePositionWithCandle(p, candle("103", "100.01", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.PARTIALLY_CLOSED);
        assertThat(result.getExitReason()).isNull();
    }

    @Test
    void nextLongCandleCanExitWithTrailingStop() {
        ExitEngineService service = service();
        PaperPositionEntity p = position(PositionSide.LONG);
        service.evaluatePositionWithCandle(p, candle("102", "100", "101"), "5m");

        PaperPositionEntity result = service.evaluatePositionWithCandle(p, candle(candleOpen.plusSeconds(300), candleClose.plusSeconds(300), "103", "100", "101"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TRAILING_STOP");
    }

    @Test
    void nextShortCandleCanExitWithTrailingStop() {
        ExitEngineService service = service();
        PaperPositionEntity p = position(PositionSide.SHORT);
        service.evaluatePositionWithCandle(p, candle("100", "98", "99"), "5m");

        PaperPositionEntity result = service.evaluatePositionWithCandle(p, candle(candleOpen.plusSeconds(300), candleClose.plusSeconds(300), "100", "97", "99"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("TRAILING_STOP");
    }

    @Test
    void duplicateCandleCloseTimeIsSkipped() {
        ExitEngineService service = service();
        PaperPositionEntity p = position(PositionSide.LONG);
        service.evaluatePositionWithCandle(p, candle("102", "100", "101"), "5m");
        BigDecimal remaining = p.getRemainingPositionPct();

        PaperPositionEntity result = service.evaluatePositionWithCandle(p, candle("104", "100", "102"), "5m");

        assertThat(result.getRemainingPositionPct()).isEqualByComparingTo(remaining);
    }

    @Test
    void writesPaperPositionEvent() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        when(events.save(any(PaperPositionEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ExitEngineService service = service(events, null);
        PaperPositionEntity position = position(PositionSide.LONG);

        service.evaluatePositionWithCandle(position, candle("102", "100", "101"), "5m");

        ArgumentCaptor<PaperPositionEventEntity> captor = ArgumentCaptor.forClass(PaperPositionEventEntity.class);
        verify(events, atLeastOnce()).save(captor.capture());

        PaperPositionEventEntity event = captor.getAllValues().get(0);
        assertThat(event).isNotNull();
        assertThat(event.getPosition()).isEqualTo(position);
        assertThat(event.getEventType()).isNotNull();
        assertThat(captor.getAllValues()).extracting(PaperPositionEventEntity::getEventType)
                .contains(PaperPositionEventType.PARTIAL_TP1);
        assertThat(captor.getAllValues())
                .filteredOn(capturedEvent -> capturedEvent.getEventType() == PaperPositionEventType.PARTIAL_TP1)
                .first()
                .extracting(PaperPositionEventEntity::getDetailsJson)
                .asString()
                .contains("\"interval\":\"5m\"")
                .contains("\"candleCloseTime\":\"2026-06-07 13:04:59 TRT\"")
                .doesNotContain("2026-06-07T10:04:59Z");
    }

    @Test
    void callsJsonlServiceForPaperEvent() {
        JsonlDecisionLogService jsonl = mock(JsonlDecisionLogService.class);
        ExitEngineService service = service(null, jsonl);

        service.evaluatePositionWithCandle(position(PositionSide.LONG), candle("102", "100", "101"), "5m");

        verify(jsonl, atLeastOnce()).logPaper(any());
    }

    private ExitEngineService service() {
        return service(null, null);
    }

    private ExitEngineService service(PaperPositionEventRepository eventRepository, JsonlDecisionLogService jsonlDecisionLogService) {
        ExitEngineService service = new ExitEngineService(mock(PaperPositionRepository.class), mock(BinanceFuturesClient.class), properties());
        if (eventRepository != null) {
            when(eventRepository.save(any(PaperPositionEventEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            ReflectionTestUtils.setField(service, "eventRepository", eventRepository);
        }
        if (jsonlDecisionLogService != null) {
            ReflectionTestUtils.setField(service, "jsonlDecisionLogService", jsonlDecisionLogService);
        }
        return service;
    }

    private ScannerProperties properties() {
        ScannerProperties properties = new ScannerProperties();
        properties.getPaperRisk().setTp1ClosePct(new BigDecimal("50"));
        properties.getPaperRisk().setTp2ClosePct(new BigDecimal("25"));
        properties.getPaperRisk().setFeeBufferPct(new BigDecimal("0.0005"));
        properties.getPaperRisk().setTrailingAtrMultiplier(new BigDecimal("1.2"));
        return properties;
    }

    private KlineCandle candle(String high, String low, String close) {
        return candle(candleOpen, candleClose, high, low, close);
    }

    private KlineCandle candle(Instant openTime, Instant closeTime, String high, String low, String close) {
        return KlineCandle.builder()
                .openTime(openTime)
                .closeTime(closeTime)
                .open(new BigDecimal("100"))
                .high(new BigDecimal(high))
                .low(new BigDecimal(low))
                .close(new BigDecimal(close))
                .interval("5m")
                .build();
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
                .openedAt(Instant.now().minusSeconds(600))
                .currentStop(side == PositionSide.LONG ? new BigDecimal("99.5") : new BigDecimal("100.5"))
                .tp1(side == PositionSide.LONG ? new BigDecimal("101") : new BigDecimal("99"))
                .tp2(side == PositionSide.LONG ? new BigDecimal("103") : new BigDecimal("97"))
                .tp1Hit(false)
                .tp2Hit(false)
                .trailingActive(false)
                .remainingPositionPct(new BigDecimal("100"))
                .riskPerUnit(new BigDecimal("1"))
                .highestPriceSinceEntry(new BigDecimal("100"))
                .lowestPriceSinceEntry(new BigDecimal("100"))
                .barsInPosition(0)
                .build();
    }
}
