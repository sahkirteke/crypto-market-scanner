package com.crypto.laplace.persistence;
import java.util.List;import org.springframework.data.jpa.repository.JpaRepository;
public interface LaplaceTradeEventRepository extends JpaRepository<LaplaceTradeEventEntity,String>{List<LaplaceTradeEventEntity> findTop100ByJsonlWrittenFalseOrderByCreatedAtAsc();List<LaplaceTradeEventEntity> findTop100ByEventTypeOrderByCreatedAtDesc(String eventType);long countByEventType(String eventType);boolean existsByPositionIdAndEventType(String positionId,String eventType);}
