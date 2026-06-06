package com.crypto.binance.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BinanceFundingRateDto {
    private String symbol;
    private String fundingRate;
    private Long fundingTime;
}
