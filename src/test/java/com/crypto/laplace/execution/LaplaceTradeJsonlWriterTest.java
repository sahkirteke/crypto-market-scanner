package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.config.JacksonConfig;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.persistence.LaplaceTradeEventEntity;
import com.crypto.laplace.persistence.LaplaceTradeEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class LaplaceTradeJsonlWriterTest {
    @TempDir java.nio.file.Path tempDir;

    @Test
    void serializesInstantAsIsoString() {
        LaplaceTradeJsonlWriter writer = writer(mock(LaplaceTradeEventRepository.class), new JacksonConfig().objectMapper());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signalCandleCloseTime", Instant.parse("2026-07-18T00:29:59.999Z"));
        assertThat(writer.json(payload)).isEqualTo("{\"signalCandleCloseTime\":\"2026-07-18T00:29:59.999Z\"}");
    }

    @Test
    void serializesAllLaplaceTimeFieldsAsIsoStrings() {
        LaplaceTradeJsonlWriter writer = writer(mock(LaplaceTradeEventRepository.class), new JacksonConfig().objectMapper());
        Map<String, Object> payload = new LinkedHashMap<>();
        Instant instant = Instant.parse("2026-07-18T00:29:59.999Z");
        payload.put("signalCandleOpenTime", instant.minusSeconds(1800));
        payload.put("signalCandleCloseTime", instant);
        payload.put("signalDetectedAt", instant.plusSeconds(1));
        payload.put("executionRequestedAt", instant.plusSeconds(2));
        payload.put("executionTime", instant.plusSeconds(3));
        payload.put("entryTime", instant.plusSeconds(4));
        payload.put("exitTime", instant.plusSeconds(5));
        payload.put("createdAt", instant.plusSeconds(6));
        payload.put("publishedAt", instant.plusSeconds(7));
        payload.put("localDateTime", LocalDateTime.parse("2026-07-18T03:29:59.999"));
        payload.put("offsetDateTime", OffsetDateTime.parse("2026-07-18T03:29:59.999+03:00"));
        payload.put("zonedDateTime", ZonedDateTime.parse("2026-07-18T03:29:59.999+03:00[Europe/Istanbul]"));
        payload.put("localDate", LocalDate.parse("2026-07-18"));
        payload.put("duration", Duration.ofMinutes(30));
        String json = writer.json(payload);
        assertThat(json).contains("\"signalCandleCloseTime\":\"2026-07-18T00:29:59.999Z\"");
        assertThat(json).contains("\"executionTime\":\"2026-07-18T00:30:02.999Z\"");
        assertThat(json).contains("\"localDate\":\"2026-07-18\"");
        assertThat(json).contains("\"duration\":\"PT30M\"");
        assertThat(json).doesNotContain("timestamp");
    }

    @Test
    void serializesEntryExitFailureAndReversalPayloadsWithInstants() {
        LaplaceTradeJsonlWriter writer = writer(mock(LaplaceTradeEventRepository.class), new JacksonConfig().objectMapper());
        Instant now = Instant.parse("2026-07-18T00:29:59.999Z");
        Map<String, Object> entry = base("ENTRY", "rev-1", now);
        entry.put("side", "LONG");
        entry.put("notional", BigDecimal.TEN);
        entry.put("leverage", 1);
        entry.put("orderType", "MARKET");
        entry.put("executionPriceType", "ASK");
        entry.put("executionTime", now.plusSeconds(1));
        Map<String, Object> exit = base("EXIT", "rev-1", now);
        exit.put("entryTime", now.minusSeconds(3600));
        exit.put("exitTime", now.plusSeconds(2));
        exit.put("grossPnl", BigDecimal.ONE);
        exit.put("netPnl", new BigDecimal("0.991"));
        exit.put("entryFee", new BigDecimal("0.004"));
        exit.put("exitFee", new BigDecimal("0.005"));
        Map<String, Object> failure = base("FAILURE", null, now);
        failure.put("failureReason", "EXECUTION_PRICE_UNAVAILABLE");
        failure.put("failureTime", now.plusSeconds(3));
        assertThat(writer.json(entry)).contains("\"eventType\":\"ENTRY\"", "\"reversalId\":\"rev-1\"", "\"executionTime\":\"2026-07-18T00:30:00.999Z\"");
        assertThat(writer.json(exit)).contains("\"eventType\":\"EXIT\"", "\"reversalId\":\"rev-1\"", "\"exitTime\":\"2026-07-18T00:30:01.999Z\"");
        assertThat(writer.json(failure)).contains("\"eventType\":\"FAILURE\"", "\"failureTime\":\"2026-07-18T00:30:02.999Z\"");
    }

    @Test
    void failureSerializationErrorDoesNotEscapeOrMaskOriginalFailure() throws Exception {
        LaplaceTradeEventRepository repository = mock(LaplaceTradeEventRepository.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        LaplaceTradeJsonlWriter writer = writer(repository, mapper);
        assertThatCode(() -> writer.failure("INJUSDT", Instant.parse("2026-07-18T00:29:59.999Z"),
                "FLAT", "ENTRY", "EXECUTION_PRICE_UNAVAILABLE", new IllegalStateException("price missing"), true))
                .doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    void failurePayloadWithInstantIsSavedToOutbox() {
        LaplaceTradeEventRepository repository = mock(LaplaceTradeEventRepository.class);
        LaplaceTradeJsonlWriter writer = writer(repository, new JacksonConfig().objectMapper());
        writer.failure("BTCUSDT", Instant.parse("2026-07-18T00:29:59.999Z"), "FLAT", "ENTRY",
                "EXECUTION_PRICE_UNAVAILABLE", null, true);
        ArgumentCaptor<LaplaceTradeEventEntity> captor = ArgumentCaptor.forClass(LaplaceTradeEventEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).contains("\"signalCandleCloseTime\":\"2026-07-18T00:29:59.999Z\"");
        assertThat(captor.getValue().getPayloadJson()).contains("\"failureReason\":\"EXECUTION_PRICE_UNAVAILABLE\"");
    }

    @Test
    void drainDoesNotAppendDuplicateEventIds() throws Exception {
        LaplaceTradeEventRepository repository = mock(LaplaceTradeEventRepository.class);
        LaplaceTradeEventEntity event = LaplaceTradeEventEntity.builder()
                .eventId("event-1").eventType("ENTRY").symbol("BTCUSDT")
                .payloadJson("{\"eventId\":\"event-1\",\"eventType\":\"ENTRY\"}")
                .jsonlWritten(false).createdAt(Instant.now()).build();
        when(repository.findTop100ByJsonlWrittenFalseOrderByCreatedAtAsc()).thenReturn(List.of(event), List.of(event));
        LaplaceTradeJsonlWriter writer = writer(repository, new JacksonConfig().objectMapper());
        writer.drain();
        event.setJsonlWritten(false);
        writer.drain();
        java.nio.file.Path file = tempDir.resolve("laplace-trades-" + LocalDate.now() + ".jsonl");
        assertThat(Files.readString(file).lines().filter(line -> line.contains("event-1")).count()).isOne();
    }

    private Map<String, Object> base(String eventType, String reversalId, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("eventId", eventType.toLowerCase() + "-1");
        payload.put("reversalId", reversalId);
        payload.put("strategy", LaplacePaperExecutionService.STRATEGY);
        payload.put("symbol", "BTCUSDT");
        payload.put("signalCandleCloseTime", now);
        return payload;
    }

    private LaplaceTradeJsonlWriter writer(LaplaceTradeEventRepository repository, ObjectMapper mapper) {
        LaplaceStrategyProperties properties = new LaplaceStrategyProperties();
        properties.getLaplace().setDiagnosticDirectory(tempDir.toString());
        return new LaplaceTradeJsonlWriter(repository, mapper, properties);
    }
}
