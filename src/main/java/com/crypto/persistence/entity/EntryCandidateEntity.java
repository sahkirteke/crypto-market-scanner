package com.crypto.persistence.entity;

import com.crypto.common.enums.MarketRegime;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.enums.RiskLevel;
import com.crypto.scanner.model.EntryCandidateStatus;
import com.crypto.common.time.IstanbulTimeUtil;
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
@Table(name = "entry_candidates")
public class EntryCandidateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "scan_run_id", nullable = false)
    private MarketScanRunEntity scanRun;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "coin_scan_result_id")
    private CoinScanResultEntity coinScanResult;
    @Column(nullable = false, length = 32)
    private String symbol;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PositionSide side;
    private Integer score;
    private Integer entryPriorityScore;
    @Enumerated(EnumType.STRING) @Column(length = 32)
    private RiskLevel riskLevel;
    @Enumerated(EnumType.STRING) @Column(length = 32)
    private MarketRegime marketRegime;
    @Column(precision = 20, scale = 8)
    private BigDecimal marketBreadthPct;
    @Column(nullable = false)
    private Instant validFromUtc;
    @Column(name = "valid_from_text", length = 64)
    private String validFromText;
    @Column(nullable = false)
    private Instant validUntilUtc;
    @Column(name = "valid_until_text", length = 64)
    private String validUntilText;
    @Column(columnDefinition = "TEXT")
    private String reasonsJson;
    @Column(columnDefinition = "TEXT")
    private String warningsJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private EntryCandidateStatus status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(name = "created_at_text", length = 64)
    private String createdAtText;
    @Column(nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_at_text", length = 64)
    private String updatedAtText;
    @PrePersist void prePersist(){ Instant now=Instant.now(); if(createdAt==null)createdAt=now; if(updatedAt==null)updatedAt=now; syncTextFields(); }
    @PreUpdate void preUpdate(){ updatedAt=Instant.now(); syncTextFields(); }
    private void syncTextFields(){ validFromText=IstanbulTimeUtil.format(validFromUtc); validUntilText=IstanbulTimeUtil.format(validUntilUtc); createdAtText=IstanbulTimeUtil.format(createdAt); updatedAtText=IstanbulTimeUtil.format(updatedAt); }
}
