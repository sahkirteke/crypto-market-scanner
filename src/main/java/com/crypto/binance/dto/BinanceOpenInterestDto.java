package com.crypto.binance.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BinanceOpenInterestDto {
    private String symbol;
    private String openInterest;
    private Long time;
}
