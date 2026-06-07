package com.crypto.system.dto;

public record DbConsistencyResponse(
        Long latestScanId,
        Integer latestScanTotalSymbols,
        Long latestCoinResultCount,
        Boolean latestScanConsistent,
        Long openPaperPositionCount,
        Long closedPaperPositionCount
) {
}
