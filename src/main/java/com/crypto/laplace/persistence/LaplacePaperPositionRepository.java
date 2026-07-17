package com.crypto.laplace.persistence;
import com.crypto.laplace.model.LaplacePositionStatus;import jakarta.persistence.LockModeType;import java.util.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface LaplacePaperPositionRepository extends JpaRepository<LaplacePaperPositionEntity,String>{
 List<LaplacePaperPositionEntity> findByStrategyAndStatus(String strategy,LaplacePositionStatus status);
 List<LaplacePaperPositionEntity> findByStrategyAndSymbolAndStatus(String strategy,String symbol,LaplacePositionStatus status);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from LaplacePaperPositionEntity p where p.strategy=:strategy and p.symbol=:symbol and p.status=:status")
 List<LaplacePaperPositionEntity> findOpenForUpdate(@Param("strategy")String strategy,@Param("symbol")String symbol,@Param("status")LaplacePositionStatus status);
}
