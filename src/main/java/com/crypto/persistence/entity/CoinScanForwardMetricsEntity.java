package com.crypto.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "coin_scan_forward_metrics")
public class CoinScanForwardMetricsEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "coin_scan_result_id", nullable = false)
    private CoinScanResultEntity coinScanResult;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "scan_run_id", nullable = false)
    private MarketScanRunEntity scanRun;
    @Column(nullable = false, length = 32)
    private String symbol;
    @Column(precision = 20, scale = 8) private BigDecimal forwardReturn1hPct;
    @Column(precision = 20, scale = 8) private BigDecimal forwardReturn4hPct;
    @Column(precision = 20, scale = 8) private BigDecimal forwardReturn8hPct;
    @Column(precision = 20, scale = 8) private BigDecimal forwardReturn24hPct;
    @Column(precision = 20, scale = 8) private BigDecimal maxForwardGainPct;
    @Column(precision = 20, scale = 8) private BigDecimal maxForwardDrawdownPct;
    @Column(nullable = false) private Instant calculatedAt;
    @Column(nullable = false) private Instant createdAt;
    @PrePersist void prePersist(){ if(createdAt==null)createdAt=Instant.now(); }
}
