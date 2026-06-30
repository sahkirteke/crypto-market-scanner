package com.crypto.analysis.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.domain.model.Kline;
import com.crypto.persistence.entity.CoinScanForwardMetricsEntity;
import com.crypto.persistence.entity.CoinScanResultEntity;
import com.crypto.persistence.repository.CoinScanForwardMetricsRepository;
import com.crypto.persistence.repository.CoinScanResultRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForwardMetricsService {
    private final CoinScanResultRepository coinScanResultRepository;
    private final CoinScanForwardMetricsRepository forwardMetricsRepository;
    private final BinanceFuturesClient binanceFuturesClient;

    public List<CoinScanForwardMetricsEntity> calculateMissingMetrics() {
        log.info("FORWARD_METRICS_DB_PERSIST_SKIPPED reason=PAPER_POSITIONS_ONLY");
        return List.of();
    }

    private CoinScanForwardMetricsEntity calculate(CoinScanResultEntity result) {
        try {
            BigDecimal entry = result.getLastPrice();
            if (entry == null || entry.compareTo(BigDecimal.ZERO) <= 0 || result.getScanRun() == null) return null;
            List<Kline> klines = binanceFuturesClient.getKlines(result.getSymbol(), "1h", 48).stream()
                    .filter(k -> Boolean.TRUE.equals(k.getClosed()))
                    .sorted(Comparator.comparing(Kline::getCloseTime))
                    .toList();
            if (klines.size() < 24) return null;
            BigDecimal c1 = closeAt(klines, 1), c4 = closeAt(klines, 4), c8 = closeAt(klines, 8), c24 = closeAt(klines, 24);
            BigDecimal maxHigh = klines.stream().limit(24).map(Kline::getHigh).max(BigDecimal::compareTo).orElse(null);
            BigDecimal minLow = klines.stream().limit(24).map(Kline::getLow).min(BigDecimal::compareTo).orElse(null);
            return CoinScanForwardMetricsEntity.builder()
                    .coinScanResult(result).scanRun(result.getScanRun()).symbol(result.getSymbol())
                    .forwardReturn1hPct(pct(c1.subtract(entry), entry))
                    .forwardReturn4hPct(pct(c4.subtract(entry), entry))
                    .forwardReturn8hPct(pct(c8.subtract(entry), entry))
                    .forwardReturn24hPct(pct(c24.subtract(entry), entry))
                    .maxForwardGainPct(maxHigh == null ? null : pct(maxHigh.subtract(entry), entry))
                    .maxForwardDrawdownPct(minLow == null ? null : pct(minLow.subtract(entry), entry))
                    .calculatedAt(Instant.now()).build();
        } catch (Exception e) {
            log.warn("FORWARD_METRICS_SKIPPED symbol={} reason={}", result.getSymbol(), e.getMessage());
            return null;
        }
    }
    private BigDecimal closeAt(List<Kline> klines, int hour) { return klines.get(hour - 1).getClose(); }
    private BigDecimal pct(BigDecimal n, BigDecimal d) { return n.divide(d, 12, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(8, RoundingMode.HALF_UP); }
}
