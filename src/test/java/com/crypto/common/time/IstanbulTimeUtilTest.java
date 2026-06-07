package com.crypto.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IstanbulTimeUtilTest {
    @Test
    void formatUsesIstanbulDisplayStandard() {
        assertThat(IstanbulTimeUtil.format(Instant.parse("2026-06-07T20:25:10Z")))
                .isEqualTo("2026-06-07 23:25:10 TRT");
    }

    @Test
    void formatReturnsNullForNullInstant() {
        assertThat(IstanbulTimeUtil.format(null)).isNull();
    }
}
