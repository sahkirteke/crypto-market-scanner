package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.V20PnlResult;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class V20PnlCalculatorExactTest {
    private final V20PnlCalculator calculator = new V20PnlCalculator(new ScannerProperties());

    @Test
    void longTakeProfitMakerFeeNoSlippagePnlExact() {
        assertPnl(calculator.calculate(PositionSide.LONG, bd("100"), bd("102")),
                "2.00000000", "2.00000000", "0.02000000", "0.02040000", "1.95960000",
                "10.00000000", "0.10000000", "0.10200000", "9.79800000", "9.79800000");
    }

    @Test
    void longStopLossMakerFeeNoSlippagePnlExact() {
        assertPnl(calculator.calculate(PositionSide.LONG, bd("100"), bd("98.6")),
                "-1.40000000", "-1.40000000", "0.02000000", "0.01972000", "-1.43972000",
                "-7.00000000", "0.10000000", "0.09860000", "-7.19860000", "-7.19860000");
    }

    @Test
    void shortTakeProfitMakerFeeNoSlippagePnlExact() {
        assertPnl(calculator.calculate(PositionSide.SHORT, bd("100"), bd("98")),
                "2.00000000", "2.00000000", "0.02000000", "0.01960000", "1.96040000",
                "10.00000000", "0.10000000", "0.09800000", "9.80200000", "9.80200000");
    }

    @Test
    void shortStopLossMakerFeeNoSlippagePnlExact() {
        assertPnl(calculator.calculate(PositionSide.SHORT, bd("100"), bd("101.4")),
                "-1.40000000", "-1.40000000", "0.02000000", "0.02028000", "-1.44028000",
                "-7.00000000", "0.10000000", "0.10140000", "-7.20140000", "-7.20140000");
    }


    @Test
    void makerFeeAppliedOnEntryAndExit() {
        V20PnlResult pnl = calculator.calculate(PositionSide.LONG, bd("100"), bd("102"));
        assertThat(pnl.feeMode()).isEqualTo("MAKER");
        assertThat(pnl.feeRate()).isEqualByComparingTo("0.0002");
        assertThat(pnl.leveragedEntryFeeUsdt()).isEqualByComparingTo("0.10000000");
        assertThat(pnl.leveragedExitFeeUsdt()).isEqualByComparingTo("0.10200000");
        assertThat(pnl.leveragedTotalFeeUsdt()).isEqualByComparingTo("0.20200000");
    }

    @Test
    void takerFeeNotUsedWhenFeeModeMaker() {
        V20PnlResult pnl = calculator.calculate(PositionSide.LONG, bd("100"), bd("102"));
        assertThat(pnl.feeRate()).isNotEqualByComparingTo("0.0004");
    }

    @Test
    void slippageZeroMeansAdjustedEqualsRaw() {
        V20PnlResult longPnl = calculator.calculate(PositionSide.LONG, bd("100"), bd("102"));
        V20PnlResult shortPnl = calculator.calculate(PositionSide.SHORT, bd("100"), bd("98"));
        assertThat(longPnl.slippagePct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(longPnl.entryPriceAdjusted()).isEqualByComparingTo("100.000000000000");
        assertThat(longPnl.exitPriceAdjusted()).isEqualByComparingTo("102.000000000000");
        assertThat(shortPnl.entryPriceAdjusted()).isEqualByComparingTo("100.000000000000");
        assertThat(shortPnl.exitPriceAdjusted()).isEqualByComparingTo("98.000000000000");
    }

    @Test
    void leveragedNetPnlPctUsesMarginNotNotional() {
        V20PnlResult pnl = calculator.calculate(PositionSide.LONG, bd("100"), bd("102"));
        assertThat(pnl.leveragedNetPnlPct()).isEqualByComparingTo("9.79800000");
    }

    @Test
    void unleveragedAndLeveragedUseDifferentQuantities() {
        V20PnlResult pnl = calculator.calculate(PositionSide.LONG, bd("100"), bd("102"));
        assertThat(pnl.unleveragedQuantity()).isEqualByComparingTo("1.000000000000");
        assertThat(pnl.leveragedQuantity()).isEqualByComparingTo("5.000000000000");
    }

    private void assertPnl(V20PnlResult pnl, String rawPct, String unRaw, String unEntryFee, String unExitFee,
                           String unNet, String levRaw, String levEntryFee, String levExitFee, String levNet,
                           String levPct) {
        assertThat(pnl.rawPnlPct()).isEqualByComparingTo(rawPct);
        assertThat(pnl.unleveragedRawPnlUsdt()).isEqualByComparingTo(unRaw);
        assertThat(pnl.unleveragedEntryFeeUsdt()).isEqualByComparingTo(unEntryFee);
        assertThat(pnl.unleveragedExitFeeUsdt()).isEqualByComparingTo(unExitFee);
        assertThat(pnl.unleveragedNetPnlUsdt()).isEqualByComparingTo(unNet);
        assertThat(pnl.leveragedRawPnlUsdt()).isEqualByComparingTo(levRaw);
        assertThat(pnl.leveragedEntryFeeUsdt()).isEqualByComparingTo(levEntryFee);
        assertThat(pnl.leveragedExitFeeUsdt()).isEqualByComparingTo(levExitFee);
        assertThat(pnl.leveragedNetPnlUsdt()).isEqualByComparingTo(levNet);
        assertThat(pnl.leveragedNetPnlPct()).isEqualByComparingTo(levPct);
        assertThat(pnl.leveragedQuantity()).isEqualByComparingTo("5.000000000000");
        assertThat(pnl.unleveragedQuantity()).isEqualByComparingTo("1.000000000000");
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
