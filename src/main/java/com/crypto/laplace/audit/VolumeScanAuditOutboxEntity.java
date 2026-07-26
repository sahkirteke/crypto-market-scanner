package com.crypto.laplace.audit;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="laplace_volume_scan_audit_outbox")
public class VolumeScanAuditOutboxEntity {
    @Id @Column(length=36) private String eventId;
    @Column(nullable=false,length=220,unique=true) private String idempotencyKey;
    @Column(nullable=false,length=100) private String scanRunId;
    @Column(nullable=false,length=32) private String eventType;
    @Column(length=32) private String symbol;
    @Column(nullable=false,columnDefinition="TEXT") private String payloadJson;
    @Column(nullable=false) private boolean jsonlWritten;
    @Column(nullable=false) private Instant createdAt;
    private Instant writtenAt;
}
