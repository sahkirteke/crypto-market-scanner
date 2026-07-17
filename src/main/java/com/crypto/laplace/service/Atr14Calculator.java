package com.crypto.laplace.service;

import com.crypto.domain.model.Kline;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Atr14Calculator {
    public static final int PERIOD = 14;
    public double at(List<Kline> candles, int endInclusive) {
        if (endInclusive < PERIOD) throw new IllegalArgumentException("ATR unavailable");
        double total = 0;
        for (int i = endInclusive - PERIOD + 1; i <= endInclusive; i++) {
            Kline c = candles.get(i);
            double high=c.getHigh().doubleValue(), low=c.getLow().doubleValue(), previous=candles.get(i-1).getClose().doubleValue();
            total += Math.max(high-low, Math.max(Math.abs(high-previous), Math.abs(low-previous)));
        }
        double atr=total/PERIOD;
        if (!Double.isFinite(atr) || atr <= 0) throw new IllegalArgumentException("ATR must be positive");
        return atr;
    }
}
