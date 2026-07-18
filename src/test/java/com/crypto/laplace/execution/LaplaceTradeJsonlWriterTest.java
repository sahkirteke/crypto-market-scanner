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
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class LaplaceTradeJsonlWriterTest {
    @TempDir Path tempDir;

    @Test
    void serializesInstantAsIsoString() {
        LaplaceTradeJsonlWriter writer = writer(mock(LaplaceTradeEventRepository.class), mapper());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signalCandleCloseTime", Instant.parse("2026-07-18T00:29:59.999Z"));
        assertThat(writer.json(payload)).isEqualTo("{\"signalCandleCloseTime\":\"2026-07-18T00:29:59.999Z\"}");
    }

    @Test
    void serializesAllLaplaceTimeFieldsAsIsoStrings() {
        LaplaceTradeJsonlWriter writer = writer(mock(LaplaceTradeEventRepository.class), mapper());
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
        LaplaceTradeJsonlWriter writer = writer(mock(LaplaceTradeEventRepository.class), mapper());
        Instant now = Instant.parse("2026-07-18T00:29:59.999Z");
        Map<String, Object> entry = basePayload("ENTRY", "entry-1", "BTCUSDT", "position-1", "rev-1", now);
        entry.put("side", "LONG");
        entry.put("notional", BigDecimal.TEN);
        entry.put("leverage", 1);
        entry.put("orderType", "MARKET");
        entry.put("executionPriceType", "ASK");
        entry.put("executionTime", now.plusSeconds(1));
        Map<String, Object> exit = basePayload("EXIT", "exit-1", "BTCUSDT", "position-1", "rev-1", now);
        exit.put("side", "LONG");
        exit.put("entryTime", now.minusSeconds(3600));
        exit.put("exitTime", now.plusSeconds(2));
        exit.put("grossPnl", BigDecimal.ONE);
        exit.put("netPnl", new BigDecimal("0.991"));
        exit.put("entryFee", new BigDecimal("0.004"));
        exit.put("exitFee", new BigDecimal("0.005"));
        Map<String, Object> failure = basePayload("FAILURE", "failure-1", "BTCUSDT", "position-1", null, now);
        failure.put("failureReason", "EXECUTION_PRICE_UNAVAILABLE");
        failure.put("failureTime", now.plusSeconds(3));
        assertThat(writer.json(entry)).contains("\"eventType\":\"ENTRY\"", "\"reversalId\":\"rev-1\"", "\"executionTime\":\"2026-07-18T00:30:00.999Z\"");
        assertThat(writer.json(exit)).contains("\"eventType\":\"EXIT\"", "\"reversalId\":\"rev-1\"", "\"exitTime\":\"2026-07-18T00:30:01.999Z\"");
        assertThat(writer.json(failure)).contains("\"eventType\":\"FAILURE\"", "\"failureTime\":\"2026-07-18T00:30:02.999Z\"");
    }

    @Test
    void writesSeparatePersistentFilesPerSymbol() throws Exception {
        var solEntry = event("sol-entry", "ENTRY", "SOLUSDT", "sol-position", null);
        var btcEntry = event("btc-entry", "ENTRY", "BTCUSDT", "btc-position", null);
        LaplaceTradeJsonlWriter writer = writer(List.of(solEntry, btcEntry));
        writer.drain();
        assertThat(Files.readString(tradeFile("SOLUSDT"))).contains("SOLUSDT").doesNotContain("BTCUSDT");
        assertThat(Files.readString(tradeFile("BTCUSDT"))).contains("BTCUSDT").doesNotContain("SOLUSDT");
        assertThat(Files.list(tradeDir()).map(p -> p.getFileName().toString()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("SOLUSDT.jsonl", "BTCUSDT.jsonl");
    }

    @Test
    void writesEntryAndExitForSamePositionToSameSymbolFileInOrder() throws Exception {
        var entry = event("sol-entry", "ENTRY", "SOLUSDT", "position-1", null);
        var exit = event("sol-exit", "EXIT", "SOLUSDT", "position-1", null);
        writer(List.of(entry, exit)).drain();
        List<String> lines = Files.readAllLines(tradeFile("SOLUSDT"));
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("\"eventType\":\"ENTRY\"", "\"positionId\":\"position-1\"");
        assertThat(lines.get(1)).contains("\"eventType\":\"EXIT\"", "\"positionId\":\"position-1\"");
    }

    @Test
    void writesReversalAsExitThenEntryWithSameReversalId() throws Exception {
        var shortEntry = event("short-entry", "ENTRY", "SOLUSDT", "short-position", null, "SHORT");
        var shortExit = event("short-exit", "EXIT", "SOLUSDT", "short-position", "rev-1", "SHORT");
        var longEntry = event("long-entry", "ENTRY", "SOLUSDT", "long-position", "rev-1", "LONG");
        writer(List.of(shortEntry, shortExit, longEntry)).drain();
        List<String> lines = Files.readAllLines(tradeFile("SOLUSDT"));
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("\"eventType\":\"ENTRY\"", "\"side\":\"SHORT\"");
        assertThat(lines.get(1)).contains("\"eventType\":\"EXIT\"", "\"side\":\"SHORT\"", "\"reversalId\":\"rev-1\"");
        assertThat(lines.get(2)).contains("\"eventType\":\"ENTRY\"", "\"side\":\"LONG\"", "\"reversalId\":\"rev-1\"");
    }

    @Test
    void appendsAcrossDaysToSameSymbolFileWithoutDateFile() throws Exception {
        var entry = event("late-entry", "ENTRY", "SOLUSDT", "position-1", null, "LONG", Instant.parse("2026-07-18T23:30:00Z"));
        var exit = event("next-day-exit", "EXIT", "SOLUSDT", "position-1", null, "LONG", Instant.parse("2026-07-19T01:00:00Z"));
        writer(List.of(entry, exit)).drain();
        assertThat(Files.readAllLines(tradeFile("SOLUSDT"))).hasSize(2);
        assertThat(Files.exists(tradeDir().resolve("laplace-trades-2026-07-18.jsonl"))).isFalse();
        assertThat(Files.exists(tradeDir().resolve("laplace-trades-2026-07-19.jsonl"))).isFalse();
    }

    @Test
    void parallelSymbolsAndSameSymbolWritesProduceCompleteJsonLines() throws Exception {
        var events = List.of(
                event("sol-1", "ENTRY", "SOLUSDT", "position-1", null),
                event("sol-2", "EXIT", "SOLUSDT", "position-1", null),
                event("btc-1", "ENTRY", "BTCUSDT", "position-2", null),
                event("btc-2", "EXIT", "BTCUSDT", "position-2", null));
        var executor = Executors.newFixedThreadPool(4);
        try {
            var futures = events.stream().map(event -> (Callable<Void>) () -> {
                writer(List.of(event)).drain();
                return null;
            }).map(executor::submit).toList();
            for (var future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        ObjectMapper mapper = mapper();
        for (String line : Files.readAllLines(tradeFile("SOLUSDT"))) {
            assertThat(mapper.readTree(line).get("symbol").asText()).isEqualTo("SOLUSDT");
        }
        for (String line : Files.readAllLines(tradeFile("BTCUSDT"))) {
            assertThat(mapper.readTree(line).get("symbol").asText()).isEqualTo("BTCUSDT");
        }
    }

    @Test
    void duplicateEventIdIsSkippedInSymbolFile() throws Exception {
        var event = event("duplicate-event", "ENTRY", "SOLUSDT", "position-1", null);
        LaplaceTradeJsonlWriter writer = writer(List.of(event));
        writer.drain();
        event.setJsonlWritten(false);
        writer.drain();
        assertThat(Files.readAllLines(tradeFile("SOLUSDT")).stream().filter(line -> line.contains("duplicate-event")).count()).isOne();
    }

    @Test
    void restartAppendsToExistingSymbolFileWithoutOverwrite() throws Exception {
        writer(List.of(event("entry", "ENTRY", "SOLUSDT", "position-1", null))).drain();
        writer(List.of(event("exit", "EXIT", "SOLUSDT", "position-1", null))).drain();
        List<String> lines = Files.readAllLines(tradeFile("SOLUSDT"));
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("entry");
        assertThat(lines.get(1)).contains("exit");
    }

    @Test
    void writeFailureKeepsEventPendingAndRetryWritesOnlyOnce() throws Exception {
        Path blocked = tempDir.resolve("blocked-trade-dir");
        Files.writeString(blocked, "not-a-directory");
        var event = event("retry-event", "ENTRY", "SOLUSDT", "position-1", null);
        LaplaceTradeJsonlWriter failingWriter = writer(List.of(event), blocked);
        failingWriter.drain();
        assertThat(event.isJsonlWritten()).isFalse();
        Files.delete(blocked);
        LaplaceTradeJsonlWriter retryWriter = writer(List.of(event));
        retryWriter.drain();
        event.setJsonlWritten(false);
        retryWriter.drain();
        assertThat(Files.readAllLines(tradeFile("SOLUSDT")).stream().filter(line -> line.contains("retry-event")).count()).isOne();
    }

    @Test
    void invalidSymbolDoesNotCreatePathTraversalFile() throws Exception {
        var event = event("bad", "ENTRY", "../SOLUSDT", "position-1", null);
        writer(List.of(event)).drain();
        assertThat(Files.exists(tempDir.resolve("SOLUSDT.jsonl"))).isFalse();
        assertThat(event.isJsonlWritten()).isFalse();
    }

    @Test
    void failureEventsAreNotWrittenIntoSymbolTradeFiles() throws Exception {
        var failure = event("failure-1", "FAILURE", "SOLUSDT", "position-1", null);
        writer(List.of(failure)).drain();
        assertThat(Files.exists(tradeFile("SOLUSDT"))).isFalse();
        assertThat(Files.readString(tempDir.resolve("laplace-diagnostics").resolve("laplace-failures.jsonl")))
                .contains("\"eventType\":\"FAILURE\"", "\"symbol\":\"SOLUSDT\"");
        assertThat(failure.isJsonlWritten()).isTrue();
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
        LaplaceTradeJsonlWriter writer = writer(repository, mapper());
        writer.failure("BTCUSDT", Instant.parse("2026-07-18T00:29:59.999Z"), "FLAT", "ENTRY",
                "EXECUTION_PRICE_UNAVAILABLE", null, true);
        ArgumentCaptor<LaplaceTradeEventEntity> captor = ArgumentCaptor.forClass(LaplaceTradeEventEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).contains("\"signalCandleCloseTime\":\"2026-07-18T00:29:59.999Z\"");
        assertThat(captor.getValue().getPayloadJson()).contains("\"failureReason\":\"EXECUTION_PRICE_UNAVAILABLE\"");
    }

    private LaplaceTradeJsonlWriter writer(List<LaplaceTradeEventEntity> events) {
        return writer(events, tradeDir());
    }

    private LaplaceTradeJsonlWriter writer(List<LaplaceTradeEventEntity> events, Path tradeDirectory) {
        LaplaceTradeEventRepository repository = mock(LaplaceTradeEventRepository.class);
        when(repository.findTop100ByJsonlWrittenFalseOrderByCreatedAtAsc()).thenReturn(events);
        return writer(repository, mapper(), tradeDirectory);
    }

    private LaplaceTradeJsonlWriter writer(LaplaceTradeEventRepository repository, ObjectMapper mapper) {
        return writer(repository, mapper, tradeDir());
    }

    private LaplaceTradeJsonlWriter writer(LaplaceTradeEventRepository repository, ObjectMapper mapper, Path tradeDirectory) {
        LaplaceStrategyProperties properties = new LaplaceStrategyProperties();
        properties.getLaplace().setDiagnosticDirectory(tempDir.resolve("laplace-diagnostics").toString());
        properties.getLaplace().setTradeDirectory(tradeDirectory.toString());
        return new LaplaceTradeJsonlWriter(repository, mapper, properties);
    }

    private LaplaceTradeEventEntity event(String eventId, String eventType, String symbol, String positionId, String reversalId) {
        return event(eventId, eventType, symbol, positionId, reversalId, "LONG");
    }

    private LaplaceTradeEventEntity event(String eventId, String eventType, String symbol, String positionId, String reversalId, String side) {
        return event(eventId, eventType, symbol, positionId, reversalId, side, Instant.parse("2026-07-18T09:00:57.177Z"));
    }

    private LaplaceTradeEventEntity event(String eventId, String eventType, String symbol, String positionId,
                                          String reversalId, String side, Instant time) {
        Map<String, Object> payload = basePayload(eventType, eventId, symbol, positionId, reversalId, time);
        payload.put("side", side);
        payload.put("strategyVersion", "1.0");
        payload.put("status", "ENTRY".equals(eventType) ? "OPEN" : "CLOSED");
        if ("ENTRY".equals(eventType)) {
            payload.put("executionTime", time);
            payload.put("executionAction", side.equals("LONG") ? "LONG_OPEN" : "SHORT_OPEN");
            payload.put("executionPriceType", side.equals("LONG") ? "ASK" : "BID");
            payload.put("executionPrice", new BigDecimal("74.9600"));
            payload.put("quantity", new BigDecimal("0.133404482390"));
            payload.put("notional", BigDecimal.TEN);
            payload.put("margin", BigDecimal.TEN);
            payload.put("leverage", 1);
            payload.put("orderType", "MARKET");
            payload.put("takerFeeRate", new BigDecimal("0.0004"));
            payload.put("entryFee", new BigDecimal("0.004"));
        } else {
            payload.put("entryTime", time.minusSeconds(3600));
            payload.put("exitTime", time.plusSeconds(3600));
            payload.put("entryExecutionPrice", new BigDecimal("74.9600"));
            payload.put("exitExecutionPrice", new BigDecimal("73.8200"));
            payload.put("quantity", new BigDecimal("0.133404482390"));
            payload.put("entryNotional", BigDecimal.TEN);
            payload.put("exitNotional", new BigDecimal("9.847120481230"));
            payload.put("grossPnl", new BigDecimal("0.1441"));
            payload.put("entryFee", new BigDecimal("0.004"));
            payload.put("exitFee", new BigDecimal("0.003938848192"));
            payload.put("totalFee", new BigDecimal("0.007938848192"));
            payload.put("netPnl", new BigDecimal("0.136161151808"));
            payload.put("grossPnlPct", new BigDecimal("1.441"));
            payload.put("netPnlPct", new BigDecimal("1.36161151808"));
            payload.put("exitReason", "OPPOSITE_CONFIRMED_LAPLACE_SIGNAL");
        }
        return LaplaceTradeEventEntity.builder()
                .eventId(eventId).eventType(eventType).reversalId(reversalId).positionId(positionId).symbol(symbol)
                .payloadJson(json(payload)).jsonlWritten(false).createdAt(time).build();
    }

    private Map<String, Object> basePayload(String eventType, String eventId, String symbol, String positionId,
                                            String reversalId, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("strategy", LaplacePaperExecutionService.STRATEGY);
        payload.put("strategyVersion", "1.0");
        payload.put("symbol", symbol);
        payload.put("positionId", positionId);
        payload.put("reversalId", reversalId);
        payload.put("signalCandleOpenTime", now.minusSeconds(1800));
        payload.put("signalCandleCloseTime", now);
        payload.put("signalCandleClose", new BigDecimal("74.91"));
        return payload;
    }

    private String json(Map<String, Object> payload) {
        try {
            return mapper().writeValueAsString(payload);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ObjectMapper mapper() {
        return new JacksonConfig().objectMapper();
    }

    private Path tradeDir() {
        return tempDir.resolve("laplace-trades");
    }

    private Path tradeFile(String symbol) {
        return tradeDir().resolve(symbol + ".jsonl");
    }
}
