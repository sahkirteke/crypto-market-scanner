package com.crypto.laplace.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LaplaceTradeReconciliationServiceTest {
 @Test void startupPublishesMissingExitOnlyOnce(){LaplacePaperPositionRepository positions=mock(LaplacePaperPositionRepository.class);LaplaceTradeEventRepository events=mock(LaplaceTradeEventRepository.class);LaplaceTradeJsonlWriter writer=mock(LaplaceTradeJsonlWriter.class);LaplacePaperPositionEntity closed=LaplacePaperPositionEntity.builder().id("p1").strategy(LaplacePaperExecutionService.STRATEGY).strategyVersion("1.0").symbol("BTCUSDT").side(PositionSide.LONG).status(LaplacePositionStatus.CLOSED_BY_STOP_LOSS).entryExecutionPrice(new BigDecimal("100")).exitExecutionPrice(new BigDecimal("94")).quantity(new BigDecimal("0.5")).notional(new BigDecimal("50")).entryFee(new BigDecimal("0.02")).exitFee(new BigDecimal("0.0188")).grossPnl(new BigDecimal("-3")).netPnl(new BigDecimal("-3.0388")).exitReason("STOP_LOSS_5M").exitTime(Instant.now()).build();when(positions.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of());when(positions.findAll()).thenReturn(List.of(closed));when(events.existsByPositionIdAndEventType("p1","EXIT")).thenReturn(false,true);when(writer.tryJson(any())).thenReturn(Optional.of("{}"));LaplaceTradeReconciliationService service=new LaplaceTradeReconciliationService(positions,events,writer);service.run(null);service.run(null);verify(events,times(1)).save(any());verify(writer,times(2)).drain();}
}
