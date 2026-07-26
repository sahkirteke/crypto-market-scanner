package com.crypto.laplace.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolumeScanAuditOutboxRepository extends JpaRepository<VolumeScanAuditOutboxEntity,String> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<VolumeScanAuditOutboxEntity> findTop500ByJsonlWrittenFalseOrderByCreatedAtAsc();
}
