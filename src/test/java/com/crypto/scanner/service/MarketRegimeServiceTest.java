package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.MarketRegimeResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarketRegimeServiceTest {
    private final MarketRegimeService marketRegimeService = new MarketRegimeService(new ScannerProperties());

    @Test
    void calculateReturnsPanicWhenRuleAMatches() {
        MarketRegimeResult result = marketRegimeService.calculate(
                oneHour("1.6"),
                fourHour("95", "100", "1"),
                fourHour("110", "100", "1"),
                ticker("-6"),
                bd("40"),
                bd("0"));

        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.PANIC);
        assertThat(result.getBlockNewLong()).isTrue();
        assertThat(result.getBlockNewShort()).isTrue();
        assertThat(result.getReasonTags()).contains(ReasonTag.MARKET_PANIC);
        assertThat(result.getNotes()).contains("PANIC_RULE_A");
    }

    @Test
    void calculateReturnsPanicWhenRuleBMatches() {
        MarketRegimeResult result = marketRegimeService.calculate(
                oneHour("1.9"),
                fourHour("110", "100", "1"),
                fourHour("110", "100", "1"),
                ticker("0"),
                bd("40"),
                bd("-3.5"));

        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.PANIC);
        assertThat(result.getReasonTags()).contains(ReasonTag.MARKET_PANIC);
        assertThat(result.getNotes()).contains("PANIC_RULE_B");
    }

    @Test
    void calculateReturnsRiskOnWithBreadthOk() {
        MarketRegimeResult result = marketRegimeService.calculate(
                oneHour("1.0"),
                fourHour("110", "100", "1"),
                fourHour("210", "200", "1"),
                ticker("0"),
                bd("40"),
                bd("1"));

        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.RISK_ON);
        assertThat(result.getReasonTags()).contains(ReasonTag.MARKET_RISK_ON, ReasonTag.MARKET_BREADTH_OK);
    }

    @Test
    void calculateReturnsRiskOnWithStrongBreadth() {
        MarketRegimeResult result = marketRegimeService.calculate(
                oneHour("1.0"),
                fourHour("110", "100", "1"),
                fourHour("210", "200", "1"),
                ticker("0"),
                bd("65"),
                bd("1"));

        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.RISK_ON);
        assertThat(result.getReasonTags()).contains(ReasonTag.MARKET_BREADTH_STRONG);
    }

    @Test
    void calculateReturnsChopWhenWeakRiskOnBreadthFails() {
        MarketRegimeResult result = marketRegimeService.calculate(
                oneHour("1.0"),
                fourHour("110", "100", "1"),
                fourHour("210", "200", "1"),
                ticker("0"),
                bd("20"),
                bd("1"));

        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.CHOP);
        assertThat(result.getReasonTags()).contains(ReasonTag.MARKET_CHOP, ReasonTag.WEAK_RISK_ON_BREADTH_FAIL);
    }

    @Test
    void calculateReturnsRiskOff() {
        MarketRegimeResult result = marketRegimeService.calculate(
                oneHour("1.0"),
                fourHour("95", "100", "-1"),
                fourHour("190", "200", "1"),
                ticker("0"),
                bd("40"),
                bd("1"));

        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.RISK_OFF);
        assertThat(result.getReasonTags()).contains(ReasonTag.MARKET_RISK_OFF);
    }

    @Test
    void calculateReturnsChopWhenNoConditionMatches() {
        MarketRegimeResult result = marketRegimeService.calculate(
                oneHour("1.0"),
                fourHour("110", "100", "-1"),
                fourHour("190", "200", "1"),
                ticker("0"),
                bd("40"),
                bd("1"));

        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.CHOP);
        assertThat(result.getReasonTags()).contains(ReasonTag.MARKET_CHOP);
    }

    @Test
    void calculateReturnsChopWithoutThrowingWhenCriticalDataIsMissing() {
        assertThatNoException().isThrownBy(() -> {
            MarketRegimeResult result = marketRegimeService.calculate(
                    TechnicalSnapshot.builder().build(),
                    fourHour("110", "100", "1"),
                    fourHour("210", "200", "1"),
                    ticker("0"),
                    bd("40"),
                    bd("1"));

            assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.CHOP);
            assertThat(result.getNotes()).contains("DATA_NOT_READY");
        });
    }

    @Test
    void calculateBtcFourHourChangePctReturnsPctChange() {
        BigDecimal changePct = marketRegimeService.calculateBtcFourHourChangePct(TechnicalSnapshot.builder()
                .previousClose(bd("100"))
                .close(bd("97"))
                .build());

        assertThat(changePct).isEqualByComparingTo(new BigDecimal("-3.00"));
    }

    private TechnicalSnapshot oneHour(String volumeRatio) {
        return TechnicalSnapshot.builder()
                .volumeRatio(bd(volumeRatio))
                .build();
    }

    private TechnicalSnapshot fourHour(String close, String ema20, String macdHist) {
        return TechnicalSnapshot.builder()
                .close(bd(close))
                .ema20(bd(ema20))
                .macdHist(bd(macdHist))
                .build();
    }

    private Ticker24h ticker(String priceChangePercent) {
        return Ticker24h.builder()
                .priceChangePercent(bd(priceChangePercent))
                .build();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
