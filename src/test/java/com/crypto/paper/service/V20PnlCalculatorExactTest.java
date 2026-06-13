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
    void longTakeProfitFiveXPnlExact() {
        assertPnl(calculator.calculate(PositionSide.LONG, bd("100"), bd("102")),
                "2.00000000", "2.00000000", "0.02001000", "0.02038980", "1.85860020",
                "10.00000000", "0.10005000", "0.10194900", "9.29300100", "9.29300100");
    }

    @Test
    void longStopLossFiveXPnlExact() {
        assertPnl(calculator.calculate(PositionSide.LONG, bd("100"), bd("98.6")),
                "-1.40000000", "-1.40000000", "0.02001000", "0.01971014", "-1.53902014",
                "-7.00000000", "0.10005000", "0.09855070", "-7.69510070", "-7.69510070");
    }

    @Test
    void shortTakeProfitFiveXPnlExact() {
        assertPnl(calculator.calculate(PositionSide.SHORT, bd("100"), bd("98")),
                "2.00000000", "2.00000000", "0.01999000", "0.01960980", "1.86140020",
                "10.00000000", "0.09995000", "0.09804900", "9.30700100", "9.30700100");
    }

    @Test
    void shortStopLossFiveXPnlExact() {
        assertPnl(calculator.calculate(PositionSide.SHORT, bd("100"), bd("101.4")),
                "-1.40000000", "-1.40000000", "0.01999000", "0.02029014", "-1.54098014",
                "-7.00000000", "0.09995000", "0.10145070", "-7.70490070", "-7.70490070");
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
