package com.crypto.laplace.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.model.LaplaceSignalResult;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LaplaceHighRiskLongFilter {
    private static final String INTERVAL = "30m";
    private static final int KLINE_LIMIT = 100;
    private static final int ATR_PERIOD = 14;

    private final BinanceFuturesClient binanceFuturesClient;

    public Evaluation evaluate(LaplaceSignalResult signal, PositionSide effectiveExecutionSide) {
        if (effectiveExecutionSide != PositionSide.LONG || signal == null || signal.signalCandleCloseTime() == null) {
            return Evaluation.notHighRisk();
        }
        List<Kline> completedBeforeSignal = safeKlines(signal.symbol()).stream()
                .filter(this::hasOhlc)
                .filter(candle -> Boolean.TRUE.equals(candle.getClosed()))
                .filter(candle -> candle.getCloseTime().isBefore(signal.signalCandleCloseTime()))
                .sorted(Comparator.comparing(Kline::getCloseTime))
                .toList();
        return evaluateCompletedCandles(effectiveExecutionSide, completedBeforeSignal);
    }

    Evaluation evaluateCompletedCandles(PositionSide effectiveExecutionSide, List<Kline> candles) {
        if (effectiveExecutionSide != PositionSide.LONG || candles == null || candles.size() < ATR_PERIOD + 1) {
            return Evaluation.notHighRisk();
        }
        double atr = initialAtr(candles);
        if (Double.isNaN(atr)) {
            return Evaluation.notHighRisk();
        }
        for (int index = ATR_PERIOD + 1; index < candles.size(); index++) {
            atr = ((atr * (ATR_PERIOD - 1)) + trueRange(candles.get(index), candles.get(index - 1))) / ATR_PERIOD;
        }
        Kline last = candles.getLast();
        Kline fourBarsAgo = candles.get(candles.size() - 5);
        double lastClose = last.getClose().doubleValue();
        double referenceClose = fourBarsAgo.getClose().doubleValue();
        if (lastClose <= 0 || referenceClose <= 0) {
            return Evaluation.notHighRisk();
        }
        double atrPct = atr / lastClose * 100.0;
        double return2hPct = (lastClose / referenceClose - 1.0) * 100.0;
        double twoHourDropPct = Math.max(0.0, -return2hPct);
        double adverseAtrRatio = atrPct > 0.0 ? twoHourDropPct / atrPct : 0.0;
        return new Evaluation(atr, atrPct, lastClose, referenceClose, return2hPct, twoHourDropPct,
                adverseAtrRatio, atrPct + 1e-9 >= 2.5 && adverseAtrRatio + 1e-9 >= 0.5);
    }

    private List<Kline> safeKlines(String symbol) {
        List<Kline> klines = binanceFuturesClient.getKlines(symbol, INTERVAL, KLINE_LIMIT);
        return klines == null ? List.of() : klines;
    }

    private boolean hasOhlc(Kline candle) {
        return candle != null && candle.getCloseTime() != null && candle.getHigh() != null
                && candle.getLow() != null && candle.getClose() != null;
    }

    private double initialAtr(List<Kline> candles) {
        double total = 0.0;
        for (int index = 1; index <= ATR_PERIOD; index++) {
            total += trueRange(candles.get(index), candles.get(index - 1));
        }
        return total / ATR_PERIOD;
    }

    private double trueRange(Kline current, Kline previous) {
        double highLow = current.getHigh().subtract(current.getLow()).doubleValue();
        double highPreviousClose = Math.abs(current.getHigh().subtract(previous.getClose()).doubleValue());
        double lowPreviousClose = Math.abs(current.getLow().subtract(previous.getClose()).doubleValue());
        return Math.max(highLow, Math.max(highPreviousClose, lowPreviousClose));
    }

    public record Evaluation(double atr14, double atrPct, double lastCompleted30mClose, double closeFourBarsAgo,
                             double return2hPct, double twoHourDropPct, double adverseAtrRatio, boolean highRiskLong) {
        static Evaluation notHighRisk() {
            return new Evaluation(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false);
        }
    }
}
