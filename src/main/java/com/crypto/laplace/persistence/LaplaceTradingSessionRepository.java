package com.crypto.laplace.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LaplaceTradingSessionRepository extends JpaRepository<LaplaceTradingSessionEntity, String> {
    Optional<LaplaceTradingSessionEntity> findTopByOrderBySessionStartTimeDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select * from laplace_trading_sessions order by session_start_time desc limit 1 for update", nativeQuery = true)
    Optional<LaplaceTradingSessionEntity> findCurrentForUpdate();
}
