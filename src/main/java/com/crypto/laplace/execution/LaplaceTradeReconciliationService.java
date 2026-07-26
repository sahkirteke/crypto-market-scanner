package com.crypto.laplace.execution;

import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class LaplaceTradeReconciliationService implements ApplicationRunner {
    private final LaplacePaperPositionRepository positions;
    private final LaplaceTradeEventRepository events;
    private final LaplaceTradeJsonlWriter writer;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Long> openBySymbol = new HashMap<>();
        positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY, LaplacePositionStatus.OPEN)
                .forEach(p -> openBySymbol.merge(p.getSymbol(), 1L, Long::sum));
        openBySymbol.forEach((symbol, count) -> { if (count > 1) log.error("LAPLACE_RECONCILIATION_CRITICAL_DUPLICATE_OPEN symbol={} count={}", symbol, count); });
        int repaired = 0;
        for (LaplacePaperPositionEntity p : positions.findAll()) {
            if (!LaplacePaperExecutionService.STRATEGY.equals(p.getStrategy()) || p.getStatus() == LaplacePositionStatus.OPEN
                    || events.existsByPositionIdAndEventType(p.getId(), "EXIT")) continue;
            String id = UUID.randomUUID().toString();
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("eventType", "EXIT"); payload.put("eventId", id); payload.put("reversalId", null);
            payload.put("strategy", p.getStrategy()); payload.put("strategyVersion", p.getStrategyVersion());
            payload.put("positionId", p.getId()); payload.put("symbol", p.getSymbol()); payload.put("closedSide", p.getSide());
            payload.put("positionBefore", p.getSide()); payload.put("positionAfter", "FLAT"); payload.put("exitReason", p.getExitReason());
            payload.put("status", p.getStatus()); payload.put("entryExecutionPrice", p.getEntryExecutionPrice());
            payload.put("exitExecutionPrice", p.getExitExecutionPrice()); payload.put("quantity", p.getQuantity());
            payload.put("entryNotional", p.getNotional()); payload.put("grossPnl", p.getGrossPnl()); payload.put("entryFee", p.getEntryFee());
            payload.put("exitFee", p.getExitFee()); payload.put("netPnl", p.getNetPnl()); payload.put("exitTime", p.getExitTime());
            payload.put("reconciled", true); payload.put("reconciledAt", Instant.now());
            writer.tryJson(payload).ifPresent(json -> events.save(LaplaceTradeEventEntity.builder().eventId(id).eventType("EXIT")
                    .positionId(p.getId()).symbol(p.getSymbol()).payloadJson(json).jsonlWritten(false).createdAt(Instant.now()).build()));
            repaired++;
        }
        log.info("LAPLACE_RECONCILIATION_COMPLETED repairedMissingExitEvents={} openPositions={}", repaired, openBySymbol.values().stream().mapToLong(Long::longValue).sum());
        writer.drain();
    }
}
