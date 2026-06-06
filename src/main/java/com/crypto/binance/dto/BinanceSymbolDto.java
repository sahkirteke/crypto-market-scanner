package com.crypto.binance.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BinanceSymbolDto {
    private String symbol;
    private String pair;
    private String contractType;
    private String status;
    private String baseAsset;
    private String quoteAsset;
    private Integer pricePrecision;
    private Integer quantityPrecision;
    private List<BinanceFilterDto> filters;
}
