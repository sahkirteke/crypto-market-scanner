package com.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.laplace.api.LaplaceVariantController;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionRepository;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import com.crypto.laplace.scheduler.LaplaceStopLossFanOutScheduler;
import com.crypto.laplace.scheduler.LaplaceThirtyMinuteScheduler;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import com.crypto.laplace.service.LaplaceRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "scanner.scheduler.enabled=false",
        "scanner.paper-auto.enabled=false"
})
class CryptoMarketScannerApplicationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean BinanceFuturesClient binance;
    @Autowired LaplaceRuntimeService trueRuntime;
    @Autowired LaplaceInvertedFalseRuntimeService falseRuntime;
    @Autowired LaplacePaperPositionRepository truePositions;
    @Autowired LaplaceInvertedFalsePositionRepository falsePositions;
    @Autowired LaplaceThirtyMinuteScheduler thirtyMinuteScheduler;
    @Autowired LaplaceStopLossFanOutScheduler stopLossScheduler;
    @Autowired LaplaceVariantController controller;

    @Test
    void migrationsValidateAndFullDualVariantContextStarts() {
        assertThat(trueRuntime.current()).isNotNull();
        assertThat(falseRuntime.current()).isNotNull();
        assertThat(truePositions).isNotNull();
        assertThat(falsePositions).isNotNull();
        assertThat(thirtyMinuteScheduler).isNotNull();
        assertThat(stopLossScheduler).isNotNull();
        assertThat(controller).isNotNull();
    }
}
