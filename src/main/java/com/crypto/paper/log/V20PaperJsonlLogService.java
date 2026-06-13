package com.crypto.paper.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class V20PaperJsonlLogService {
    public static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ISTANBUL_ZONE);
    private final ObjectMapper objectMapper;

    public void log(Map<String, Object> event) {
        try {
            Files.createDirectories(Path.of("logs/paper"));
            String day = LocalDate.now(ISTANBUL_ZONE).format(DAY);
            Path file = Path.of("logs/paper", "v20-paper-events-" + day + ".jsonl");
            Map<String, Object> payload = new LinkedHashMap<>();
            if (event != null) event.forEach((key, value) -> payload.put(key, format(value)));
            if (!payload.containsKey("timeTr")) payload.put("timeTr", TIME.format(Instant.now()));
            Files.writeString(file, objectMapper.writeValueAsString(payload) + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exception) {
            log.warn("V20_PAPER_JSONL_WRITE_FAILED reason={}", exception.getMessage());
        }
    }

    public String formatTr(Instant instant) { return instant == null ? null : TIME.format(instant); }

    private Object format(Object value) { return value instanceof Instant instant ? TIME.format(instant) : value; }
}
