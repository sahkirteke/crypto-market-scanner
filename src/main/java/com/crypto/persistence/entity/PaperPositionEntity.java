package com.crypto.persistence.entity;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.DirectionBias;
import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.common.enums.ScanType;
import com.crypto.paper.model.PaperPositionStatus;
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

    @Column(name = "source_scan_run_id")
    private Long sourceScanRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_scan_type", length = 32)
    private ScanType sourceScanType;

    @Column(name = "source_candidate_id")
    private Long sourceCandidateId;

    @Column(name = "entry_close_1h", precision = 30, scale = 12)
    private BigDecimal entryClose1h;

    @Column(name = "entry_ema20_1h", precision = 30, scale = 12)
    private BigDecimal entryEma20_1h;

    @Column(name = "entry_rsi14_1h", precision = 20, scale = 8)
    private BigDecimal entryRsi14_1h;

    @Column(name = "entry_macd_hist_1h", precision = 30, scale = 12)
    private BigDecimal entryMacdHist_1h;

    @Column(name = "entry_atr14_1h", precision = 30, scale = 12)
    private BigDecimal entryAtr14_1h;

    @Column(name = "entry_volume_ratio_1h", precision = 20, scale = 8)
    private BigDecimal entryVolumeRatio_1h;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 16)
    private PositionSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_signal_side", length = 16)
    private PositionSide sourceSignalSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_side", length = 16)
    private PositionSide executionSide;

    @Column(name = "signal_inverted")
    private Boolean signalInverted;

    @Column(name = "inversion_reason", length = 64)
    private String inversionReason;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "market_regime", length = 32)
    private MarketRegime marketRegime;

    @Column(name = "entry_bb_score", precision = 20, scale = 8)
    private BigDecimal entryBbScore;

    @Column(name = "entry_bb_percent_b", precision = 20, scale = 8)
    private BigDecimal entryBbPercentB;

    @Column(name = "entry_bb_width", precision = 20, scale = 8)
    private BigDecimal entryBbWidth;

    @Column(name = "entry_bb_upper", precision = 30, scale = 12)
    private BigDecimal entryBbUpper;

    @Column(name = "entry_bb_middle", precision = 30, scale = 12)
    private BigDecimal entryBbMiddle;

    @Column(name = "entry_bb_lower", precision = 30, scale = 12)
    private BigDecimal entryBbLower;

    @Column(name = "entry_bb_upper_touched")
    private Boolean entryBbUpperTouched;

    @Column(name = "entry_bb_lower_touched")
    private Boolean entryBbLowerTouched;

    @Column(name = "entry_bb_upper_closed_outside")
    private Boolean entryBbUpperClosedOutside;

    @Column(name = "entry_bb_lower_closed_outside")
    private Boolean entryBbLowerClosedOutside;

    @Column(name = "entry_bb_reasons_json", columnDefinition = "TEXT")
    private String entryBbReasonsJson;

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

    @Column(name = "opened_at_text", length = 64)
    private String openedAtText;


    @Column(name = "bid_price", precision = 30, scale = 12)
    private BigDecimal bidPrice;

    @Column(name = "ask_price", precision = 30, scale = 12)
    private BigDecimal askPrice;

    @Column(name = "mid_price", precision = 30, scale = 12)
    private BigDecimal midPrice;

    @Column(name = "initial_stop", precision = 30, scale = 12)
    private BigDecimal initialStop;

    @Column(name = "current_stop", precision = 30, scale = 12)
    private BigDecimal currentStop;

    @Column(name = "risk_per_unit", precision = 30, scale = 12)
    private BigDecimal riskPerUnit;

    @Column(name = "tp1", precision = 30, scale = 12)
    private BigDecimal tp1;

    @Column(name = "tp2", precision = 30, scale = 12)
    private BigDecimal tp2;

    @Column(name = "tp1_hit")
    private Boolean tp1Hit;

    @Column(name = "tp2_hit")
    private Boolean tp2Hit;

    @Column(name = "trailing_active")
    private Boolean trailingActive;

    @Column(name = "trailing_activated_at_bar_close_time")
    private Instant trailingActivatedAtBarCloseTime;

    @Column(name = "remaining_position_pct", precision = 20, scale = 8)
    private BigDecimal remainingPositionPct;

    @Column(name = "highest_price_since_entry", precision = 30, scale = 12)
    private BigDecimal highestPriceSinceEntry;

    @Column(name = "lowest_price_since_entry", precision = 30, scale = 12)
    private BigDecimal lowestPriceSinceEntry;

    @Column(name = "bars_in_position")
    private Integer barsInPosition;

    @Column(name = "entry_price_adjusted", precision = 30, scale = 12)
    private BigDecimal entryPriceAdjusted;

    @Column(name = "exit_price_adjusted", precision = 30, scale = 12)
    private BigDecimal exitPriceAdjusted;

    @Column(name = "raw_realized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal rawRealizedPnlPct;

    @Column(name = "net_realized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal netRealizedPnlPct;

    @Column(name = "leveraged_net_realized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal leveragedNetRealizedPnlPct;

    @Column(name = "raw_unrealized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal rawUnrealizedPnlPct;

    @Column(name = "net_unrealized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal netUnrealizedPnlPct;

    @Column(name = "leveraged_net_unrealized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal leveragedNetUnrealizedPnlPct;

    @Column(name = "total_fee_pct", precision = 20, scale = 8)
    private BigDecimal totalFeePct;

    @Column(name = "total_slippage_pct", precision = 20, scale = 8)
    private BigDecimal totalSlippagePct;


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

    @Column(name = "last_checked_at_text", length = 64)
    private String lastCheckedAtText;

    @Column(name = "last_exit_candle_close_time")
    private Instant lastExitCandleCloseTime;

    @Column(name = "prev3_5m_return", precision = 20, scale = 12)
    private BigDecimal prev3_5mReturn;

    @Column(name = "early_exit_triggered")
    private Boolean earlyExitTriggered;

    @Column(name = "early_exit_rule_a")
    private Boolean earlyExitRuleA;

    @Column(name = "early_exit_rule_b")
    private Boolean earlyExitRuleB;

    @Column(name = "first15_high_pct", precision = 20, scale = 12)
    private BigDecimal first15HighPct;

    @Column(name = "close15_pct", precision = 20, scale = 12)
    private BigDecimal close15Pct;

    @Column(name = "early_exit_price", precision = 30, scale = 12)
    private BigDecimal earlyExitPrice;

    @Column(name = "early_exit_time")
    private Instant earlyExitTime;

    @Column(name = "take_profit_pct", precision = 20, scale = 8)
    private BigDecimal takeProfitPct;

    @Column(name = "stop_loss_pct", precision = 20, scale = 8)
    private BigDecimal stopLossPct;

    @Column(name = "time_stop_minutes")
    private Integer timeStopMinutes;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_at_text", length = 64)
    private String closedAtText;

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

    @Column(name = "created_at_text", length = 64)
    private String createdAtText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_at_text", length = 64)
    private String updatedAtText;

    @Column(name = "symbol_trade_entry_logged")
    private Boolean symbolTradeEntryLogged;

    @Column(name = "symbol_trade_exit_logged")
    private Boolean symbolTradeExitLogged;

    @Column(name = "symbol_trade_tp1_exit_logged")
    private Boolean symbolTradeTp1ExitLogged;

    @Column(name = "symbol_trade_tp2_exit_logged")
    private Boolean symbolTradeTp2ExitLogged;

    @Column(name = "symbol_trade_final_exit_logged")
    private Boolean symbolTradeFinalExitLogged;

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
        openedAtText = IstanbulTimeUtil.format(openedAt);
        closedAtText = IstanbulTimeUtil.format(closedAt);
        lastCheckedAtText = IstanbulTimeUtil.format(lastCheckedAt);
        createdAtText = IstanbulTimeUtil.format(createdAt);
        updatedAtText = IstanbulTimeUtil.format(updatedAt);
    }
}
