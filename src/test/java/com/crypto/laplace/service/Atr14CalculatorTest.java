package com.crypto.laplace.service;
import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.domain.model.Kline;import java.math.BigDecimal;import java.util.*;import org.junit.jupiter.api.Test;
class Atr14CalculatorTest { @Test void usesHighLowAndPreviousClose(){List<Kline>x=new ArrayList<>();for(int i=0;i<16;i++)x.add(Kline.builder().high(BigDecimal.valueOf(11)).low(BigDecimal.valueOf(9)).close(BigDecimal.TEN).build());assertThat(new Atr14Calculator().at(x,15)).isEqualTo(2);}}
