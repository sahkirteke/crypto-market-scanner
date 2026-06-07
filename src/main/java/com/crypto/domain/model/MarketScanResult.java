package com.crypto.domain.model;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.ScanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
public class MarketScanResult {
    private Long scanRunId;
    private ScanType scanType;
    private Instant scanTimeUtc;
    private String scanTimeText;
    private MarketRegime marketRegime;
    private BigDecimal marketBreadthPct;
    private Integer totalSymbols;
    private Integer preFilterPassedCount;
    private Integer strongLongCount;
    private Integer strongShortCount;
    private Integer watchlistCount;
    private Integer eliminatedCount;
    @Builder.Default
    private List<CoinScanResult> strongLong = new ArrayList<>();
    @Builder.Default
    private List<CoinScanResult> strongShort = new ArrayList<>();
    @Builder.Default
    private List<CoinScanResult> watchlist = new ArrayList<>();
    @Builder.Default
    private List<CoinScanResult> eliminated = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> reasons = new ArrayList<>();
    @Builder.Default
    private List<ReasonTag> warnings = new ArrayList<>();
}
