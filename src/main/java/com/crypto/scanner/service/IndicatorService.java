package com.crypto.scanner.service;

import com.crypto.common.enums.ReasonTag;
import com.crypto.domain.model.Kline;
import com.crypto.domain.model.KlineBundle;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.TechnicalSnapshotPair;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

@Slf4j
@Service
public class IndicatorService {
    private static final String ONE_HOUR_INTERVAL = "1h";
    private static final String FOUR_HOUR_INTERVAL = "4h";
    private static final int MIN_CLOSED_CANDLES = 220;
    private static final int VOLUME_RATIO_SCALE = 8;
    private static final int BOLLINGER_PERIOD = 20;
    private static final BigDecimal BOLLINGER_STD_DEV_MULTIPLIER = new BigDecimal("2");
    private static final int BOLLINGER_SCALE = 12;

    public TechnicalSnapshot calculate(String symbol, String interval, List<Kline> klines) {
        try {
            BarSeries series = toBarSeries(symbol, klines);
            if (series.getBarCount() < MIN_CLOSED_CANDLES) {
                throw new IllegalArgumentException("minimum closed kline count is " + MIN_CLOSED_CANDLES
                        + ", actual=" + series.getBarCount());
            }

            int latestIndex = series.getEndIndex();
            int previousIndex = latestIndex - 1;

            ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
            EMAIndicator ema20Indicator = new EMAIndicator(closePriceIndicator, 20);
            EMAIndicator ema50Indicator = new EMAIndicator(closePriceIndicator, 50);
            EMAIndicator ema200Indicator = new EMAIndicator(closePriceIndicator, 200);
            RSIIndicator rsi14Indicator = new RSIIndicator(closePriceIndicator, 14);
            MACDIndicator macdIndicator = new MACDIndicator(closePriceIndicator, 12, 26);
            EMAIndicator signalLineIndicator = new EMAIndicator(macdIndicator, 9);
            ATRIndicator atr14Indicator = new ATRIndicator(series, 14);
            VolumeIndicator volumeIndicator = new VolumeIndicator(series);
            SMAIndicator volumeSma20Indicator = new SMAIndicator(volumeIndicator, 20);

            BigDecimal close = toBigDecimal(closePriceIndicator.getValue(latestIndex));
            BigDecimal ema20 = toBigDecimal(ema20Indicator.getValue(latestIndex));
            BigDecimal rsi14 = toBigDecimal(rsi14Indicator.getValue(latestIndex));
            BigDecimal macdHist = macdHistogram(macdIndicator, signalLineIndicator, latestIndex);
            BigDecimal volumeSma20 = toBigDecimal(volumeSma20Indicator.getValue(latestIndex));
            BigDecimal latestVolume = toBigDecimal(volumeIndicator.getValue(latestIndex));
            BigDecimal volumeRatio = calculateVolumeRatio(latestVolume, volumeSma20);
            BollingerValues bollinger = calculateBollinger(series, latestIndex, close);

            TechnicalSnapshot snapshot = TechnicalSnapshot.builder()
                    .symbol(symbol)
                    .interval(interval)
                    .candleCloseTime(series.getBar(latestIndex).getEndTime())
                    .close(close)
                    .previousClose(toBigDecimal(closePriceIndicator.getValue(previousIndex)))
                    .previousHigh(toBigDecimal(series.getBar(previousIndex).getHighPrice()))
                    .previousLow(toBigDecimal(series.getBar(previousIndex).getLowPrice()))
                    .ema20(ema20)
                    .ema50(toBigDecimal(ema50Indicator.getValue(latestIndex)))
                    .ema200(toBigDecimal(ema200Indicator.getValue(latestIndex)))
                    .rsi14(rsi14)
                    .previousRsi14(toBigDecimal(rsi14Indicator.getValue(previousIndex)))
                    .macdHist(macdHist)
                    .previousMacdHist(macdHistogram(macdIndicator, signalLineIndicator, previousIndex))
                    .atr14(toBigDecimal(atr14Indicator.getValue(latestIndex)))
                    .volumeSma20(volumeSma20)
                    .volumeRatio(volumeRatio)
                    .bbMiddle(bollinger.bbMiddle())
                    .bbUpper(bollinger.bbUpper())
                    .bbLower(bollinger.bbLower())
                    .bbWidth(bollinger.bbWidth())
                    .bbPercentB(bollinger.bbPercentB())
                    .bbUpperTouched(bollinger.bbUpperTouched())
                    .bbLowerTouched(bollinger.bbLowerTouched())
                    .bbUpperClosedOutside(bollinger.bbUpperClosedOutside())
                    .bbLowerClosedOutside(bollinger.bbLowerClosedOutside())
                    .build();

            log.info("TECH_READY symbol={} interval={} close={} ema20={} rsi14={} macdHist={} volumeRatio={} bbPercentB={} bbWidth={}",
                    symbol, interval, close, ema20, rsi14, macdHist, volumeRatio, bollinger.bbPercentB(), bollinger.bbWidth());
            return snapshot;
        } catch (IllegalArgumentException exception) {
            log.info("TECH_NOT_READY symbol={} interval={} reason=DATA_NOT_READY message={}",
                    symbol, interval, exception.getMessage());
            throw exception;
        }
    }

    public TechnicalSnapshot calculateOneHour(String symbol, List<Kline> oneHourKlines) {
        return calculate(symbol, ONE_HOUR_INTERVAL, oneHourKlines);
    }

    public TechnicalSnapshot calculateFourHour(String symbol, List<Kline> fourHourKlines) {
        return calculate(symbol, FOUR_HOUR_INTERVAL, fourHourKlines);
    }

    public TechnicalSnapshotPair calculatePair(KlineBundle bundle) {
        String symbol = bundle == null ? null : bundle.getSymbol();
        TechnicalSnapshotPair pair = TechnicalSnapshotPair.builder()
                .symbol(symbol)
                .warnings(bundle == null ? new ArrayList<>() : new ArrayList<>(bundle.getWarnings()))
                .build();

        try {
            if (bundle == null) {
                throw new IllegalArgumentException("kline bundle must not be null");
            }
            pair.setOneHour(calculateOneHour(symbol, bundle.getOneHourKlines()));
            pair.setFourHour(calculateFourHour(symbol, bundle.getFourHourKlines()));
            pair.setReady(true);
            log.info("TECH_PAIR_READY symbol={}", symbol);
            return pair;
        } catch (IllegalArgumentException exception) {
            pair.setReady(false);
            if (!pair.getReasons().contains(ReasonTag.DATA_NOT_READY)) {
                pair.getReasons().add(ReasonTag.DATA_NOT_READY);
            }
            log.info("TECH_PAIR_NOT_READY symbol={} reasons={}", symbol, pair.getReasons());
            return pair;
        }
    }

    private BarSeries toBarSeries(String symbol, List<Kline> klines) {
        if (klines == null) {
            throw new IllegalArgumentException("kline list must not be null");
        }

        BarSeries series = new BaseBarSeriesBuilder()
                .withName(symbol)
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        klines.stream()
                .filter(kline -> kline != null
                        && !Boolean.FALSE.equals(kline.getClosed())
                        && kline.getCloseTime() != null
                        && kline.getOpenTime() != null)
                .sorted(Comparator.comparing(Kline::getOpenTime))
                .forEach(kline -> addBar(series, kline));
        return series;
    }

    private void addBar(BarSeries series, Kline kline) {
        if (kline.getOpen() == null || kline.getHigh() == null || kline.getLow() == null
                || kline.getClose() == null || kline.getVolume() == null) {
            throw new IllegalArgumentException("kline contains null OHLCV value");
        }

        series.addBar(series.barBuilder()
                .beginTime(kline.getOpenTime())
                .endTime(kline.getCloseTime())
                .openPrice(kline.getOpen())
                .highPrice(kline.getHigh())
                .lowPrice(kline.getLow())
                .closePrice(kline.getClose())
                .volume(kline.getVolume())
                .build());
    }

    private BigDecimal macdHistogram(Indicator<Num> macdIndicator, Indicator<Num> signalLineIndicator, int index) {
        Num macdLine = macdIndicator.getValue(index);
        Num signalLine = signalLineIndicator.getValue(index);
        if (Num.isNaNOrNull(macdLine) || Num.isNaNOrNull(signalLine)) {
            return null;
        }
        return toBigDecimal(macdLine.minus(signalLine));
    }

    private BigDecimal calculateVolumeRatio(BigDecimal latestVolume, BigDecimal volumeSma20) {
        if (latestVolume == null || volumeSma20 == null || volumeSma20.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return latestVolume.divide(volumeSma20, VOLUME_RATIO_SCALE, RoundingMode.HALF_UP);
    }

    public BollingerValues calculateBollingerForTest(BigDecimal close, BigDecimal high, BigDecimal low, BigDecimal bbMiddle, BigDecimal bbUpper, BigDecimal bbLower) {
        return buildBollingerValues(close, high, low, bbMiddle, bbUpper, bbLower);
    }

    private BollingerValues calculateBollinger(BarSeries series, int latestIndex, BigDecimal close) {
        if (series == null || latestIndex < BOLLINGER_PERIOD - 1 || close == null) {
            return BollingerValues.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = latestIndex - BOLLINGER_PERIOD + 1; index <= latestIndex; index++) {
            BigDecimal value = toBigDecimal(series.getBar(index).getClosePrice());
            if (value == null) {
                return BollingerValues.empty();
            }
            sum = sum.add(value);
        }
        BigDecimal middle = sum.divide(BigDecimal.valueOf(BOLLINGER_PERIOD), BOLLINGER_SCALE, RoundingMode.HALF_UP);
        BigDecimal varianceSum = BigDecimal.ZERO;
        for (int index = latestIndex - BOLLINGER_PERIOD + 1; index <= latestIndex; index++) {
            BigDecimal diff = toBigDecimal(series.getBar(index).getClosePrice()).subtract(middle);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(BOLLINGER_PERIOD), BOLLINGER_SCALE, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal upper = middle.add(stdDev.multiply(BOLLINGER_STD_DEV_MULTIPLIER));
        BigDecimal lower = middle.subtract(stdDev.multiply(BOLLINGER_STD_DEV_MULTIPLIER));
        BigDecimal high = toBigDecimal(series.getBar(latestIndex).getHighPrice());
        BigDecimal low = toBigDecimal(series.getBar(latestIndex).getLowPrice());
        return buildBollingerValues(close, high, low, middle, upper, lower);
    }

    private BollingerValues buildBollingerValues(BigDecimal close, BigDecimal high, BigDecimal low, BigDecimal middle, BigDecimal upper, BigDecimal lower) {
        if (close == null || middle == null || upper == null || lower == null) {
            return BollingerValues.empty();
        }
        BigDecimal bandRange = upper.subtract(lower);
        BigDecimal width = middle.compareTo(BigDecimal.ZERO) == 0
                ? null
                : bandRange.divide(middle, BOLLINGER_SCALE, RoundingMode.HALF_UP);
        BigDecimal percentB = bandRange.compareTo(BigDecimal.ZERO) == 0
                ? null
                : close.subtract(lower).divide(bandRange, BOLLINGER_SCALE, RoundingMode.HALF_UP);
        return new BollingerValues(
                middle,
                upper,
                lower,
                width,
                percentB,
                high != null && high.compareTo(upper) >= 0,
                low != null && low.compareTo(lower) <= 0,
                close.compareTo(upper) > 0,
                close.compareTo(lower) < 0
        );
    }

    public record BollingerValues(
            BigDecimal bbMiddle,
            BigDecimal bbUpper,
            BigDecimal bbLower,
            BigDecimal bbWidth,
            BigDecimal bbPercentB,
            Boolean bbUpperTouched,
            Boolean bbLowerTouched,
            Boolean bbUpperClosedOutside,
            Boolean bbLowerClosedOutside
    ) {
        static BollingerValues empty() {
            return new BollingerValues(null, null, null, null, null, false, false, false, false);
        }
    }

    private BigDecimal toBigDecimal(Num num) {
        if (num == null || Num.isNaNOrNull(num) || Double.isInfinite(num.doubleValue())) {
            return null;
        }
        return num.bigDecimalValue();
    }
}
