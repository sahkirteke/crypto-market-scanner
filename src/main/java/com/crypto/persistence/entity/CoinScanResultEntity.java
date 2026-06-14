package com.crypto.persistence.entity;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EliminationReason;
import com.crypto.common.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coin_scan_results")
public class CoinScanResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_run_id", nullable = false)
    private MarketScanRunEntity scanRun;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction_bias", length = 32)
    private DirectionBias directionBias;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 32)
    private CoinClassification classification;

    @Column(name = "score")
    private Integer score;

    @Column(name = "long_score")
    private Integer longScore;

    @Column(name = "short_score")
    private Integer shortScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 32)
    private RiskLevel riskLevel;

    @Column(name = "last_price", precision = 30, scale = 12)
    private BigDecimal lastPrice;

    @Column(name = "price_change_24h_pct", precision = 20, scale = 8)
    private BigDecimal priceChange24hPct;

    @Column(name = "quote_volume_24h", precision = 30, scale = 8)
    private BigDecimal quoteVolume24h;

    @Column(name = "spread_pct", precision = 20, scale = 8)
    private BigDecimal spreadPct;

    @Column(name = "funding_rate", precision = 20, scale = 10)
    private BigDecimal fundingRate;

    @Column(name = "open_interest", precision = 30, scale = 8)
    private BigDecimal openInterest;

    @Column(name = "market_breadth_pct", precision = 20, scale = 8)
    private BigDecimal marketBreadthPct;


    @Column(name = "close_1h", precision = 30, scale = 12)
    private BigDecimal close1h;
    @Column(name = "ema20_1h", precision = 30, scale = 12)
    private BigDecimal ema20_1h;
    @Column(name = "rsi14_1h", precision = 20, scale = 8)
    private BigDecimal rsi14_1h;
    @Column(name = "volume_ratio_1h", precision = 20, scale = 8)
    private BigDecimal volumeRatio_1h;
    @Column(name = "close_4h", precision = 30, scale = 12)
    private BigDecimal close4h;
    @Column(name = "ema20_4h", precision = 30, scale = 12)
    private BigDecimal ema20_4h;
    @Column(name = "ema50_4h", precision = 30, scale = 12)
    private BigDecimal ema50_4h;
    @Column(name = "ema200_4h", precision = 30, scale = 12)
    private BigDecimal ema200_4h;
    @Column(name = "rsi14_4h", precision = 20, scale = 8)
    private BigDecimal rsi14_4h;
    @Column(name = "macd_hist_4h", precision = 30, scale = 12)
    private BigDecimal macdHist_4h;
    @Column(name = "atr14_4h", precision = 30, scale = 12)
    private BigDecimal atr14_4h;
    @Column(name = "volume_ratio_4h", precision = 20, scale = 8)
    private BigDecimal volumeRatio_4h;

    @Column(name = "reasons_json", columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "eliminated_reason", length = 64)
    private EliminationReason eliminatedReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
