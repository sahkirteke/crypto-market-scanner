package com.crypto.laplace.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.model.LaplacePositionStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LaplacePaperPositionRepositoryPersistenceTest {

    @Autowired
    private LaplacePaperPositionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsStopLossStatusAndExitReason() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");
        LaplacePaperPositionEntity position = LaplacePaperPositionEntity.builder()
                .id("stop-loss-persistence-test")
                .strategy("LAPLACE_KERNEL_REGRESSION_30M")
                .strategyVersion("1.0")
                .symbol("BTCUSDT")
                .side(PositionSide.LONG)
                .status(LaplacePositionStatus.CLOSED_BY_STOP_LOSS)
                .entrySignalId("stop-loss-persistence-entry")
                .entryRawSignal("SHORT")
                .signalInverted(true)
                .entryCandleCloseTime(now.minusSeconds(1800))
                .entryTime(now.minusSeconds(1200))
                .entrySignalClosePrice(new BigDecimal("100.00"))
                .entryExecutionPrice(new BigDecimal("100.00"))
                .margin(new BigDecimal("5.00"))
                .quantity(new BigDecimal("0.500000000000"))
                .notional(new BigDecimal("50.00"))
                .leverage(10)
                .entryFeeRate(new BigDecimal("0.0004"))
                .entryFee(new BigDecimal("0.020000000000"))
                .exitTime(now)
                .exitExecutionPrice(new BigDecimal("94.50"))
                .exitReason("STOP_LOSS_5M")
                .build();

        repository.saveAndFlush(position);
        entityManager.clear();

        LaplacePaperPositionEntity reloaded = repository.findById(position.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LaplacePositionStatus.CLOSED_BY_STOP_LOSS);
        assertThat(reloaded.getExitReason()).isEqualTo("STOP_LOSS_5M");
    }
}
