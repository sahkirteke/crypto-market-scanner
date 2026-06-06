package com.crypto.binance.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BinanceTicker24hDto {
    private String symbol;
    private String lastPrice;
    private String priceChange;
    private String priceChangePercent;
    private String quoteVolume;
    private String volume;
    private String highPrice;
    private String lowPrice;
    private Long closeTime;
}
