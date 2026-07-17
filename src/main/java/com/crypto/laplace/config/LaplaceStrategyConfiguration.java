package com.crypto.laplace.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LaplaceStrategyProperties.class)
public class LaplaceStrategyConfiguration {
    @Bean ApplicationRunner laplaceConfigurationGuard(LaplaceStrategyProperties properties) {
        return args -> properties.validatePhaseOne();
    }
}
