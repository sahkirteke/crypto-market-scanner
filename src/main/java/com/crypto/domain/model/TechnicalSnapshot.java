package com.crypto.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalSnapshot {
    private String symbol;
    private String interval;
    private Instant candleCloseTime;
    private BigDecimal close;
    private BigDecimal previousClose;
    private BigDecimal previousHigh;
    private BigDecimal previousLow;
    private BigDecimal ema20;
    private BigDecimal ema50;
    private BigDecimal ema200;
    private BigDecimal rsi14;
    private BigDecimal previousRsi14;
    private BigDecimal macdHist;
    private BigDecimal previousMacdHist;
    private BigDecimal atr14;
    private BigDecimal volumeSma20;
    private BigDecimal volumeRatio;
    private BigDecimal bbMiddle;
    private BigDecimal bbUpper;
    private BigDecimal bbLower;
    private BigDecimal bbWidth;
    private BigDecimal bbPercentB;
    private Boolean bbUpperTouched;
    private Boolean bbLowerTouched;
    private Boolean bbUpperClosedOutside;
    private Boolean bbLowerClosedOutside;
}
