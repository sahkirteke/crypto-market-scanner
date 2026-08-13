package com.crypto.laplace.session;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LaplaceTradingSessionRepository extends JpaRepository<LaplaceTradingSessionEntity,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LaplaceTradingSessionEntity s where s.sessionId=:id")
    Optional<LaplaceTradingSessionEntity> findByIdForUpdate(@Param("id") String id);
    Optional<LaplaceTradingSessionEntity> findFirstByStatusOrderByStartTimeDesc(LaplaceSessionStatus status);
}
