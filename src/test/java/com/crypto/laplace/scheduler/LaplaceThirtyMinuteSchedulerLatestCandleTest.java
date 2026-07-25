package com.crypto.laplace.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.domain.model.Kline;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LaplaceThirtyMinuteSchedulerLatestCandleTest {

    private static final Instant SCAN_TIME = Instant.parse("2026-07-25T12:30:05Z");

    @Test
    void acceptsOnlyTheLatestClosedThirtyMinuteCandle() {
        Kline latest = candle("2026-07-25T12:00:00Z", "2026-07-25T12:29:59.999Z");

        assertThat(LaplaceThirtyMinuteScheduler.isLatestClosedCandle(latest, SCAN_TIME)).isTrue();
    }

    @Test
    void rejectsHistoricalClosedCandle() {
        Kline historical = candle("2026-07-25T11:30:00Z", "2026-07-25T11:59:59.999Z");

        assertThat(LaplaceThirtyMinuteScheduler.isLatestClosedCandle(historical, SCAN_TIME)).isFalse();
    }

    @Test
    void rejectsCurrentOpenThirtyMinuteCandle() {
        Kline open = candle("2026-07-25T12:30:00Z", "2026-07-25T12:59:59.999Z");

        assertThat(LaplaceThirtyMinuteScheduler.isLatestClosedCandle(open, SCAN_TIME)).isFalse();
    }

    private Kline candle(String openTime, String closeTime) {
        return Kline.builder()
                .openTime(Instant.parse(openTime))
                .closeTime(Instant.parse(closeTime))
                .build();
    }
}
