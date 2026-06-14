package com.crypto.scanner.log;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.ScanType;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class V20ScoreScanJsonlLogService {
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ISTANBUL_ZONE);
    private static final long FOUR_HOUR_SECONDS = 4 * 60 * 60;

    private final ObjectMapper objectMapper;
    private final Path directory;
    private final Set<String> writtenKeys = ConcurrentHashMap.newKeySet();

    public V20ScoreScanJsonlLogService(ObjectMapper objectMapper) {
        this(objectMapper, Path.of("logs/scanner"));
    }

    V20ScoreScanJsonlLogService(ObjectMapper objectMapper, Path directory) {
        this.objectMapper = objectMapper;
        this.directory = directory;
    }

    public void logFourHourScores(MarketScanResult result) {
        if (result == null || result.getScanType() != ScanType.FOUR_HOUR) {
            return;
        }
        Instant scanTime = result.getScanTimeUtc() == null ? Instant.now() : result.getScanTimeUtc();
        Instant candleCloseTime = latestFourHourClose(scanTime);
        for (CoinScanResult coin : allCoins(result)) {
            if (coin == null || coin.getSymbol() == null || coin.getSymbol().isBlank()) {
                continue;
            }
            String key = duplicateKey(result.getScanRunId(), candleCloseTime, coin.getSymbol());
            if (!writtenKeys.add(key)) {
                continue;
            }
            try {
                write(payload(result, coin, scanTime, candleCloseTime));
            } catch (Exception exception) {
                log.error("V20_4H_SCORE_SCAN_JSONL_WRITE_FAILED scanRunId={} symbol={} message={}",
                        result.getScanRunId(), coin.getSymbol(), exception.getMessage(), exception);
            }
        }
    }

    private Map<String, Object> payload(MarketScanResult result, CoinScanResult coin, Instant scanTime, Instant candleCloseTime) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "V20_4H_SCORE_SCAN");
        payload.put("strategyVersion", "V20");
        payload.put("dateTr", DATE.format(scanTime.atZone(ISTANBUL_ZONE).toLocalDate()));
        payload.put("scanTimeTr", TIME.format(scanTime));
        payload.put("fourHourCandleCloseTimeTr", TIME.format(candleCloseTime));
        payload.put("symbol", coin.getSymbol());
        payload.put("longScore", score(coin.getLongScore()));
        payload.put("shortScore", score(coin.getShortScore()));
        payload.put("classification", coin.getClassification() == null ? null : coin.getClassification().name());
        payload.put("longPassed", coin.getClassification() == CoinClassification.STRONG_LONG);
        payload.put("shortPassed", coin.getClassification() == CoinClassification.STRONG_SHORT);
        payload.put("longReasons", reasonNames(coin));
        payload.put("shortReasons", reasonNames(coin));
        payload.put("scanRunId", result.getScanRunId());
        return payload;
    }

    private void write(Map<String, Object> payload) throws Exception {
        Files.createDirectories(directory);
        Object dateValue = payload.get("scanTimeTr");
        String day = dateValue instanceof String value && value.length() >= 10
                ? value.substring(0, 10).replace("-", "")
                : LocalDate.now(IstanbulTimeUtil.ISTANBUL_ZONE).format(DAY);
        Path file = directory.resolve("v20-score-scan-" + day + ".jsonl");
        Files.writeString(file, objectMapper.writeValueAsString(payload) + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private List<CoinScanResult> allCoins(MarketScanResult result) {
        List<CoinScanResult> coins = new ArrayList<>();
        coins.addAll(safeList(result.getStrongLong()));
        coins.addAll(safeList(result.getStrongShort()));
        coins.addAll(safeList(result.getWatchlist()));
        coins.addAll(safeList(result.getEliminated()));
        return coins;
    }

    private List<CoinScanResult> safeList(List<CoinScanResult> coins) {
        return coins == null ? List.of() : coins;
    }

    private String duplicateKey(Long scanRunId, Instant candleCloseTime, String symbol) {
        return (scanRunId == null ? String.valueOf(candleCloseTime) : String.valueOf(scanRunId)) + ":" + symbol;
    }

    private Instant latestFourHourClose(Instant scanTime) {
        long epochSecond = scanTime.getEpochSecond();
        return Instant.ofEpochSecond((epochSecond / FOUR_HOUR_SECONDS) * FOUR_HOUR_SECONDS);
    }

    private int score(Integer score) {
        return score == null ? 0 : score;
    }

    private List<String> reasonNames(CoinScanResult coin) {
        return coin.getReasons() == null ? List.of() : coin.getReasons().stream()
                .map(reason -> reason == null ? null : reason.name())
                .toList();
    }
}
