package com.crypto.laplace.persistence;

import com.crypto.laplace.model.LaplaceRuntimeState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "laplace_inverted_false_sessions")
public class LaplaceInvertedFalseSessionEntity {
    @Id @Column(length = 36) private String sessionId;
    @Column(nullable = false) private Instant sessionStartTime;
    @Column(nullable = false, precision = 30, scale = 12) private BigDecimal sessionStartCapital;
    @Column(nullable = false, precision = 30, scale = 12) private BigDecimal marginPerPosition;
    @Column(nullable = false, precision = 30, scale = 12) private BigDecimal startingNotional;
    @Column(nullable = false) private Integer leverage;
    @Column(nullable = false, precision = 10, scale = 6) private BigDecimal profitTargetPct;
    @Column(nullable = false, precision = 10, scale = 6) private BigDecimal minimumLockedProfitPct;
    @Column(nullable = false, precision = 30, scale = 12) private BigDecimal profitTargetUsdt;
    @Column(nullable = false, precision = 30, scale = 12) private BigDecimal minimumLockedProfitUsdt;
    @Column(nullable = false, precision = 30, scale = 12) private BigDecimal realizedSessionNetPnl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private LaplaceRuntimeState runtimeState;
    private Instant cooldownStartedAt;
    private Instant cooldownUntil;
    @Column(precision = 30, scale = 12) private BigDecimal openPositionPnl;
    @Column(precision = 30, scale = 12) private BigDecimal estimatedExitFees;
    @Column(precision = 30, scale = 12) private BigDecimal estimatedNetProfitAfterClose;
    @Column(precision = 30, scale = 12) private BigDecimal actualLockedSessionProfit;
    @Column(precision = 30, scale = 12) private BigDecimal capitalGrowthFactor;
    @Column(precision = 30, scale = 12) private BigDecimal nextSessionCapital;
    @Column(precision = 30, scale = 12) private BigDecimal nextMarginPerPosition;
    @Column(precision = 30, scale = 12) private BigDecimal nextPositionNotional;
    private Instant profitLockTriggeredAt;
    @Version private Long version;
}
