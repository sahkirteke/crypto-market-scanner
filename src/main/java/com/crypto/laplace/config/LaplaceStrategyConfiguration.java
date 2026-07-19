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
        return args -> { properties.validatePhaseOne(); var p = properties.getLaplace(); org.slf4j.LoggerFactory.getLogger(LaplaceStrategyConfiguration.class).info("LAPLACE_INVERTED_EXECUTION_CONFIG_READY signalInverted=true initialCapitalUsdt={} marginPerPositionUsdt={} leverage={} notionalUsdt={} stopLossEnabled=false startupFreshSignalOnly=true", p.getInitialCapitalUsdt(), p.getMarginPerPositionUsdt(), p.getLeverage(), p.getNotionalUsdt()); };
    }
}
