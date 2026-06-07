package com.crypto.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlDecisionLogServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void appendWritesSingleIstanbulTimeField() throws Exception {
        JsonlDecisionLogService service = new JsonlDecisionLogService(new ObjectMapper());
        Path directory = tempDir.resolve("scanner");

        service.append(directory.toString(), "scan-decisions", Map.of(
                "event", "TEST",
                "time", Instant.parse("2026-06-07T20:25:10Z"),
                "timeUtc", "2026-06-07T20:25:10Z",
                "timeIstanbul", "2026-06-07T23:25:10+03:00",
                "timeIstanbulText", "2026-06-07 23:25:10 TRT"
        ));

        String jsonl = Files.readString(Files.list(directory).findFirst().orElseThrow());

        assertThat(jsonl).contains("\"time\":\"2026-06-07 23:25:10 TRT\"");
        assertThat(jsonl).doesNotContain("timeUtc");
        assertThat(jsonl).doesNotContain("timeIstanbul");
        assertThat(jsonl).doesNotContain("timeIstanbulText");
    }
}
