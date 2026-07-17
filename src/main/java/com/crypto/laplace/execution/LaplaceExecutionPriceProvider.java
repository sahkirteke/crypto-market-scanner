package com.crypto.laplace.execution;
import java.math.BigDecimal;
public interface LaplaceExecutionPriceProvider { Price quote(String symbol); record Price(BigDecimal value,String source){} }
