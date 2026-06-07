package com.crypto.persistence.entity;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.ScanType;
import com.crypto.common.time.IstanbulTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "market_scan_runs")
public class MarketScanRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_time_utc", nullable = false)
    private Instant scanTimeUtc;

    @Column(name = "scan_time_istanbul_text", length = 64)
    private String scanTimeIstanbulText;

    @Column(name = "scan_time_text", length = 64)
    private String scanTimeText;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false, length = 32)
    private ScanType scanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_regime", length = 32)
    private MarketRegime marketRegime;

    @Column(name = "market_breadth_pct", precision = 20, scale = 8)
    private BigDecimal marketBreadthPct;

    @Column(name = "total_symbols")
    private Integer totalSymbols;

    @Column(name = "prefilter_passed_count")
    private Integer preFilterPassedCount;

    @Column(name = "strong_long_count")
    private Integer strongLongCount;

    @Column(name = "strong_short_count")
    private Integer strongShortCount;

    @Column(name = "watchlist_count")
    private Integer watchlistCount;

    @Column(name = "eliminated_count")
    private Integer eliminatedCount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "reasons_json", columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        syncTextFields();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        syncTextFields();
    }

    private void syncTextFields() {
        scanTimeText = IstanbulTimeUtil.format(scanTimeUtc);
    }
}
