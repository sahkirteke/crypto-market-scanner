package com.crypto.persistence.entity;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.paper.model.PaperPositionStatus;
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
@Entity
@Table(name = "paper_positions")
public class PaperPositionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 16)
    private PositionSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaperPositionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_action", nullable = false, length = 32)
    private EntryAction entryAction;

    @Column(name = "entry_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal entryPrice;

    @Column(name = "quantity", nullable = false, precision = 30, scale = 12)
    private BigDecimal quantity;

    @Column(name = "notional_usdt", nullable = false, precision = 30, scale = 8)
    private BigDecimal notionalUsdt;

    @Column(name = "leverage", nullable = false)
    private Integer leverage;

    @Column(name = "entry_score")
    private Integer entryScore;

    @Column(name = "long_score")
    private Integer longScore;

    @Column(name = "short_score")
    private Integer shortScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_classification", length = 32)
    private CoinClassification sourceClassification;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction_bias", length = 32)
    private DirectionBias directionBias;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 32)
    private RiskLevel riskLevel;

    @Column(name = "funding_rate", precision = 20, scale = 10)
    private BigDecimal fundingRate;

    @Column(name = "open_interest", precision = 30, scale = 8)
    private BigDecimal openInterest;

    @Column(name = "market_breadth_pct", precision = 20, scale = 8)
    private BigDecimal marketBreadthPct;

    @Column(name = "price_change_24h_pct", precision = 20, scale = 8)
    private BigDecimal priceChange24hPct;

    @Column(name = "spread_pct", precision = 20, scale = 8)
    private BigDecimal spreadPct;

    @Column(name = "entry_reason", length = 128)
    private String entryReason;

    @Column(name = "signal_reason", length = 128)
    private String signalReason;

    @Column(name = "reasons_json", columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;


    @Column(name = "current_price", precision = 30, scale = 12)
    private BigDecimal currentPrice;

    @Column(name = "highest_price", precision = 30, scale = 12)
    private BigDecimal highestPrice;

    @Column(name = "lowest_price", precision = 30, scale = 12)
    private BigDecimal lowestPrice;

    @Column(name = "max_favorable_move_pct", precision = 20, scale = 8)
    private BigDecimal maxFavorableMovePct;

    @Column(name = "max_adverse_move_pct", precision = 20, scale = 8)
    private BigDecimal maxAdverseMovePct;

    @Column(name = "bars_held")
    private Integer barsHeld;

    @Column(name = "minutes_held")
    private Integer minutesHeld;

    @Column(name = "entry_signal_score")
    private Integer entrySignalScore;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "take_profit_pct", precision = 20, scale = 8)
    private BigDecimal takeProfitPct;

    @Column(name = "stop_loss_pct", precision = 20, scale = 8)
    private BigDecimal stopLossPct;

    @Column(name = "time_stop_minutes")
    private Integer timeStopMinutes;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "exit_price", precision = 30, scale = 12)
    private BigDecimal exitPrice;

    @Column(name = "realized_pnl_usdt", precision = 30, scale = 8)
    private BigDecimal realizedPnlUsdt;

    @Column(name = "realized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal realizedPnlPct;

    @Column(name = "exit_reason", length = 128)
    private String exitReason;

    @Column(name = "exit_detail", columnDefinition = "TEXT")
    private String exitDetail;

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
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
