package com.crypto.api.dto;

import java.util.List;

public record LatestScanResponse(
        MarketScanRunResponse scan,
        List<CoinScanResultResponse> strongLong,
        List<CoinScanResultResponse> strongShort,
        List<CoinScanResultResponse> watchlist
) {
}
