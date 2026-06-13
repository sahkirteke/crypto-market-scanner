package com.crypto.scanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.scanner.config.ScannerProperties;
import com.crypto.scanner.model.V20ScanEntrySummary;
import com.crypto.scanner.model.V20ScanInput;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class V20ScanEntryServiceTest {
    private final ScannerProperties properties = new ScannerProperties();
    private final V20ScanEntryService service = new V20ScanEntryService(properties, new V20SignalService(properties), new com.crypto.paper.service.V20PnlCalculator(properties));

    @Test
    void opensThreePositionsWhenThreeSymbolsQualifyInSameScan() {
        V20ScanEntrySummary summary = service.evaluate(List.of(input("AUSDT", PositionSide.LONG), input("BUSDT", PositionSide.SHORT), input("CUSDT", PositionSide.LONG)), Set.of());
        assertThat(summary.getOpenedPositions()).hasSize(3);
    }

    @Test
    void skipsWhenSameSymbolAlreadyHasOpenPosition() {
        V20ScanEntrySummary summary = service.evaluate(List.of(input("AUSDT", PositionSide.LONG)), Set.of("AUSDT"));
        assertThat(summary.getOpenedPositions()).isEmpty();
        assertThat(summary.getSkippedOpenPositionCount()).isEqualTo(1);
    }

    @Test
    void longConditionsProduceEnterLongPosition() {
        PaperPositionEntity position = service.evaluate(List.of(input("AUSDT", PositionSide.LONG)), Set.of()).getOpenedPositions().get(0);
        assertThat(position.getSide()).isEqualTo(PositionSide.LONG);
        assertThat(position.getEntryPrice()).isEqualByComparingTo("10.500000000000");
    }

    @Test
    void shortConditionsProduceEnterShortPosition() {
        PaperPositionEntity position = service.evaluate(List.of(input("BUSDT", PositionSide.SHORT)), Set.of()).getOpenedPositions().get(0);
        assertThat(position.getSide()).isEqualTo(PositionSide.SHORT);
    }

    @Test
    void conflictSelectsHigherSignalScore() {
        V20ScanInput conflict = V20ScanInput.builder().symbol("XUSDT").symbolInfo(symbolInfo()).bookTicker(bookTicker())
                .fourHour(conflict4h()).oneHour(TechnicalSnapshot.builder().takerBuyRatio(bd("0.50")).closePosition(bd("0.50")).build()).fundingRate(bd("0.00008000")).fundingRates(funding()).marketRegime(MarketRegime.RISK_ON)
                .qualityPass(true).qualityFail(false).build();
        PaperPositionEntity position = service.evaluate(List.of(conflict), Set.of()).getOpenedPositions().get(0);
        assertThat(position.getSide()).isEqualTo(PositionSide.LONG);
    }

    @Test
    void conflictWithScoreDifferenceOneOrLessSkips() {
        TechnicalSnapshot equalConflict = conflict4h(); equalConflict.setBbPosition(bd("0.50")); equalConflict.setClosePosition(bd("0.50"));
        V20ScanInput conflict = V20ScanInput.builder().symbol("XUSDT").symbolInfo(symbolInfo()).bookTicker(bookTicker())
                .fourHour(equalConflict).oneHour(TechnicalSnapshot.builder().takerBuyRatio(bd("0.50")).closePosition(bd("0.50")).build())
                .fundingRate(bd("0.00008000")).fundingRates(funding()).marketRegime(MarketRegime.RISK_ON).qualityPass(false).qualityFail(false).build();
        assertThat(service.evaluate(List.of(conflict), Set.of()).getOpenedPositions()).isEmpty();
    }

    @Test
    void missingBookTickerSkipsEntry() {
        V20ScanInput input = V20ScanInput.builder().symbol("AUSDT").symbolInfo(symbolInfo()).fourHour(long4h()).oneHour(oneHourLong())
                .fundingRate(bd("0.00008000")).fundingRates(funding()).marketRegime(MarketRegime.RISK_ON).qualityPass(true).build();
        assertThat(service.evaluate(List.of(input), Set.of()).getOpenedPositions()).isEmpty();
    }

    @Test
    void invalidQuantitySkipsEntry() {
        SymbolInfo invalid = symbolInfo(); invalid.setMinQty(bd("1000"));
        V20ScanInput input = V20ScanInput.builder().symbol("AUSDT").symbolInfo(invalid).bookTicker(bookTicker()).fourHour(long4h()).oneHour(oneHourLong())
                .fundingRate(bd("0.00008000")).fundingRates(funding()).marketRegime(MarketRegime.RISK_ON).qualityPass(true).build();
        V20ScanEntrySummary summary = service.evaluate(List.of(input), Set.of());
        assertThat(summary.getOpenedPositions()).isEmpty();
        assertThat(summary.getSkippedInvalidQuantityCount()).isEqualTo(1);
    }

    private V20ScanInput input(String symbol, PositionSide side) {
        return V20ScanInput.builder().symbol(symbol).symbolInfo(symbolInfo()).bookTicker(bookTicker())
                .fourHour(side == PositionSide.LONG ? long4h() : short4h()).oneHour(side == PositionSide.LONG ? oneHourLong() : oneHourShort())
                .fundingRate(bd("0.00008000")).fundingRates(funding()).marketRegime(MarketRegime.RISK_ON).qualityPass(true).qualityFail(false).build();
    }
    private SymbolInfo symbolInfo() { return SymbolInfo.builder().symbol("TESTUSDT").stepSize(bd("0.001")).minQty(bd("0.001")).minNotional(bd("5")).build(); }
    private BookTicker bookTicker() { return BookTicker.builder().bidPrice(bd("10.00")).askPrice(bd("11.00")).build(); }
    private TechnicalSnapshot long4h() { return TechnicalSnapshot.builder().rsi14(bd("50")).adx14(bd("25")).atrPct(bd("2.0")).ema20Ema50CompPct(bd("0.5")).closeEma20DistPct(bd("1.0")).distFromLow20Pct(bd("3.0")).diDiff(bd("-1")).takerBuyRatio(bd("0.52")).closePosition(bd("0.50")).volumeRatio(bd("1.0")).rangePct(bd("2.0")).bbPosition(bd("0.50")).build(); }
    private TechnicalSnapshot short4h() { return TechnicalSnapshot.builder().rsi14(bd("50")).adx14(bd("25")).atrPct(bd("2.0")).ema20Ema50CompPct(bd("0.5")).closeEma20DistPct(bd("1.0")).distFromHigh20Pct(bd("3.0")).diDiff(bd("1")).takerBuyRatio(bd("0.46")).closePosition(bd("0.50")).volumeRatio(bd("1.0")).rangePct(bd("2.0")).bbPosition(bd("0.50")).build(); }
    private TechnicalSnapshot conflict4h() { TechnicalSnapshot t = long4h(); t.setDistFromHigh20Pct(bd("3.0")); t.setTakerBuyRatio(bd("0.50")); t.setDiDiff(bd("0")); t.setBbPosition(bd("0.78")); t.setClosePosition(bd("0.76")); return t; }
    private TechnicalSnapshot oneHourLong() { return TechnicalSnapshot.builder().takerBuyRatio(bd("0.52")).closePosition(bd("0.50")).build(); }
    private TechnicalSnapshot oneHourShort() { return TechnicalSnapshot.builder().takerBuyRatio(bd("0.48")).closePosition(bd("0.50")).build(); }
    private List<BigDecimal> funding() { return List.of(bd("0.00009"), bd("0.00008"), bd("0.000075")); }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
