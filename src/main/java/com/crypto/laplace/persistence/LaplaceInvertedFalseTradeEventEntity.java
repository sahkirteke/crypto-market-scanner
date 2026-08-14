package com.crypto.laplace.persistence;
import jakarta.persistence.*;import java.time.Instant;import lombok.*;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="laplace_inverted_false_trade_events")
public class LaplaceInvertedFalseTradeEventEntity {
 @Id @Column(length=36) private String eventId;
 @Column(nullable=false,length=24) private String eventType;
 @Column(length=36) private String reversalId;
 @Column(length=36) private String positionId;
 @Column(nullable=false,length=32) private String symbol;
 @Column(nullable=false,columnDefinition="TEXT") private String payloadJson;
 @Column(nullable=false) private boolean jsonlWritten;
 @Column(nullable=false) private Instant createdAt;
 private Instant writtenAt;
}
