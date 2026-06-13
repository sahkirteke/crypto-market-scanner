package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.RiskLevel;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.Ticker24h;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.CoinScoringInput;
import com.crypto.scanner.model.MarketRegimeResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CoinScoringServiceTest {
    private static final String SYMBOL = "BTCUSDT";

    @Test
    void riskOnAddsLongMarketScoreAdvantage() {
        CoinScoringInput riskOnInput = neutralInput(MarketRegime.RISK_ON);
        CoinScoringInput riskOffInput = neutralInput(MarketRegime.RISK_OFF);

        int riskOnLongScore = service().calculateLongScore(riskOnInput, new ArrayList<>(), new ArrayList<>());
        int riskOffLongScore = service().calculateLongScore(riskOffInput, new ArrayList<>(), new ArrayList<>());

        assertThat(riskOnLongScore - riskOffLongScore).isEqualTo(15);
    }

    @Test
    void riskOffAddsShortMarketScoreAdvantage() {
        CoinScoringInput riskOffInput = neutralInput(MarketRegime.RISK_OFF);
        CoinScoringInput riskOnInput = neutralInput(MarketRegime.RISK_ON);

        int riskOffShortScore = service().calculateShortScore(riskOffInput, new ArrayList<>(), new ArrayList<>());
        int riskOnShortScore = service().calculateShortScore(riskOnInput, new ArrayList<>(), new ArrayList<>());

        assertThat(riskOffShortScore - riskOnShortScore).isEqualTo(15);
    }

    @Test
    void openInterestDoesNotAddScore() {
        CoinScanResult withoutOpenInterest = service().score(bullishInput(MarketRegime.RISK_ON, BigDecimal.ZERO, BigDecimal.ZERO, null));
        CoinScanResult withOpenInterest = service().score(bullishInput(
                MarketRegime.RISK_ON,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("999999999")));

        assertThat(withOpenInterest.getScore()).isEqualTo(withoutOpenInterest.getScore());
        assertThat(withOpenInterest.getLongScore()).isEqualTo(withoutOpenInterest.getLongScore());
        assertThat(withOpenInterest.getShortScore()).isEqualTo(withoutOpenInterest.getShortScore());
    }

    @Test
    void dangerPositiveFundingAddsLongCrowdedAndHighRisk() {
        CoinScanResult result = service().score(bullishInput(MarketRegime.RISK_ON, new BigDecimal("0.0012"), BigDecimal.ZERO, null));

        assertThat(result.getWarnings()).contains(ReasonTag.LONG_CROWDED);
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void dangerNegativeFundingAddsShortCrowdedAndHighRisk() {
        CoinScanResult result = service().score(bearishInput(MarketRegime.RISK_OFF, new BigDecimal("-0.0012"), BigDecimal.ZERO, null));

        assertThat(result.getWarnings()).contains(ReasonTag.SHORT_CROWDED);
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void pumpWarningDoesNotEliminateByItself() {
        CoinScanResult result = service().score(bullishInput(MarketRegime.RISK_ON, BigDecimal.ZERO, BigDecimal.valueOf(30), null));

        assertThat(result.getWarnings()).contains(ReasonTag.PUMPED_TOO_MUCH_WARNING);
        assertThat(result.getClassification()).isNotEqualTo(CoinClassification.ELIMINATED);
    }

    @Test
    void dumpWarningDoesNotEliminateByItself() {
        CoinScanResult result = service().score(bearishInput(MarketRegime.RISK_OFF, BigDecimal.ZERO, BigDecimal.valueOf(-30), null));

        assertThat(result.getWarnings()).containsAnyOf(
                ReasonTag.DUMPED_TOO_MUCH_WARNING,
                ReasonTag.SHORT_EXTREME_LATE_DUMP_RISK);
        assertThat(result.getClassification()).isNotEqualTo(CoinClassification.ELIMINATED);
    }

    @Test
    void directionBiasUsesDifferenceThreshold() {
        CoinScoringService scoringService = service();

        assertThat(scoringService.determineDirectionBias(70, 60, regime(MarketRegime.CHOP)))
                .isEqualTo(DirectionBias.LONG);
        assertThat(scoringService.determineDirectionBias(69, 60, regime(MarketRegime.CHOP)))
                .isEqualTo(DirectionBias.NEUTRAL);
    }

    @Test
    void scoreBelowWatchlistThresholdUsesScoreBelowThresholdReason() {
        CoinScanResult result = service().score(neutralInput(MarketRegime.CHOP));

        assertThat(result.getClassification()).isEqualTo(CoinClassification.ELIMINATED);
        assertThat(result.getEliminatedReason()).isEqualTo(EliminationReason.SCORE_BELOW_THRESHOLD);
    }

    @Test
    void panicRegimeEliminatesWithNeutralBias() {
        CoinScanResult result = service().score(bullishInput(MarketRegime.PANIC, BigDecimal.ZERO, BigDecimal.ZERO, null));

        assertThat(result.getClassification()).isEqualTo(CoinClassification.ELIMINATED);
        assertThat(result.getDirectionBias()).isEqualTo(DirectionBias.NEUTRAL);
        assertThat(result.getEliminatedReason()).isEqualTo(EliminationReason.MARKET_PANIC_NO_NEW_ENTRY);
    }

    @Test
    void nullInputReturnsDataNotReady() {
        CoinScanResult result = service().score(null);

        assertThat(result.getClassification()).isEqualTo(CoinClassification.ELIMINATED);
        assertThat(result.getEliminatedReason()).isEqualTo(EliminationReason.DATA_NOT_READY);
        assertThat(result.getReasons()).contains(ReasonTag.DATA_NOT_READY);
    }

    private CoinScoringService service() {
        ScannerProperties properties = new ScannerProperties();
        properties.getV20().setEnabled(false);
        return new CoinScoringService(properties);
    }

    private CoinScoringInput neutralInput(MarketRegime marketRegime) {
        TechnicalSnapshot oneHour = TechnicalSnapshot.builder()
                .close(BigDecimal.valueOf(100))
                .ema20(BigDecimal.valueOf(100))
                .rsi14(BigDecimal.valueOf(40))
                .macdHist(BigDecimal.ZERO)
                .volumeRatio(BigDecimal.ONE)
                .build();
        TechnicalSnapshot fourHour = TechnicalSnapshot.builder()
                .close(BigDecimal.valueOf(100))
                .ema20(BigDecimal.valueOf(100))
                .ema50(BigDecimal.valueOf(100))
                .ema200(BigDecimal.valueOf(100))
                .build();
        return input(oneHour, fourHour, marketRegime, null, BigDecimal.ZERO, null);
    }

    private CoinScoringInput bullishInput(
            MarketRegime marketRegime,
            BigDecimal fundingRate,
            BigDecimal priceChangePercent,
            BigDecimal openInterest
    ) {
        TechnicalSnapshot oneHour = TechnicalSnapshot.builder()
                .close(BigDecimal.valueOf(120))
                .ema20(BigDecimal.valueOf(100))
                .rsi14(BigDecimal.valueOf(50))
                .macdHist(BigDecimal.ONE)
                .volumeRatio(new BigDecimal("1.3"))
                .build();
        TechnicalSnapshot fourHour = TechnicalSnapshot.builder()
                .close(BigDecimal.valueOf(120))
                .ema20(BigDecimal.valueOf(100))
                .ema50(BigDecimal.valueOf(90))
                .ema200(BigDecimal.valueOf(80))
                .build();
        return input(oneHour, fourHour, marketRegime, fundingRate, priceChangePercent, openInterest);
    }

    private CoinScoringInput bearishInput(
            MarketRegime marketRegime,
            BigDecimal fundingRate,
            BigDecimal priceChangePercent,
            BigDecimal openInterest
    ) {
        TechnicalSnapshot oneHour = TechnicalSnapshot.builder()
                .close(BigDecimal.valueOf(80))
                .ema20(BigDecimal.valueOf(100))
                .rsi14(BigDecimal.valueOf(45))
                .macdHist(BigDecimal.ONE.negate())
                .volumeRatio(new BigDecimal("1.3"))
                .build();
        TechnicalSnapshot fourHour = TechnicalSnapshot.builder()
                .close(BigDecimal.valueOf(80))
                .ema20(BigDecimal.valueOf(100))
                .ema50(BigDecimal.valueOf(110))
                .ema200(BigDecimal.valueOf(120))
                .build();
        return input(oneHour, fourHour, marketRegime, fundingRate, priceChangePercent, openInterest);
    }

    private CoinScoringInput input(
            TechnicalSnapshot oneHour,
            TechnicalSnapshot fourHour,
            MarketRegime marketRegime,
            BigDecimal fundingRate,
            BigDecimal priceChangePercent,
            BigDecimal openInterest
    ) {
        return CoinScoringInput.builder()
                .symbol(SYMBOL)
                .oneHour(oneHour)
                .fourHour(fourHour)
                .ticker24h(Ticker24h.builder()
                        .symbol(SYMBOL)
                        .lastPrice(BigDecimal.valueOf(100))
                        .priceChangePercent(priceChangePercent)
                        .quoteVolume(BigDecimal.valueOf(100_000_000))
                        .build())
                .bookTicker(BookTicker.builder()
                        .symbol(SYMBOL)
                        .spreadPct(new BigDecimal("0.01"))
                        .build())
                .futuresSnapshot(FuturesSnapshot.builder()
                        .symbol(SYMBOL)
                        .fundingRate(fundingRate)
                        .openInterest(openInterest)
                        .build())
                .marketRegimeResult(regime(marketRegime))
                .build();
    }

    private MarketRegimeResult regime(MarketRegime marketRegime) {
        return MarketRegimeResult.builder()
                .marketRegime(marketRegime)
                .marketBreadthPct(BigDecimal.valueOf(50))
                .build();
    }
}
