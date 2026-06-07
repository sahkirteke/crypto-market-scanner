package com.crypto.persistence.repository;

import com.crypto.persistence.entity.EntryCandidateEntity;
import com.crypto.scanner.model.EntryCandidateStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryCandidateRepository extends JpaRepository<EntryCandidateEntity, Long> {
    List<EntryCandidateEntity> findByStatusOrderByScoreDesc(EntryCandidateStatus status);
    List<EntryCandidateEntity> findByScanRun_Id(Long scanRunId);
    Optional<EntryCandidateEntity> findFirstBySymbolAndStatusOrderByCreatedAtDesc(String symbol, EntryCandidateStatus status);
}
