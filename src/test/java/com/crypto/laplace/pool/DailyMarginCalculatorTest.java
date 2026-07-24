package com.crypto.laplace.pool;
import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class DailyMarginCalculatorTest {
 @Test void compounds_realized_capital_only(){assertThat(DailyMarginCalculator.margin(BigDecimal.ZERO)).isEqualByComparingTo("5");assertThat(DailyMarginCalculator.margin(new BigDecimal("25"))).isEqualByComparingTo("5.5");assertThat(DailyMarginCalculator.margin(new BigDecimal("-25"))).isEqualByComparingTo("4.5");}
}
