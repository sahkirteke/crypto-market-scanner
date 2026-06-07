package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class IndicatorServiceBollingerTest {
    private final IndicatorService indicatorService = new IndicatorService();

    @Test
    void percentBIsOneWhenCloseEqualsUpperBand() {
        var values = indicatorService.calculateBollingerForTest(new BigDecimal("110"), new BigDecimal("110"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"));
        assertThat(values.bbPercentB()).isEqualByComparingTo("1");
    }

    @Test
    void percentBIsGreaterThanOneWhenCloseIsAboveUpperBand() {
        var values = indicatorService.calculateBollingerForTest(new BigDecimal("111"), new BigDecimal("111"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"));
        assertThat(values.bbPercentB()).isGreaterThan(BigDecimal.ONE);
    }

    @Test
    void percentBIsZeroWhenCloseEqualsLowerBand() {
        var values = indicatorService.calculateBollingerForTest(new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"));
        assertThat(values.bbPercentB()).isEqualByComparingTo("0");
    }

    @Test
    void percentBIsLessThanZeroWhenCloseIsBelowLowerBand() {
        var values = indicatorService.calculateBollingerForTest(new BigDecimal("89"), new BigDecimal("100"), new BigDecimal("89"), new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"));
        assertThat(values.bbPercentB()).isLessThan(BigDecimal.ZERO);
    }

    @Test
    void upperTouchedIsTrueWhenHighReachesUpperBand() {
        var values = indicatorService.calculateBollingerForTest(new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("95"), new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"));
        assertThat(values.bbUpperTouched()).isTrue();
    }

    @Test
    void lowerTouchedIsTrueWhenLowReachesLowerBand() {
        var values = indicatorService.calculateBollingerForTest(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"));
        assertThat(values.bbLowerTouched()).isTrue();
    }
}
