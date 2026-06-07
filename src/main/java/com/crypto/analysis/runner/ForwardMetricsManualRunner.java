package com.crypto.analysis.runner;

import com.crypto.analysis.service.ForwardMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("manual-forward-metrics")
@RequiredArgsConstructor
public class ForwardMetricsManualRunner implements ApplicationRunner {
    private final ForwardMetricsService service;
    @Override public void run(ApplicationArguments args) { service.calculateMissingMetrics(); }
}
