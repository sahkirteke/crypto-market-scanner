package com.crypto.laplace.persistence;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplacePositionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @Entity
@Table(name="laplace_paper_positions")
public class LaplacePaperPositionEntity {
 @Id @Column(length=36) private String id;
 @Column(nullable=false,length=64) private String strategy;
 @Column(nullable=false,length=16) private String strategyVersion;
 @Column(nullable=false,length=32) private String symbol;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=8) private PositionSide side;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private LaplacePositionStatus status;
 @Column(nullable=false,length=160,unique=true) private String entrySignalId;
 @Column(length=8) private String entryRawSignal;
 @Column(nullable=false) private Boolean signalInverted;
 @Column(nullable=false) private Instant entryCandleCloseTime;
 @Column(nullable=false) private Instant entryTime;
 @Column(nullable=false,precision=30,scale=12) private BigDecimal entrySignalClosePrice;
 @Column(nullable=false,precision=30,scale=12) private BigDecimal entryExecutionPrice;
 @Column(nullable=false,precision=30,scale=12) private BigDecimal margin;
 @Column(nullable=false,precision=30,scale=12) private BigDecimal quantity;
 @Column(precision=30,scale=12) private BigDecimal originalQuantity;
 @Column(precision=30,scale=12) private BigDecimal remainingQuantity;
 @Column(nullable=false,precision=30,scale=8) private BigDecimal notional;
 @Column(precision=30,scale=8) private BigDecimal originalNotional;
 @Column(precision=30,scale=12) private BigDecimal remainingEntryNotional;
 @Column(nullable=false) private Integer leverage;
 @Column(nullable=false,precision=20,scale=10) private BigDecimal entryFeeRate;
 @Column(nullable=false,precision=30,scale=12) private BigDecimal entryFee;
 private Boolean partialTakeProfitExecuted;
 private Instant partialTakeProfitTime;
 private Instant partialTriggerCandleCloseTime;
 @Column(precision=30,scale=12) private BigDecimal partialTakeProfitPrice;
 @Column(precision=30,scale=12) private BigDecimal partialTakeProfitQuantity;
 @Column(precision=30,scale=12) private BigDecimal partialExitNotional;
 @Column(precision=30,scale=12) private BigDecimal partialExitFee;
 @Column(precision=30,scale=12) private BigDecimal partialGrossPnl;
 @Column(precision=30,scale=12) private BigDecimal breakEvenStopPrice;
 private Instant breakEvenActiveAfterCandleCloseTime;
 @Column(precision=30,scale=12) private BigDecimal cumulativeExitFee;
 @Column(precision=30,scale=12) private BigDecimal realizedGrossPnl;
 private Instant lastManagedFiveMinuteCandleCloseTime;
 private Boolean fastExitMode;
 @Column(precision=10,scale=4) private BigDecimal positiveBreadth30Pct;
 private Instant exitTime;
 @Column(precision=30,scale=12) private BigDecimal exitExecutionPrice;
 @Column(precision=30,scale=12) private BigDecimal exitFee;
 @Column(precision=30,scale=12) private BigDecimal grossPnl;
 @Column(precision=20,scale=8) private BigDecimal grossPnlPct;
 @Column(precision=30,scale=12) private BigDecimal netPnl;
 @Column(precision=20,scale=8) private BigDecimal netPnlPct;
 private Long holdingMinutes;
 @Column(length=80) private String exitReason;
 @Column(length=160,unique=true) private String exitSignalId;
 @Version private Long version;
}
