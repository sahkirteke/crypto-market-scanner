package com.crypto.laplace.service;

import com.crypto.domain.model.Kline;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LaplaceVolumeProfileCalculator {
    public double calculatePoc(List<Kline> candles, int bins) {
        if (candles == null || candles.isEmpty() || bins <= 0) throw new IllegalArgumentException("Invalid volume profile input");
        double min = candles.stream().mapToDouble(c -> required(c.getLow())).min().orElseThrow();
        double max = candles.stream().mapToDouble(c -> required(c.getHigh())).max().orElseThrow();
        if (!(max > min)) throw new IllegalArgumentException("Zero volume profile price range");
        double width = (max - min) / bins;
        double[] volume = new double[bins];
        for (Kline candle : candles) {
            double low = required(candle.getLow()), high = required(candle.getHigh());
            double quote = required(candle.getQuoteAssetVolume());
            if (quote < 0 || high < low) throw new IllegalArgumentException("Invalid volume profile candle");
            if (high == low) {
                volume[index(low, min, width, bins)] += quote;
                continue;
            }
            for (int i = 0; i < bins; i++) {
                double binLow = min + i * width, binHigh = i == bins - 1 ? max : binLow + width;
                double overlap = Math.max(0, Math.min(high, binHigh) - Math.max(low, binLow));
                volume[i] += quote * overlap / (high - low);
            }
        }
        int poc = 0;
        for (int i = 1; i < bins; i++) if (volume[i] > volume[poc]) poc = i;
        return min + (poc + 0.5d) * width;
    }

    private static int index(double price, double min, double width, int bins) {
        return Math.min(bins - 1, Math.max(0, (int) ((price - min) / width)));
    }
    private static double required(java.math.BigDecimal value) {
        if (value == null || !Double.isFinite(value.doubleValue())) throw new IllegalArgumentException("Missing/non-finite volume profile value");
        return value.doubleValue();
    }
}
