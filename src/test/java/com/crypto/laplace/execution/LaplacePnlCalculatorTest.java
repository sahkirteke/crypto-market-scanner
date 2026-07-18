package com.crypto.laplace.execution;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.common.enums.PositionSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LaplacePnlCalculatorTest {
    private final LaplacePnlCalculator calculator = new LaplacePnlCalculator();

    @Test
    void longPnlUsesFixedTenUsdtDenominatorAndSubtractsFees() {
        var pnl = calculator.calculate(PositionSide.LONG, bd("100"), bd("110"), bd("0.1"),
                bd("10"), bd("0.004"), bd("0.0044"));
        assertThat(pnl.gross()).isEqualByComparingTo("1");
        assertThat(pnl.grossPct()).isEqualByComparingTo("10");
        assertThat(pnl.net()).isEqualByComparingTo("0.9916");
        assertThat(pnl.netPct()).isEqualByComparingTo("9.916");
    }

    @Test
    void shortPnlUsesFixedTenUsdtDenominatorAndSubtractsFees() {
        var pnl = calculator.calculate(PositionSide.SHORT, bd("100"), bd("90"), bd("0.1"),
                bd("10"), bd("0.004"), bd("0.0036"));
        assertThat(pnl.gross()).isEqualByComparingTo("1");
        assertThat(pnl.grossPct()).isEqualByComparingTo("10");
        assertThat(pnl.net()).isEqualByComparingTo("0.9924");
        assertThat(pnl.netPct()).isEqualByComparingTo("9.924");
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
