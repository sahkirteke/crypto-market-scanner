package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ExitEngineServiceIntrabarTest {
    private final Instant candleOpen = Instant.parse("2026-06-07T10:00:00Z");
    private final Instant candleClose = Instant.parse("2026-06-07T10:04:59Z");

    @Test
    void candleClosedBeforePositionOpenedIsSkipped() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        JsonlDecisionLogService jsonl = mock(JsonlDecisionLogService.class);
        ExitEngineService service = service(events, jsonl);
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.parse("2026-06-07T10:03:59Z"));

        PaperPositionEntity result = service.evaluatePositionWithCandle(position,
                candle(Instant.parse("2026-06-07T09:55:00Z"), Instant.parse("2026-06-07T09:59:59Z"), "105", "99", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(result.getExitReason()).isNull();
        assertThat(result.getLastExitCandleCloseTime()).isNull();
        verify(events, never()).save(any(PaperPositionEventEntity.class));
        verify(jsonl, never()).logPaper(any());
        verify(jsonl, never()).logPaperTrade(any());
    }

    @Test
    void candleContainingPositionOpenIsSkipped() {
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.parse("2026-06-07T10:03:59Z"));

        PaperPositionEntity result = service().evaluatePositionWithCandle(position,
                candle(Instant.parse("2026-06-07T10:00:00Z"), Instant.parse("2026-06-07T10:04:59Z"), "105", "99", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.OPEN);
        assertThat(result.getExitReason()).isNull();
        assertThat(result.getLastExitCandleCloseTime()).isNull();
    }

    @Test
    void firstFullyFormedCandleAfterPositionOpenIsEvaluated() {
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.parse("2026-06-07T10:03:59Z"));

        PaperPositionEntity result = service().evaluatePositionWithCandle(position,
                candle(Instant.parse("2026-06-07T10:05:00Z"), Instant.parse("2026-06-07T10:09:59Z"), "105", "98.4", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(result.getLastExitCandleCloseTime()).isEqualTo(Instant.parse("2026-06-07T10:09:59Z"));
    }

    @Test
    void closeTimeBeforeOpenedAtIsCorrected() {
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setOpenedAt(Instant.parse("2999-06-07T10:03:59Z"));
        ExitEngineService service = service();

        ReflectionTestUtils.invokeMethod(service, "closeRemaining", position, new BigDecimal("99.5"),
                com.crypto.paper.model.PaperExitReason.STOP_LOSS, Instant.parse("2999-06-07T09:59:59Z"));

        assertThat(position.getClosedAt()).isNotNull();
        assertThat(position.getClosedAt()).isAfterOrEqualTo(position.getOpenedAt());
    }

    @Test
    void longCandleLowAtStopClosesWithStopLoss() {
        PaperPositionEntity result = service().evaluatePositionWithCandle(position(PositionSide.LONG), candle("105", "98.4", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(result.getExitReason()).isEqualTo("STOP_LOSS");
        assertThat(result.getRemainingPositionPct()).isEqualByComparingTo("0");
    }

    @Test
    void shortCandleHighAtStopClosesWithStopLoss() {
        PaperPositionEntity result = service().evaluatePositionWithCandle(position(PositionSide.SHORT), candle("101.6", "95", "98"), "5m");

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

        PaperPositionEntity result = service.evaluatePositionWithCandle(position(PositionSide.LONG), candle("102", "98.4", "101"), "5m");

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

        PaperPositionEntity result = service.evaluatePositionWithCandle(position(PositionSide.SHORT), candle("101.6", "98", "99"), "5m");

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
    void longTrailingUpdateDoesNotExitWithNewStopUntilNextCandle() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        ExitEngineService service = service(events, null);
        PaperPositionEntity p = positionWithTp2BeyondTrailingUpdateCandle(PositionSide.LONG);

        PaperPositionEntity result = service.evaluatePositionWithCandle(p, candle("103", "101", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.PARTIALLY_CLOSED);
        assertThat(result.getExitReason()).isNull();
        assertThat(result.getCurrentStop()).isEqualByComparingTo("101.8");
        ArgumentCaptor<PaperPositionEventEntity> captor = ArgumentCaptor.forClass(PaperPositionEventEntity.class);
        verify(events, atLeastOnce()).save(captor.capture());
        assertThat(result.getRemainingPositionPct()).isEqualByComparingTo("50");
        List<PaperPositionEventType> eventTypes = captor.getAllValues().stream()
                .map(PaperPositionEventEntity::getEventType)
                .toList();
        assertThat(eventTypes)
                .contains(PaperPositionEventType.PARTIAL_TP1, PaperPositionEventType.STOP_UPDATED, PaperPositionEventType.TRAILING_UPDATED)
                .doesNotContain(PaperPositionEventType.PARTIAL_TP2, PaperPositionEventType.TRAILING_STOP, PaperPositionEventType.CLOSED);

        PaperPositionEntity next = service.evaluatePositionWithCandle(p,
                candle(candleOpen.plusSeconds(300), candleClose.plusSeconds(300), "102", "101.7", "101.9"), "5m");

        assertThat(next.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(next.getExitReason()).isEqualTo("TRAILING_STOP");
    }

    @Test
    void shortTrailingUpdateDoesNotExitWithNewStopUntilNextCandle() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        ExitEngineService service = service(events, null);
        PaperPositionEntity p = positionWithTp2BeyondTrailingUpdateCandle(PositionSide.SHORT);

        PaperPositionEntity result = service.evaluatePositionWithCandle(p, candle("99", "97", "98"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.PARTIALLY_CLOSED);
        assertThat(result.getExitReason()).isNull();
        assertThat(result.getCurrentStop()).isEqualByComparingTo("98.2");
        ArgumentCaptor<PaperPositionEventEntity> captor = ArgumentCaptor.forClass(PaperPositionEventEntity.class);
        verify(events, atLeastOnce()).save(captor.capture());
        assertThat(result.getRemainingPositionPct()).isEqualByComparingTo("50");
        List<PaperPositionEventType> eventTypes = captor.getAllValues().stream()
                .map(PaperPositionEventEntity::getEventType)
                .toList();
        assertThat(eventTypes)
                .contains(PaperPositionEventType.PARTIAL_TP1, PaperPositionEventType.STOP_UPDATED, PaperPositionEventType.TRAILING_UPDATED)
                .doesNotContain(PaperPositionEventType.PARTIAL_TP2, PaperPositionEventType.TRAILING_STOP, PaperPositionEventType.CLOSED);

        PaperPositionEntity next = service.evaluatePositionWithCandle(p,
                candle(candleOpen.plusSeconds(300), candleClose.plusSeconds(300), "98.3", "98", "98.1"), "5m");

        assertThat(next.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(next.getExitReason()).isEqualTo("TRAILING_STOP");
    }

    @Test
    void tp1WritesPartialBeforeStopUpdatedWithPostPartialState() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        ExitEngineService service = service(events, null);

        service.evaluatePositionWithCandle(position(PositionSide.LONG), candle("102", "100", "101"), "5m");

        ArgumentCaptor<PaperPositionEventEntity> captor = ArgumentCaptor.forClass(PaperPositionEventEntity.class);
        verify(events, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PaperPositionEventEntity::getEventType)
                .startsWith(PaperPositionEventType.PARTIAL_TP1, PaperPositionEventType.STOP_UPDATED);
        PaperPositionEventEntity stopUpdated = captor.getAllValues().stream()
                .filter(event -> event.getEventType() == PaperPositionEventType.STOP_UPDATED)
                .findFirst()
                .orElseThrow();
        assertThat(stopUpdated.getDetailsJson())
                .contains("\"remainingPositionPctBefore\":50")
                .contains("\"remainingPositionPctAfter\":50")
                .contains("\"tp1HitBefore\":true")
                .contains("\"tp1HitAfter\":true");
    }

    @Test
    void sameCandleTp1AndTp2WritesExpectedOrderWithoutTrailingExit() {
        PaperPositionEventRepository events = mock(PaperPositionEventRepository.class);
        ExitEngineService service = service(events, null);

        PaperPositionEntity result = service.evaluatePositionWithCandle(position(PositionSide.LONG), candle("103", "100", "102"), "5m");

        assertThat(result.getStatus()).isEqualTo(PaperPositionStatus.PARTIALLY_CLOSED);
        assertThat(result.getExitReason()).isNull();
        ArgumentCaptor<PaperPositionEventEntity> captor = ArgumentCaptor.forClass(PaperPositionEventEntity.class);
        verify(events, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PaperPositionEventEntity::getEventType)
                .containsExactly(
                        PaperPositionEventType.PARTIAL_TP1,
                        PaperPositionEventType.STOP_UPDATED,
                        PaperPositionEventType.PARTIAL_TP2,
                        PaperPositionEventType.TRAILING_UPDATED)
                .doesNotContain(PaperPositionEventType.TRAILING_STOP, PaperPositionEventType.CLOSED);
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
    void callsJsonlServiceForPaperEventAndExitTrade() {
        JsonlDecisionLogService jsonl = mock(JsonlDecisionLogService.class);
        ExitEngineService service = service(null, jsonl);

        service.evaluatePositionWithCandle(position(PositionSide.LONG), candle("105", "98.4", "102"), "5m");

        verify(jsonl, atLeastOnce()).logPaper(any());
        verify(jsonl).logPaperTrade(any());
    }

    @Test
    void writesShortExitTradeWithRealizedPnl() {
        JsonlDecisionLogService jsonl = mock(JsonlDecisionLogService.class);
        ExitEngineService service = service(null, jsonl);
        PaperPositionEntity position = position(PositionSide.SHORT);
        position.setEntryPrice(new BigDecimal("107.40"));
        position.setQuantity(new BigDecimal("0.4"));
        position.setCurrentStop(new BigDecimal("109.1184"));

        service.evaluatePositionWithCandle(position, candle("109.1184", "106", "107.50"), "5m");

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(jsonl).logPaperTrade(captor.capture());
        assertThat(captor.getValue()).containsEntry("type", "EXIT");
        assertThat(captor.getValue()).containsEntry("positionId", 1L);
        assertThat((BigDecimal) captor.getValue().get("realizedPnl")).isEqualByComparingTo("-0.68736");
        assertThat(captor.getValue()).containsEntry("firstHit", "SL_FIRST");
        assertThat(captor.getValue()).containsEntry("exitTrigger", "SL_5M");
    }

    @Test
    void writesLongExitTradeWithRealizedPnl() {
        JsonlDecisionLogService jsonl = mock(JsonlDecisionLogService.class);
        ExitEngineService service = service(null, jsonl);
        PaperPositionEntity position = position(PositionSide.LONG);
        position.setEntryPrice(new BigDecimal("100"));
        position.setQuantity(new BigDecimal("2"));
        position.setTp1(null);
        position.setTp2(null);
        position.setCurrentStop(null);

        service.evaluatePosition(position, new BigDecimal("101"));

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(jsonl).logPaperTrade(captor.capture());
        assertThat(captor.getValue()).containsEntry("type", "EXIT");
        assertThat(captor.getValue()).containsEntry("positionId", 1L);
        assertThat((BigDecimal) captor.getValue().get("realizedPnl")).isEqualByComparingTo("2");
        assertThat(captor.getValue()).containsEntry("exitReason", "TAKE_PROFIT");
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


    private PaperPositionEntity positionWithTp2BeyondTrailingUpdateCandle(PositionSide side) {
        PaperPositionEntity position = position(side);
        position.setTp2(side == PositionSide.LONG ? new BigDecimal("104") : new BigDecimal("96"));
        return position;
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
                .openedAt(candleOpen.minusSeconds(60))
                .currentStop(side == PositionSide.LONG ? new BigDecimal("98.4") : new BigDecimal("101.6"))
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
