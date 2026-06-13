package com.crypto.paper.service;

import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaperNotionalConfigValidator implements ApplicationRunner {
    private final ScannerProperties scannerProperties;

    @Override
    public void run(ApplicationArguments args) {
        ScannerProperties.Paper paper = scannerProperties.getPaper();
        if (paper == null) return;
        BigDecimal margin = paper.getMarginUsdt() == null ? BigDecimal.valueOf(100) : paper.getMarginUsdt();
        int leverage = paper.getLeverage() == null ? 5 : paper.getLeverage();
        BigDecimal expected = margin.multiply(BigDecimal.valueOf(leverage));
        BigDecimal configured = paper.getPositionNotionalUsdt();
        if (configured != null && configured.compareTo(expected) != 0) {
            log.warn("PAPER_NOTIONAL_MISMATCH margin={} leverage={} expectedNotional={} configuredNotional={}",
                    margin, leverage, expected, configured);
        }
    }
}
