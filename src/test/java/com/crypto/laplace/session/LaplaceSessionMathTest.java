package com.crypto.laplace.session;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LaplaceSessionMathTest {
 @Test void compoundsOnlyPositiveReturns(){assertThat(LaplaceSessionMath.nextMargin(new BigDecimal("5"),new BigDecimal(".05"),true)).isEqualByComparingTo("5.25");assertThat(LaplaceSessionMath.nextMargin(new BigDecimal("5.25"),new BigDecimal(".04"),true)).isEqualByComparingTo("5.46");assertThat(LaplaceSessionMath.nextMargin(new BigDecimal("5"),new BigDecimal("-.1"),true)).isEqualByComparingTo("5");}
 @Test void requiresBothProfitLockThresholds(){BigDecimal c=new BigDecimal("100");assertThat(LaplaceSessionMath.shouldProfitLock(c,new BigDecimal("4.99"),new BigDecimal("10"),new BigDecimal(".05"),new BigDecimal(".046"))).isFalse();assertThat(LaplaceSessionMath.shouldProfitLock(c,new BigDecimal("5"),new BigDecimal("4.59"),new BigDecimal(".05"),new BigDecimal(".046"))).isFalse();assertThat(LaplaceSessionMath.shouldProfitLock(c,new BigDecimal("5"),new BigDecimal("4.6"),new BigDecimal(".05"),new BigDecimal(".046"))).isTrue();}
}
