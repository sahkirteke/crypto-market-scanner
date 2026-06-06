package com.crypto.scanner.model;

import com.crypto.domain.model.BookTicker;
import com.crypto.domain.model.FuturesSnapshot;
import com.crypto.domain.model.TechnicalSnapshot;
import com.crypto.domain.model.Ticker24h;
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
public class CoinScoringInput {
    private String symbol;
    private TechnicalSnapshot oneHour;
    private TechnicalSnapshot fourHour;
    private Ticker24h ticker24h;
    private BookTicker bookTicker;
    private FuturesSnapshot futuresSnapshot;
    private MarketRegimeResult marketRegimeResult;
}
