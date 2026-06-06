package com.crypto.domain.model;

import java.math.BigDecimal;
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
public class SymbolInfo {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private String contractType;
    private String status;
    private Integer pricePrecision;
    private Integer quantityPrecision;
    private BigDecimal tickSize;
    private BigDecimal stepSize;
    private BigDecimal minQty;
    private BigDecimal minNotional;
}
