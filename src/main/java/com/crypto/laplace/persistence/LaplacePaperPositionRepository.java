package com.crypto.laplace.persistence;

import com.crypto.laplace.model.LaplacePositionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaplacePaperPositionRepository extends JpaRepository<LaplacePaperPositionEntity, String> {
    List<LaplacePaperPositionEntity> findByStrategyAndStatus(String strategy, LaplacePositionStatus status);
    List<LaplacePaperPositionEntity> findByStrategyAndTradingRunIdAndStatus(
            String strategy, String tradingRunId, LaplacePositionStatus status);
    List<LaplacePaperPositionEntity> findByStrategyAndTradingRunIdAndSessionIdAndStatus(
            String strategy, String tradingRunId, String sessionId, LaplacePositionStatus status);
    List<LaplacePaperPositionEntity> findByStrategyAndSymbolAndStatus(
            String strategy, String symbol, LaplacePositionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LaplacePaperPositionEntity p where p.id=:id")
    Optional<LaplacePaperPositionEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LaplacePaperPositionEntity p where p.strategy=:strategy and p.symbol=:symbol and p.status=:status")
    List<LaplacePaperPositionEntity> findOpenForUpdate(
            @Param("strategy") String strategy,
            @Param("symbol") String symbol,
            @Param("status") LaplacePositionStatus status);
}
