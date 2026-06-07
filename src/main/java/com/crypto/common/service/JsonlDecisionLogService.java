package com.crypto.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JsonlDecisionLogService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private final ObjectMapper objectMapper;

    public void logScanner(Map<String, Object> event) { append("logs/scanner", "scan-decisions", event); }
    public void logEntry(Map<String, Object> event) { append("logs/entry", "entry-decisions", event); }
    public void logPaper(Map<String, Object> event) { append("logs/paper", "position-events", event); }

    public void append(String directory, String prefix, Map<String, Object> event) {
        try {
            Files.createDirectories(Path.of(directory));
            String day = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMAT);
            Path file = Path.of(directory, prefix + "-" + day + ".jsonl");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("time", java.time.Instant.now().toString());
            if (event != null) {
                payload.putAll(event);
            }
            Files.writeString(file, objectMapper.writeValueAsString(payload) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exception) {
            log.warn("JSONL_DECISION_LOG_WRITE_FAILED directory={} prefix={} reason={}", directory, prefix, exception.getMessage());
        }
    }
}
