package com.crypto.laplace.pool;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @Entity
@Table(name = "laplace_coin_pool")
public class LaplaceCoinPoolEntity {
 @Id @Column(length=32) private String symbol;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private PoolStatus status;
 @Column(precision=30,scale=8) private BigDecimal quoteVolume;
 private Instant addedAt; private Instant removedAt;
 @Column(length=16) private String lastObservedTrend;
 @Column(nullable=false) private boolean trendLockCompleted;
 @Version private Long version;
}
