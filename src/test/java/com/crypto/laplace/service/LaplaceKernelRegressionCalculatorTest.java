package com.crypto.laplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.domain.model.Kline;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceKernelRegressionCalculatorTest {
 private final LaplaceKernelRegressionCalculator calculator=new LaplaceKernelRegressionCalculator();
 @Test void weightsArePositiveDeterministicAndMonotonicallyDecreasing(){
  List<Double>w=calculator.weights(); assertThat(w).hasSize(14).allMatch(x->x>0);assertThat(w.stream().mapToDouble(x->x).sum()).isPositive();
  for(int i=1;i<w.size();i++)assertThat(w.get(i)).isLessThanOrEqualTo(w.get(i-1)); assertThat(calculator.weights()).isEqualTo(w);
 }
 @Test void constantSeriesStaysConstant(){assertThat(calculator.calculate(series(20,i->42)).current()).isCloseTo(42,org.assertj.core.data.Offset.offset(1e-10));}
 @Test void risingAndFallingSeriesHaveCorrectSlope(){var up=calculator.calculate(series(20,i->i));var down=calculator.calculate(series(20,i->100-i));assertThat(up.current()-up.previous()).isPositive();assertThat(down.current()-down.previous()).isNegative();}
 @Test void futureCandleCannotChangePastRegression(){List<Kline> base=series(20,i->i);double past=calculator.at(base,19);List<Kline> extended=new ArrayList<>(base);extended.add(k(999));assertThat(calculator.at(extended,19)).isEqualTo(past);}
 private List<Kline> series(int n,java.util.function.IntToDoubleFunction f){List<Kline>x=new ArrayList<>();for(int i=0;i<n;i++)x.add(k(f.applyAsDouble(i)));return x;}
 private Kline k(double close){return Kline.builder().close(BigDecimal.valueOf(close)).build();}
}
