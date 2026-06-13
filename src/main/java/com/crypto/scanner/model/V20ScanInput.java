package com.crypto.scanner.model;

import com.crypto.common.enums.MarketRegime;
import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.SymbolInfo;
import com.crypto.domain.model.TechnicalSnapshot;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class V20ScanInput {
    private Long scanRunId;
    private String symbol;
    private SymbolInfo symbolInfo;
    private BookTicker bookTicker;
    private TechnicalSnapshot fourHour;
    private TechnicalSnapshot oneHour;
    private BigDecimal fundingRate;
    private List<BigDecimal> fundingRates;
    private MarketRegime marketRegime;
    private Boolean qualityPass;
    private Boolean qualityFail;
}
