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
public class Kline {
    private String symbol;
    private String interval;
    private Instant openTime;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private Instant closeTime;
    private BigDecimal quoteAssetVolume;
    private Long numberOfTrades;
    private BigDecimal takerBuyBaseVolume;
    private BigDecimal takerBuyQuoteVolume;
    private Boolean closed;
}
