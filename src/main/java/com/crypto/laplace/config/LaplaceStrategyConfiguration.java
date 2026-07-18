package com.crypto.laplace.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@EnableConfigurationProperties(LaplaceStrategyProperties.class)
public class LaplaceStrategyConfiguration {
    @Bean @Order(0) ApplicationRunner laplaceConfigurationGuard(LaplaceStrategyProperties properties) {
        return args -> properties.validatePhaseOne();
    }
}
