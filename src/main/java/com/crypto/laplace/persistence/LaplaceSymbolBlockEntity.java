package com.crypto.laplace.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @Entity
@Table(name = "laplace_symbol_blocks")
public class LaplaceSymbolBlockEntity {
 @Id @Column(length=36) private String id;
 @Column(nullable=false,length=32) private String symbol;
 @Column(nullable=false,length=32) private String reason;
 @Column(nullable=false) private Instant blockedAt;
 @Column(nullable=false) private Instant blockedUntil;
 @Column(nullable=false,length=36) private String sourcePositionId;
 @Column(nullable=false) private Instant createdAt;
}
