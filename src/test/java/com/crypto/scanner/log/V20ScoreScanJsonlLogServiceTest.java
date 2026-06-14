package com.crypto.scanner.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crypto.common.enums.CoinClassification;
import com.crypto.common.enums.ReasonTag;
import com.crypto.common.enums.ScanType;
import com.crypto.domain.model.CoinScanResult;
import com.crypto.domain.model.MarketScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class V20ScoreScanJsonlLogServiceTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fourHourScanWritesV20ScoreJsonl() throws Exception {
        V20ScoreScanJsonlLogService service = new V20ScoreScanJsonlLogService(objectMapper, tempDir);

        service.logFourHourScores(scan(ScanType.FOUR_HOUR, List.of(
                coin("SOLUSDT", CoinClassification.STRONG_LONG, 9, 4),
                coin("ETHUSDT", CoinClassification.WATCHLIST, 6, 5)
        )));

        List<String> lines = Files.readAllLines(tempDir.resolve("v20-score-scan-20260614.jsonl"));
        assertThat(lines).hasSize(2);
        Map<String, Object> first = read(lines.get(0));
        assertThat(first).containsEntry("eventType", "V20_4H_SCORE_SCAN");
        assertThat(first).containsEntry("strategyVersion", "V20");
        assertThat(first).containsEntry("dateTr", "2026-06-14");
        assertThat(first).containsEntry("symbol", "SOLUSDT");
        assertThat(first).containsEntry("longScore", 9);
        assertThat(first).containsEntry("shortScore", 4);
    }

    @Test
    void oneHourScanDoesNotWriteV20ScoreJsonl() throws Exception {
        V20ScoreScanJsonlLogService service = new V20ScoreScanJsonlLogService(objectMapper, tempDir);

        service.logFourHourScores(scan(ScanType.ONE_HOUR, List.of(coin("BTCUSDT", CoinClassification.STRONG_LONG, 9, 3))));

        assertThat(Files.exists(tempDir.resolve("v20-score-scan-20260614.jsonl"))).isFalse();
    }

    @Test
    void scoreJsonlUsesEuropeIstanbulTime() throws Exception {
        V20ScoreScanJsonlLogService service = new V20ScoreScanJsonlLogService(objectMapper, tempDir);

        service.logFourHourScores(scan(ScanType.FOUR_HOUR, List.of(coin("BTCUSDT", CoinClassification.STRONG_LONG, 9, 3))));

        Map<String, Object> row = read(Files.readAllLines(tempDir.resolve("v20-score-scan-20260614.jsonl")).get(0));
        assertThat(row.get("scanTimeTr")).isEqualTo("2026-06-14T15:01:22.125+03:00");
        assertThat(row.get("fourHourCandleCloseTimeTr")).isEqualTo("2026-06-14T15:00:00.000+03:00");
        assertThat(row.get("dateTr")).isEqualTo("2026-06-14");
    }

    @Test
    void scoreJsonlDoesNotBlockScanWhenWriteFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("disk full"));
        V20ScoreScanJsonlLogService service = new V20ScoreScanJsonlLogService(failingMapper, tempDir);

        service.logFourHourScores(scan(ScanType.FOUR_HOUR, List.of(coin("BTCUSDT", CoinClassification.STRONG_LONG, 9, 3))));

        try (var files = Files.list(tempDir)) {
            assertThat(files.findAny()).isEmpty();
        }
    }

    @Test
    void scoreJsonlWritesAllScannedCoinsNotOnlyStrong() throws Exception {
        V20ScoreScanJsonlLogService service = new V20ScoreScanJsonlLogService(objectMapper, tempDir);

        service.logFourHourScores(scan(ScanType.FOUR_HOUR, List.of(
                coin("BTCUSDT", CoinClassification.STRONG_LONG, 9, 3),
                coin("ETHUSDT", CoinClassification.WATCHLIST, 6, 5),
                coin("XRPUSDT", CoinClassification.ELIMINATED, null, null)
        )));

        List<String> lines = Files.readAllLines(tempDir.resolve("v20-score-scan-20260614.jsonl"));
        assertThat(lines).hasSize(3);
        assertThat(lines).anySatisfy(line -> assertThat(read(line)).containsEntry("symbol", "XRPUSDT")
                .containsEntry("longScore", 0)
                .containsEntry("shortScore", 0));
    }

    @Test
    void duplicateScanRunAndSymbolAreWrittenOnce() throws Exception {
        V20ScoreScanJsonlLogService service = new V20ScoreScanJsonlLogService(objectMapper, tempDir);
        MarketScanResult scan = scan(ScanType.FOUR_HOUR, List.of(coin("BTCUSDT", CoinClassification.STRONG_LONG, 9, 3)));

        service.logFourHourScores(scan);
        service.logFourHourScores(scan);

        assertThat(Files.readAllLines(tempDir.resolve("v20-score-scan-20260614.jsonl"))).hasSize(1);
    }

    private MarketScanResult scan(ScanType scanType, List<CoinScanResult> coins) {
        return MarketScanResult.builder()
                .scanRunId(123L)
                .scanType(scanType)
                .scanTimeUtc(Instant.parse("2026-06-14T12:01:22.125Z"))
                .strongLong(coins.stream().filter(coin -> coin.getClassification() == CoinClassification.STRONG_LONG).toList())
                .strongShort(coins.stream().filter(coin -> coin.getClassification() == CoinClassification.STRONG_SHORT).toList())
                .watchlist(coins.stream().filter(coin -> coin.getClassification() == CoinClassification.WATCHLIST).toList())
                .eliminated(coins.stream().filter(coin -> coin.getClassification() == CoinClassification.ELIMINATED).toList())
                .build();
    }

    private CoinScanResult coin(String symbol, CoinClassification classification, Integer longScore, Integer shortScore) {
        return CoinScanResult.builder()
                .symbol(symbol)
                .classification(classification)
                .longScore(longScore)
                .shortScore(shortScore)
                .reasons(List.of(ReasonTag.DATA_NOT_READY))
                .build();
    }

    private Map<String, Object> read(String line) {
        try {
            return objectMapper.readValue(line, Map.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
