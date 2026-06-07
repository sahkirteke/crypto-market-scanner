package com.crypto.paper.runner;

import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.paper.service.ExitEngineService;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("manual-exit-engine")
@RequiredArgsConstructor
@Slf4j
public class ExitEngineManualRunner implements CommandLineRunner {
    private final ExitEngineService exitEngineService;
    private final PaperPositionRepository paperPositionRepository;

    @Override
    public void run(String... args) {
        log.info("MANUAL_EXIT_ENGINE_CHECK started");
        List<PaperPositionEntity> closedPositions = exitEngineService.evaluateOpenPositions();
        log.info("MANUAL_EXIT_ENGINE_CHECK closed={}", closedPositions.size());
        closedPositions.forEach(position -> log.info(
                "MANUAL_EXIT_ENGINE_CHECK closedPosition id={} symbol={} side={} exitReason={} pnlPct={} pnlUsdt={}",
                position.getId(),
                position.getSymbol(),
                position.getSide(),
                position.getExitReason(),
                position.getRealizedPnlPct(),
                position.getRealizedPnlUsdt()
        ));
        long openCount = paperPositionRepository.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN).size();
        log.info("MANUAL_EXIT_ENGINE_CHECK openRemaining={}", openCount);
        log.info("MANUAL_EXIT_ENGINE_CHECK completed");
    }
}
