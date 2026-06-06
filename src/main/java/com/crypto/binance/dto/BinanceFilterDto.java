package com.crypto.binance.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BinanceFilterDto {
    private String filterType;
    private String tickSize;
    private String stepSize;
    private String minQty;
    private String minNotional;
    private String notional;
}
