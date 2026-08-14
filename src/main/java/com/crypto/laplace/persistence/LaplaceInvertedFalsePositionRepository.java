package com.crypto.laplace.persistence;

import com.crypto.laplace.model.LaplacePositionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaplaceInvertedFalsePositionRepository extends JpaRepository<LaplaceInvertedFalsePositionEntity, String> {
    List<LaplaceInvertedFalsePositionEntity> findByStrategyAndStatus(String strategy, LaplacePositionStatus status);
    List<LaplaceInvertedFalsePositionEntity> findBySessionIdAndStatus(String sessionId, LaplacePositionStatus status);
    List<LaplaceInvertedFalsePositionEntity> findByStrategyAndSymbolAndStatus(String strategy, String symbol, LaplacePositionStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LaplaceInvertedFalsePositionEntity p where p.id=:id")
    Optional<LaplaceInvertedFalsePositionEntity> findByIdForUpdate(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LaplaceInvertedFalsePositionEntity p where p.strategy=:strategy and p.symbol=:symbol and p.status=:status")
    List<LaplaceInvertedFalsePositionEntity> findOpenForUpdate(@Param("strategy") String strategy, @Param("symbol") String symbol, @Param("status") LaplacePositionStatus status);
}
