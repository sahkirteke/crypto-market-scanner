package com.crypto.laplace.execution;
import static org.assertj.core.api.Assertions.assertThat;import com.crypto.common.enums.PositionSide;import java.math.BigDecimal;import org.junit.jupiter.api.Test;
class LaplacePnlCalculatorTest {private final LaplacePnlCalculator calculator=new LaplacePnlCalculator();
 @Test void longPnlSubtractsBothFees(){var p=calculator.calculate(PositionSide.LONG,bd("100"),bd("110"),bd("2"),bd("0.08"),bd("0.088"));assertThat(p.gross()).isEqualByComparingTo("20");assertThat(p.grossPct()).isEqualByComparingTo("10");assertThat(p.net()).isEqualByComparingTo("19.832");}
 @Test void shortPnlSubtractsBothFees(){var p=calculator.calculate(PositionSide.SHORT,bd("100"),bd("90"),bd("2"),bd("0.08"),bd("0.072"));assertThat(p.gross()).isEqualByComparingTo("20");assertThat(p.grossPct()).isEqualByComparingTo("10");assertThat(p.net()).isEqualByComparingTo("19.848");}
 private BigDecimal bd(String x){return new BigDecimal(x);}}
