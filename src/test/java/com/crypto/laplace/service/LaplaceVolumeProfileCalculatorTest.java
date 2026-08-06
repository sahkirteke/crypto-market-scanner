package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.domain.model.Kline;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceVolumeProfileCalculatorTest {
    private final LaplaceVolumeProfileCalculator calculator = new LaplaceVolumeProfileCalculator();

    @Test void distributesQuoteVolumeByOverlapAndReturnsBinCenter() {
        var candle = Kline.builder().low(new BigDecimal("100")).high(new BigDecimal("104"))
                .quoteAssetVolume(new BigDecimal("40")).build();
        assertThat(calculator.calculatePoc(List.of(candle), 4)).isEqualTo(100.5);
    }

    @Test void equalMaximumVolumeSelectsLowestIndexDeterministically() {
        var low = Kline.builder().low(new BigDecimal("100")).high(new BigDecimal("101")).quoteAssetVolume(BigDecimal.TEN).build();
        var high = Kline.builder().low(new BigDecimal("103")).high(new BigDecimal("104")).quoteAssetVolume(BigDecimal.TEN).build();
        assertThat(calculator.calculatePoc(List.of(low, high), 4)).isEqualTo(100.5);
    }

    @Test void rejectsZeroOverallPriceRange() {
        var candle = Kline.builder().low(BigDecimal.TEN).high(BigDecimal.TEN).quoteAssetVolume(BigDecimal.ONE).build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> calculator.calculatePoc(List.of(candle), 36))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
