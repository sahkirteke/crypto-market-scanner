package com.crypto.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.paper.model.PaperPositionEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaperPositionEventResponseTest {
    @Test
    void serializesSingleEventTimeField() throws Exception {
        PaperPositionEventResponse response = new PaperPositionEventResponse(
                1L,
                2L,
                "2026-06-07 23:25:10 TRT",
                PaperPositionEventType.OPENED,
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null,
                3,
                "OPENED",
                "{}",
                "2026-06-07 23:25:10 TRT"
        );

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("\"eventTime\":\"2026-06-07 23:25:10 TRT\"");
        assertThat(json).doesNotContain("eventTimeUtc");
    }
}
