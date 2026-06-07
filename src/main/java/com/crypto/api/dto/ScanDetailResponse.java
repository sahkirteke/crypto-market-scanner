package com.crypto.api.dto;

import java.util.List;

public record ScanDetailResponse(
        MarketScanRunResponse scan,
        List<CoinScanResultResponse> strongLong,
        List<CoinScanResultResponse> strongShort,
        List<CoinScanResultResponse> watchlist,
        List<CoinScanResultResponse> eliminated
) {
}
