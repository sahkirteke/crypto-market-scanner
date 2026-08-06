package com.crypto.laplace.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LaplaceStrategyPropertiesTest {
    @Test
    void rejectsDualStrategy() {
        var properties = new LaplaceStrategyProperties();
        properties.setOldStrategyEnabled(true);
        assertThatThrownBy(properties::validatePhaseOne).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsFixedFiveUsdtMarginTenXFiftyUsdtNotionalAndFivePercentStop() {
        assertThatCode(new LaplaceStrategyProperties()::validatePhaseOne).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnyDifferentLaplaceStopLossRule() {
        var properties = new LaplaceStrategyProperties();
        properties.getLaplace().setStopLossPct(new BigDecimal("0.04"));
        assertThatThrownBy(properties::validatePhaseOne)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paper execution");
    }
}
