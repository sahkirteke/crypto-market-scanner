package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.TechnicalSnapshot;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BollingerScoreServiceTest {
    private final BollingerScoreService service = new BollingerScoreService();

    @Test
    void longUpperBandChaseRiskSubtractsFour() {
        var result = service.calculate(PositionSide.LONG, MarketRegime.RISK_ON, snapshot("0.90"), previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("-4");
        assertThat(result.getBbReasons()).contains("LONG_BB_CHASE_RISK");
    }

    @Test
    void longUpperClosedOutsideSubtractsSixAndClampsAtMinusEight() {
        TechnicalSnapshot current = snapshot("1.10");
        current.setBbUpperClosedOutside(true);
        var result = service.calculate(PositionSide.LONG, MarketRegime.RISK_ON, current, previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("-8");
        assertThat(result.getBbReasons()).contains("LONG_BB_OUTSIDE_CHASE");
    }

    @Test
    void shortLowerBandChaseRiskSubtractsSix() {
        var result = service.calculate(PositionSide.SHORT, MarketRegime.RISK_ON, snapshot("0.10"), previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("-6");
        assertThat(result.getBbReasons()).contains("SHORT_BB_LOWER_CHASE_RISK");
    }

    @Test
    void shortLowerClosedOutsideSubtractsEightAndClampsAtMinusEight() {
        TechnicalSnapshot current = snapshot("-0.10");
        current.setBbLowerClosedOutside(true);
        var result = service.calculate(PositionSide.SHORT, MarketRegime.RISK_ON, current, previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("-8");
        assertThat(result.getBbReasons()).contains("SHORT_BB_OUTSIDE_CHASE");
    }

    @Test
    void longHealthyPullbackAddsFour() {
        TechnicalSnapshot current = snapshot("0.50");
        current.setClose(new BigDecimal("105"));
        current.setEma20(new BigDecimal("100"));
        current.setMacdHist(new BigDecimal("2"));
        var result = service.calculate(PositionSide.LONG, MarketRegime.RISK_ON, current, previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("4");
        assertThat(result.getBbReasons()).contains("LONG_BB_HEALTHY_PULLBACK");
    }

    @Test
    void shortHealthyRejectionAddsFour() {
        TechnicalSnapshot current = snapshot("0.75");
        current.setClose(new BigDecimal("95"));
        current.setEma20(new BigDecimal("100"));
        current.setMacdHist(new BigDecimal("0"));
        var result = service.calculate(PositionSide.SHORT, MarketRegime.RISK_ON, current, previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("4");
        assertThat(result.getBbReasons()).contains("SHORT_BB_HEALTHY_REJECTION");
    }

    @Test
    void chopLongMeanReversionAddsFour() {
        TechnicalSnapshot current = snapshot("0.25");
        current.setMacdHist(new BigDecimal("2"));
        current.setRsi14(new BigDecimal("45"));
        var result = service.calculate(PositionSide.LONG, MarketRegime.CHOP, current, previous("1", "40"));
        assertThat(result.getBbScore()).isEqualByComparingTo("4");
        assertThat(result.getBbReasons()).contains("LONG_BB_MEAN_REVERSION");
    }

    @Test
    void chopShortMeanReversionAddsFour() {
        TechnicalSnapshot current = snapshot("0.80");
        current.setMacdHist(new BigDecimal("0"));
        current.setRsi14(new BigDecimal("45"));
        var result = service.calculate(PositionSide.SHORT, MarketRegime.CHOP, current, previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("4");
        assertThat(result.getBbReasons()).contains("SHORT_BB_MEAN_REVERSION");
    }

    @Test
    void positiveScoreClampsAtFive() {
        TechnicalSnapshot current = snapshot("0.75");
        current.setClose(new BigDecimal("95"));
        current.setEma20(new BigDecimal("100"));
        current.setMacdHist(new BigDecimal("0"));
        current.setRsi14(new BigDecimal("45"));
        var result = service.calculate(PositionSide.SHORT, MarketRegime.CHOP, current, previous("1", "50"));
        assertThat(result.getBbScore()).isEqualByComparingTo("5");
    }

    private TechnicalSnapshot snapshot(String percentB) {
        return TechnicalSnapshot.builder()
                .close(new BigDecimal("100"))
                .ema20(new BigDecimal("100"))
                .rsi14(new BigDecimal("50"))
                .macdHist(new BigDecimal("1"))
                .bbPercentB(new BigDecimal(percentB))
                .bbWidth(new BigDecimal("0.20"))
                .bbUpper(new BigDecimal("110"))
                .bbMiddle(new BigDecimal("100"))
                .bbLower(new BigDecimal("90"))
                .bbUpperTouched(false)
                .bbLowerTouched(false)
                .bbUpperClosedOutside(false)
                .bbLowerClosedOutside(false)
                .build();
    }

    private TechnicalSnapshot previous(String macdHist, String rsi) {
        return TechnicalSnapshot.builder()
                .macdHist(new BigDecimal(macdHist))
                .rsi14(new BigDecimal(rsi))
                .build();
    }
}
