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
public class Ticker24h {
    private String symbol;
    private BigDecimal lastPrice;
    private BigDecimal priceChange;
    private BigDecimal priceChangePercent;
    private BigDecimal quoteVolume;
    private BigDecimal volume;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private Instant closeTime;
}
