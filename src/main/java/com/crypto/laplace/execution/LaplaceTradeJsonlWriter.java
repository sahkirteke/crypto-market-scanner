package com.crypto.laplace.execution;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.persistence.LaplaceTradeEventEntity;
import com.crypto.laplace.persistence.LaplaceTradeEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaplaceTradeJsonlWriter implements ApplicationRunner {
    private static final Pattern VALID_SYMBOL = Pattern.compile("^[A-Z0-9]+$");

    private final LaplaceTradeEventRepository repository;
    private final ObjectMapper mapper;
    private final LaplaceStrategyProperties properties;
    private final ConcurrentHashMap<String, ReentrantLock> symbolLocks = new ConcurrentHashMap<>();
    private final ReentrantLock failureFileLock = new ReentrantLock();

    @Override
    public void run(ApplicationArguments args) {
        log.info("LAPLACE_TRADE_FILE_CONFIG_READY mode=PER_SYMBOL baseDirectory={} filePattern=<SYMBOL>.jsonl",
                tradeDirectory());
        drain();
    }

    @Transactional
    public void drain() {
        for (var event : repository.findTop100ByJsonlWrittenFalseOrderByCreatedAtAsc()) {
            try {
                if (isTradeEvent(event)) {
                    writeSymbolTradeEvent(event);
                } else {
                    writeFailureEvent(event);
                }
            } catch (Exception exception) {
                log.error("LAPLACE_TRADE_EVENT_DRAIN_FAILED symbol={} eventId={} eventType={} errorType={} errorMessage={}",
                        event.getSymbol(), event.getEventId(), event.getEventType(),
                        exception.getClass().getSimpleName(), exception.getMessage());
            }
        }
    }

    public Optional<String> tryJson(Object value) {
        try {
            return Optional.of(mapper.writeValueAsString(value));
        } catch (Exception exception) {
            log.error("LAPLACE_TRADE_EVENT_SERIALIZATION_FAILED errorType={} errorMessage={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return Optional.empty();
        }
    }

    public String json(Object value) {
        return tryJson(value).orElseThrow(() -> new IllegalStateException("JSON serialization failed"));
    }

    @Transactional
    public boolean failure(String symbol, Instant candle, String currentPosition, String action,
                           String reason, Throwable error, boolean retryable) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "FAILURE");
        payload.put("eventId", id);
        payload.put("strategy", LaplacePaperExecutionService.STRATEGY);
        payload.put("symbol", symbol);
        payload.put("signalCandleCloseTime", candle);
        payload.put("currentPosition", currentPosition);
        payload.put("requestedAction", action);
        payload.put("failureReason", reason);
        payload.put("errorType", error == null ? null : error.getClass().getSimpleName());
        payload.put("errorMessage", error == null ? null : error.getMessage());
        payload.put("retryable", retryable);
        payload.put("failureTime", Instant.now());
        try {
            String json = mapper.writeValueAsString(payload);
            repository.save(LaplaceTradeEventEntity.builder()
                    .eventId(id).eventType("FAILURE").symbol(symbol).payloadJson(json)
                    .jsonlWritten(false).createdAt(Instant.now()).build());
            log.info("LAPLACE_FAILURE_EVENT_PUBLISHED eventId={} failureReason={} symbol={}", id, reason, symbol);
            return true;
        } catch (Exception serializationOrPersistence) {
            log.error("LAPLACE_FAILURE_EVENT_SERIALIZATION_FAILED strategy={} symbol={} signalCandleCloseTime={} requestedAction={} originalFailureReason={} originalErrorType={} originalErrorMessage={} serializationErrorType={} serializationErrorMessage={}",
                    LaplacePaperExecutionService.STRATEGY, symbol, String.valueOf(candle), action, reason,
                    error == null ? null : error.getClass().getSimpleName(),
                    error == null ? null : error.getMessage(),
                    serializationOrPersistence.getClass().getSimpleName(), serializationOrPersistence.getMessage());
            return false;
        }
    }

    private boolean isTradeEvent(LaplaceTradeEventEntity event) {
        return "ENTRY".equals(event.getEventType()) || "PARTIAL_EXIT".equals(event.getEventType())
                || "EXIT".equals(event.getEventType());
    }

    private void writeSymbolTradeEvent(LaplaceTradeEventEntity event) throws Exception {
        String symbol = event.getSymbol();
        if (symbol == null || !VALID_SYMBOL.matcher(symbol).matches()) {
            log.error("LAPLACE_TRADE_FILE_INVALID_SYMBOL symbol={} eventId={} eventType={}",
                    symbol, event.getEventId(), event.getEventType());
            return;
        }
        ReentrantLock lock = symbolLocks.computeIfAbsent(symbol, ignored -> new ReentrantLock());
        lock.lock();
        try {
            Path directory = tradeDirectory();
            Files.createDirectories(directory);
            Path file = directory.resolve(symbol + ".jsonl");
            if (containsEventId(file, event.getEventId())) {
                event.setJsonlWritten(true);
                event.setWrittenAt(Instant.now());
                repository.save(event);
                log.info("LAPLACE_SYMBOL_TRADE_EVENT_DUPLICATE_SKIPPED symbol={} eventId={}",
                        symbol, event.getEventId());
                return;
            }
            Files.writeString(file, event.getPayloadJson() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            event.setJsonlWritten(true);
            event.setWrittenAt(Instant.now());
            repository.save(event);
            log.info("LAPLACE_SYMBOL_TRADE_EVENT_WRITTEN symbol={} eventId={} eventType={} filePath={}",
                    symbol, event.getEventId(), event.getEventType(), file);
        } catch (Exception exception) {
            Path file = tradeDirectory().resolve(symbol + ".jsonl");
            log.error("LAPLACE_SYMBOL_TRADE_FILE_WRITE_FAILED symbol={} eventId={} eventType={} filePath={} errorType={} errorMessage={}",
                    symbol, event.getEventId(), event.getEventType(), file,
                    exception.getClass().getSimpleName(), exception.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void writeFailureEvent(LaplaceTradeEventEntity event) throws Exception {
        failureFileLock.lock();
        try {
            Path directory = diagnosticDirectory();
            Files.createDirectories(directory);
            Path file = directory.resolve("laplace-failures.jsonl");
            if (!containsEventId(file, event.getEventId())) {
                Files.writeString(file, event.getPayloadJson() + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            }
            event.setJsonlWritten(true);
            event.setWrittenAt(Instant.now());
            repository.save(event);
        } finally {
            failureFileLock.unlock();
        }
    }

    private boolean containsEventId(Path file, String eventId) throws Exception {
        return Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).contains("\"eventId\":\"" + eventId + "\"");
    }

    private Path tradeDirectory() {
        return Path.of(properties.getLaplace().getTradeDirectory());
    }

    private Path diagnosticDirectory() {
        return Path.of(properties.getLaplace().getDiagnosticDirectory());
    }
}
