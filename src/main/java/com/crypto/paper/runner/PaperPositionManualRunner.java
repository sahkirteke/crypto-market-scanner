package com.crypto.paper.runner;

import com.crypto.paper.service.PaperPositionService;
import com.crypto.persistence.entity.PaperPositionEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("manual-paper-position")
@RequiredArgsConstructor
@Slf4j
public class PaperPositionManualRunner implements CommandLineRunner {
    private final PaperPositionService paperPositionService;

    @Override
    public void run(String... args) {
        log.info("MANUAL_PAPER_POSITION_CHECK started");
        List<PaperPositionEntity> opened = paperPositionService.openPositionsFromLatestSignals();
        log.info("MANUAL_PAPER_POSITION_CHECK opened={}", opened.size());
        opened.forEach(position -> log.info(
                "MANUAL_PAPER_POSITION_CHECK openedPosition id={} symbol={} side={} entryPrice={} quantity={}",
                position.getId(),
                position.getSymbol(),
                position.getSide(),
                position.getEntryPrice(),
                position.getQuantity()
        ));
        List<PaperPositionEntity> openPositions = paperPositionService.getOpenPositions();
        log.info("MANUAL_PAPER_POSITION_CHECK openPositions={}", openPositions.size());
        log.info("MANUAL_PAPER_POSITION_CHECK completed");
    }
}
