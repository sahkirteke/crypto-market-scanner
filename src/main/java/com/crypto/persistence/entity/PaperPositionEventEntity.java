package com.crypto.persistence.entity;

import com.crypto.paper.model.PaperPositionEventType;
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
@Table(name = "paper_position_events")
public class PaperPositionEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "position_id", nullable = false)
    private PaperPositionEntity position;
    @Column(nullable = false)
    private Instant eventTimeUtc;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64)
    private PaperPositionEventType eventType;
    @Column(precision = 30, scale = 12)
    private BigDecimal price;
    @Column(precision = 30, scale = 12)
    private BigDecimal adjustedPrice;
    @Column(precision = 20, scale = 8)
    private BigDecimal positionPctClosed;
    @Column(precision = 20, scale = 8)
    private BigDecimal rawPnlPct;
    @Column(precision = 20, scale = 8)
    private BigDecimal netPnlPct;
    @Column(precision = 20, scale = 8)
    private BigDecimal leveragedNetPnlPct;
    @Column(precision = 20, scale = 8)
    private BigDecimal feePct;
    @Column(precision = 20, scale = 8)
    private BigDecimal slippagePct;
    private Integer leverage;
    @Column(length = 128)
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String detailsJson;
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void prePersist(){ if(createdAt==null)createdAt=Instant.now(); if(eventTimeUtc==null)eventTimeUtc=createdAt; }
}
