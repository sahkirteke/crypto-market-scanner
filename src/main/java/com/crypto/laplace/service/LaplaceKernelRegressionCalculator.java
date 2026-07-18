package com.crypto.laplace.service;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.model.RegressionValues;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class LaplaceKernelRegressionCalculator {
    public static final int BANDWIDTH = 14;
    private final List<Double> weights = IntStream.range(0, BANDWIDTH)
            .mapToObj(i -> 0.5 * Math.exp(-Math.abs((double) i * i / (BANDWIDTH * BANDWIDTH))))
            .toList();
    public List<Double> weights() { return weights; }
    public RegressionValues calculate(List<Kline> chronological) {
        if (chronological == null || chronological.size() < BANDWIDTH + 2) throw new IllegalArgumentException("At least 16 closed candles required");
        int last = chronological.size() - 1;
        return new RegressionValues(at(chronological, last), at(chronological, last - 1), at(chronological, last - 2));
    }
    public double at(List<Kline> candles, int endInclusive) {
        if (endInclusive < BANDWIDTH - 1) throw new IllegalArgumentException("Insufficient candles");
        double sum = 0, weightSum = 0;
        for (int i = 0; i < BANDWIDTH; i++) {
            double close = candles.get(endInclusive - i).getClose().doubleValue();
            if (!Double.isFinite(close)) throw new IllegalArgumentException("Invalid close");
            sum += close * weights.get(i); weightSum += weights.get(i);
        }
        double result = sum / weightSum;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Invalid regression");
        return result;
    }
}
