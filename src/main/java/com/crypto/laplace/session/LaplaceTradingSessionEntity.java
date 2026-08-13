package com.crypto.laplace.session;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @Entity
@Table(name = "laplace_trading_sessions")
public class LaplaceTradingSessionEntity {
    @Id @Column(length=32) private String sessionId;
    @Column(nullable=false) private Instant startTime;
    @Column(nullable=false) private Instant entryCutoffTime;
    @Column(nullable=false) private Instant endTime;
    @Column(nullable=false,precision=30,scale=12) private BigDecimal startingCapital;
    @Column(nullable=false,precision=30,scale=12) private BigDecimal startingMargin;
    @Column(nullable=false,precision=30,scale=12) private BigDecimal realizedPnl;
    @Column(precision=30,scale=12) private BigDecimal finalCapital;
    @Column(precision=20,scale=12) private BigDecimal finalReturnPct;
    @Column(precision=30,scale=12) private BigDecimal nextSessionMargin;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private LaplaceSessionStatus status;
    @Column(nullable=false) private boolean entryLocked;
    @Column(nullable=false) private boolean profitTargetLocked;
    private Instant profitTargetLockedAt;
    private Instant completedAt;
    @Version private long version;
}
