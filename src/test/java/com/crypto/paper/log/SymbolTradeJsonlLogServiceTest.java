package com.crypto.paper.log;

import static org.assertj.core.api.Assertions.assertThat;

import com.crypto.common.enums.EntryAction;
import com.crypto.common.enums.PositionSide;
import com.crypto.paper.log.SymbolTradeJsonlLogService.PaperExitContext;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SymbolTradeJsonlLogServiceTest {
    private static final Instant ENTRY_TIME = Instant.parse("2026-06-08T11:44:59Z");
    private static final Instant EXIT_TIME = Instant.parse("2026-06-08T12:00:59Z");

    @TempDir
    Path tempDir;

    @Test
    void logsEntryAndExitToSymbolJsonlFile() throws Exception {
        SymbolTradeJsonlLogService service = service();
        PaperPositionEntity position = shortPosition();

        assertThat(service.logEntry(position)).isTrue();
        position.setStatus(PaperPositionStatus.CLOSED);
        position.setClosedAt(EXIT_TIME);
        position.setExitPrice(new BigDecimal("107.83"));
        position.setExitReason("STOP_LOSS");
        position.setExitDetail("SL touched by 5m candle high");
        assertThat(service.logExit(position, PaperExitContext.builder()
                .exitPrice(new BigDecimal("107.83"))
                .firstHit("SL_FIRST")
                .exitTrigger("SL_5M")
                .interval("5m")
                .candleOpenTime(Instant.parse("2026-06-08T11:55:00Z"))
                .candleCloseTime(Instant.parse("2026-06-08T11:59:59Z"))
                .candleHigh(new BigDecimal("107.90"))
                .candleLow(new BigDecimal("107.30"))
                .candleClose(new BigDecimal("107.80"))
                .build())).isTrue();

        List<Map<String, Object>> lines = readLines("AAVEUSDT");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0))
                .containsEntry("type", "ENTRY")
                .containsEntry("positionId", 12)
                .containsEntry("symbol", "AAVEUSDT")
                .containsEntry("time", "2026-06-08 14:44:59 TRT")
                .containsEntry("side", "SHORT");
        assertThat(lines.get(0)).containsKeys("entryPrice", "tp1", "tp2", "slPrice");
        assertThat(lines.get(1))
                .containsEntry("type", "EXIT")
                .containsEntry("positionId", 12)
                .containsEntry("symbol", "AAVEUSDT")
                .containsEntry("time", "2026-06-08 15:00:59 TRT")
                .containsEntry("side", "SHORT")
                .containsEntry("exitReason", "STOP_LOSS")
                .containsEntry("firstHit", "SL_FIRST")
                .containsEntry("exitTrigger", "SL_5M");
        assertThat(lines.get(1)).containsKeys("entryPrice", "exitPrice", "realizedPnl");
        assertThat(new BigDecimal(lines.get(1).get("realizedPnl").toString())).isEqualByComparingTo("-0.172");
        assertThat(lines).extracting(line -> line.get("type")).containsExactly("ENTRY", "EXIT");
    }


    @Test
    void logsMultipleExitRowsForPartialLifecycle() throws Exception {
        SymbolTradeJsonlLogService service = service();
        PaperPositionEntity position = shortPosition();

        assertThat(service.logEntry(position)).isTrue();
        position.setSymbolTradeEntryLogged(true);
        position.setTp1Hit(true);
        position.setTrailingActive(true);
        position.setRemainingPositionPct(new BigDecimal("50"));
        position.setRealizedPnlUsdt(new BigDecimal("0.45"));
        position.setRealizedPnlPct(new BigDecimal("0.45"));
        assertThat(service.logExit(position, PaperExitContext.builder()
                .exitPrice(new BigDecimal("106.43"))
                .exitPriceAdjusted(new BigDecimal("106.483215"))
                .exitTime(EXIT_TIME)
                .exitSeq(1)
                .exitReason("PARTIAL_TP1")
                .firstHit("TP_FIRST")
                .exitTrigger("TP1_5M")
                .interval("5m")
                .closedPositionPct(new BigDecimal("50"))
                .remainingPositionPctBefore(new BigDecimal("100"))
                .remainingPositionPctAfter(new BigDecimal("50"))
                .realizedPnlUsdt(new BigDecimal("0.45"))
                .tp1HitBefore(false)
                .tp1HitAfter(true)
                .tp2HitBefore(false)
                .tp2HitAfter(false)
                .trailingActiveBefore(false)
                .trailingActiveAfter(true)
                .build())).isTrue();
        position.setSymbolTradeTp1ExitLogged(true);
        position.setStatus(PaperPositionStatus.CLOSED);
        position.setClosedAt(EXIT_TIME.plusSeconds(300));
        position.setExitPrice(new BigDecimal("105.90"));
        position.setExitReason("TRAILING_STOP");
        position.setRemainingPositionPct(BigDecimal.ZERO);
        position.setRealizedPnlUsdt(new BigDecimal("0.70"));
        assertThat(service.logExit(position, PaperExitContext.builder()
                .exitPrice(new BigDecimal("105.90"))
                .exitTime(EXIT_TIME.plusSeconds(300))
                .exitSeq(2)
                .exitReason("TRAILING_STOP")
                .firstHit("TRAILING_FIRST")
                .exitTrigger("TRAILING_5M")
                .interval("5m")
                .closedPositionPct(new BigDecimal("50"))
                .remainingPositionPctBefore(new BigDecimal("50"))
                .remainingPositionPctAfter(BigDecimal.ZERO)
                .realizedPnlUsdt(new BigDecimal("0.25"))
                .build())).isTrue();

        List<Map<String, Object>> lines = readLines("AAVEUSDT");
        assertThat(lines).hasSize(3);
        assertThat(lines).extracting(line -> line.get("type")).containsExactly("ENTRY", "EXIT", "EXIT");
        assertThat(lines).extracting(line -> line.get("exitReason")).containsExactly(null, "PARTIAL_TP1", "TRAILING_STOP");
        assertThat(lines.get(1)).containsEntry("closedPositionPct", 50).containsEntry("remainingPositionPctAfter", 50);
        assertThat(lines.get(2)).containsEntry("exitSeq", 2).containsEntry("remainingPositionPctAfter", 0);
    }

    @Test
    void skipsDuplicateEntryAndExitWhenFlagsAreAlreadySet() {
        SymbolTradeJsonlLogService service = service();
        PaperPositionEntity position = shortPosition();
        position.setSymbolTradeEntryLogged(true);
        position.setSymbolTradeExitLogged(true);

        assertThat(service.logEntry(position)).isFalse();
        assertThat(service.logExit(position)).isFalse();

        assertThat(Files.exists(tempDir.resolve("AAVEUSDT.jsonl"))).isFalse();
    }


    @Test
    void skipsDuplicatePartialExitByPartialFlagOnly() throws Exception {
        SymbolTradeJsonlLogService service = service();
        PaperPositionEntity position = shortPosition();
        position.setSymbolTradeTp1ExitLogged(true);

        assertThat(service.logExit(position, PaperExitContext.builder()
                .exitReason("PARTIAL_TP1")
                .exitSeq(1)
                .build())).isFalse();
        assertThat(service.logExit(position, PaperExitContext.builder()
                .exitReason("PARTIAL_TP2")
                .exitSeq(2)
                .build())).isTrue();

        List<Map<String, Object>> lines = readLines("AAVEUSDT");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).containsEntry("type", "EXIT").containsEntry("exitReason", "PARTIAL_TP2");
    }

    @Test
    void calculatesLongRealizedPnlWhenStoredPnlIsMissing() throws Exception {
        SymbolTradeJsonlLogService service = service();
        PaperPositionEntity position = shortPosition();
        position.setSide(PositionSide.LONG);
        position.setEntryPrice(new BigDecimal("100"));
        position.setQuantity(new BigDecimal("2"));
        position.setClosedAt(EXIT_TIME);
        position.setExitPrice(new BigDecimal("101"));
        position.setExitReason("TAKE_PROFIT");

        assertThat(service.logExit(position, PaperExitContext.builder()
                .exitPrice(new BigDecimal("101"))
                .firstHit("TP_FIRST")
                .exitTrigger("TP_5M")
                .interval("5m")
                .build())).isTrue();

        Map<String, Object> exit = readLines("AAVEUSDT").get(0);
        assertThat(new BigDecimal(exit.get("realizedPnl").toString())).isEqualByComparingTo("2");
    }

    private SymbolTradeJsonlLogService service() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new SymbolTradeJsonlLogService(objectMapper, new JsonTextMapper(objectMapper), tempDir.toString());
    }

    private List<Map<String, Object>> readLines(String symbol) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return Files.readAllLines(tempDir.resolve(symbol + ".jsonl")).stream()
                .map(line -> {
                    try {
                        return objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
    }

    private PaperPositionEntity shortPosition() {
        return PaperPositionEntity.builder()
                .id(12L)
                .symbol("AAVEUSDT")
                .side(PositionSide.SHORT)
                .status(PaperPositionStatus.OPEN)
                .entryAction(EntryAction.ENTER_SHORT)
                .entryPrice(new BigDecimal("107.40"))
                .entryPriceAdjusted(new BigDecimal("107.3463"))
                .bidPrice(new BigDecimal("107.39"))
                .askPrice(new BigDecimal("107.41"))
                .midPrice(new BigDecimal("107.40"))
                .quantity(new BigDecimal("0.4"))
                .notionalUsdt(new BigDecimal("100"))
                .leverage(3)
                .tp1(new BigDecimal("106.43"))
                .tp2(new BigDecimal("105.46"))
                .initialStop(new BigDecimal("107.83"))
                .currentStop(new BigDecimal("107.83"))
                .riskPerUnit(new BigDecimal("0.43"))
                .openedAt(ENTRY_TIME)
                .tp1Hit(false)
                .tp2Hit(false)
                .trailingActive(false)
                .remainingPositionPct(new BigDecimal("100"))
                .highestPriceSinceEntry(new BigDecimal("107.40"))
                .lowestPriceSinceEntry(new BigDecimal("107.40"))
                .lastCheckedAt(EXIT_TIME)
                .build();
    }
}
