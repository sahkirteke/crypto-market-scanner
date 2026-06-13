package com.crypto.paper.log;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class V20PaperJsonlLogServiceTest {
    @Test
    void formatsTimeInEuropeIstanbul() {
        V20PaperJsonlLogService service = new V20PaperJsonlLogService(new ObjectMapper());
        assertThat(service.formatTr(Instant.parse("2026-06-13T15:42:10.125Z")))
                .isEqualTo("2026-06-13T18:42:10.125+03:00");
    }
}
