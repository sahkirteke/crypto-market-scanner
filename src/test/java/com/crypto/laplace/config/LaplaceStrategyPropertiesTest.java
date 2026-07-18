package com.crypto.laplace.config;
import static org.assertj.core.api.Assertions.*;import org.junit.jupiter.api.Test;
class LaplaceStrategyPropertiesTest {@Test void rejectsDualStrategy(){var p=new LaplaceStrategyProperties();p.setOldStrategyEnabled(true);assertThatThrownBy(p::validatePhaseOne).isInstanceOf(IllegalStateException.class);}@Test void acceptsImmutableDefaults(){assertThatCode(new LaplaceStrategyProperties()::validatePhaseOne).doesNotThrowAnyException();}}
