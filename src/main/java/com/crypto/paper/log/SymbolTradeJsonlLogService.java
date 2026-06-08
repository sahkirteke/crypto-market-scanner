package com.crypto.paper.log;

import com.crypto.common.enums.PositionSide;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.domain.model.EntrySignal;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.mapper.JsonTextMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SymbolTradeJsonlLogService {
    private final ObjectMapper objectMapper;
    private final JsonTextMapper jsonTextMapper;
    private final Path logDirectory;

    public SymbolTradeJsonlLogService(
            ObjectMapper objectMapper,
            JsonTextMapper jsonTextMapper,
            @Value("${paper.symbol-trade-log.directory:logs/paper/symbol-trades}") String logDirectory) {
        this.objectMapper = objectMapper;
        this.jsonTextMapper = jsonTextMapper;
        this.logDirectory = Path.of(logDirectory);
    }

    public boolean logEntry(PaperPositionEntity position, EntrySignal signal) {
        if (position == null || Boolean.TRUE.equals(position.getSymbolTradeEntryLogged())) {
            return false;
        }
        return append(position, "ENTRY", entryPayload(position, signal));
    }

    public boolean logEntry(PaperPositionEntity position) {
        return logEntry(position, null);
    }

    public boolean logExit(PaperPositionEntity position, PaperExitContext exitContext) {
        if (position == null || Boolean.TRUE.equals(position.getSymbolTradeExitLogged())) {
            return false;
        }
        return append(position, "EXIT", exitPayload(position, exitContext == null ? PaperExitContext.builder().build() : exitContext));
    }

    public boolean logExit(PaperPositionEntity position) {
        return logExit(position, null);
    }

    private boolean append(PaperPositionEntity position, String type, Map<String, Object> payload) {
        Path path = resolvePath(position);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, objectMapper.writeValueAsString(payload) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if ("ENTRY".equals(type)) {
                log.info("SYMBOL_TRADE_JSONL_ENTRY_WRITTEN symbol={} positionId={} path={}", position.getSymbol(), position.getId(), path);
            } else {
                log.info("SYMBOL_TRADE_JSONL_EXIT_WRITTEN symbol={} positionId={} path={}", position.getSymbol(), position.getId(), path);
            }
            return true;
        } catch (Exception exception) {
            log.warn("SYMBOL_TRADE_JSONL_WRITE_FAILED symbol={} positionId={} type={} message={}",
                    position.getSymbol(), position.getId(), type, exception.getMessage());
            return false;
        }
    }

    private Path resolvePath(PaperPositionEntity position) {
        return logDirectory.resolve(position.getSymbol() + ".jsonl");
    }

    private Map<String, Object> entryPayload(PaperPositionEntity p, EntrySignal signal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putCoreEntry(payload, p);
        payload.put("tp1", p.getTp1());
        payload.put("tp2", p.getTp2());
        payload.put("slPrice", p.getCurrentStop() == null ? p.getInitialStop() : p.getCurrentStop());
        payload.put("initialStop", p.getInitialStop());
        payload.put("currentStop", p.getCurrentStop());
        payload.put("riskPerUnit", p.getRiskPerUnit());
        payload.put("tp1Raw", p.getTp1());
        payload.put("tp2Raw", p.getTp2());
        payload.put("slRaw", p.getInitialStop());
        payload.put("tickSize", null);
        payload.put("stepSize", null);
        putStrategy(payload, p, signal);
        putMarket(payload, p);
        putIndicatorSnapshot(payload, signal);
        putBollinger(payload, p, signal);
        payload.put("tp1Hit", p.getTp1Hit());
        payload.put("tp2Hit", p.getTp2Hit());
        payload.put("trailingActive", p.getTrailingActive());
        payload.put("remainingPositionPct", p.getRemainingPositionPct());
        payload.put("highestPriceSinceEntry", p.getHighestPriceSinceEntry());
        payload.put("lowestPriceSinceEntry", p.getLowestPriceSinceEntry());
        payload.put("openedAt", format(p.getOpenedAt()));
        return payload;
    }

    private void putCoreEntry(Map<String, Object> payload, PaperPositionEntity p) {
        payload.put("type", "ENTRY");
        payload.put("positionId", p.getId());
        payload.put("symbol", p.getSymbol());
        payload.put("time", format(p.getOpenedAt()));
        payload.put("side", enumName(p.getSide()));
        payload.put("entryAction", enumName(p.getEntryAction()));
        payload.put("entryPrice", p.getEntryPrice());
        payload.put("entryPriceAdjusted", p.getEntryPriceAdjusted());
        payload.put("bidPrice", p.getBidPrice());
        payload.put("askPrice", p.getAskPrice());
        payload.put("midPrice", p.getMidPrice());
        payload.put("qty", p.getQuantity());
        payload.put("quantity", p.getQuantity());
        payload.put("notionalUsdt", p.getNotionalUsdt());
        payload.put("leverage", p.getLeverage());
    }

    private Map<String, Object> exitPayload(PaperPositionEntity p, PaperExitContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        BigDecimal exitPrice = context.exitPrice() == null ? p.getExitPrice() : context.exitPrice();
        payload.put("type", "EXIT");
        payload.put("positionId", p.getId());
        payload.put("symbol", p.getSymbol());
        payload.put("time", format(p.getClosedAt()));
        payload.put("side", enumName(p.getSide()));
        payload.put("entryPrice", p.getEntryPrice());
        payload.put("entryPriceAdjusted", p.getEntryPriceAdjusted());
        payload.put("exitPrice", exitPrice);
        payload.put("exitPriceAdjusted", p.getExitPriceAdjusted());
        payload.put("qty", p.getQuantity());
        payload.put("quantity", p.getQuantity());
        payload.put("notionalUsdt", p.getNotionalUsdt());
        payload.put("leverage", p.getLeverage());
        BigDecimal realizedPnl = realizedPnl(p, exitPrice);
        payload.put("realizedPnl", realizedPnl);
        payload.put("realizedPnlUsdt", realizedPnl);
        payload.put("realizedPnlPct", p.getRealizedPnlPct());
        payload.put("rawRealizedPnlPct", p.getRawRealizedPnlPct());
        payload.put("netRealizedPnlPct", p.getNetRealizedPnlPct());
        payload.put("leveragedNetRealizedPnlPct", p.getLeveragedNetRealizedPnlPct());
        payload.put("totalFeePct", p.getTotalFeePct());
        payload.put("totalSlippagePct", p.getTotalSlippagePct());
        payload.put("exitReason", p.getExitReason());
        payload.put("exitDetail", p.getExitDetail());
        payload.put("firstHit", context.firstHit());
        payload.put("exitTrigger", context.exitTrigger());
        payload.put("interval", context.interval());
        putExitState(payload, p);
        putCandle(payload, context);
        putDevelopment(payload, p);
        payload.put("openedAt", format(p.getOpenedAt()));
        payload.put("closedAt", format(p.getClosedAt()));
        payload.put("lastCheckedAt", format(p.getLastCheckedAt()));
        putStrategy(payload, p, null);
        return payload;
    }

    private void putExitState(Map<String, Object> payload, PaperPositionEntity p) {
        payload.put("tp1", p.getTp1());
        payload.put("tp2", p.getTp2());
        payload.put("slPrice", p.getCurrentStop());
        payload.put("currentStop", p.getCurrentStop());
        payload.put("initialStop", p.getInitialStop());
        payload.put("riskPerUnit", p.getRiskPerUnit());
        payload.put("tp1Hit", p.getTp1Hit());
        payload.put("tp2Hit", p.getTp2Hit());
        payload.put("trailingActive", p.getTrailingActive());
        payload.put("trailingActivatedAtBarCloseTime", format(p.getTrailingActivatedAtBarCloseTime()));
        payload.put("remainingPositionPct", p.getRemainingPositionPct());
    }

    private void putCandle(Map<String, Object> payload, PaperExitContext context) {
        payload.put("candleOpenTime", format(context.candleOpenTime()));
        payload.put("candleCloseTime", format(context.candleCloseTime()));
        payload.put("candleHigh", context.candleHigh());
        payload.put("candleLow", context.candleLow());
        payload.put("candleClose", context.candleClose());
    }

    private void putDevelopment(Map<String, Object> payload, PaperPositionEntity p) {
        payload.put("highestPriceSinceEntry", p.getHighestPriceSinceEntry());
        payload.put("lowestPriceSinceEntry", p.getLowestPriceSinceEntry());
        payload.put("maxFavorableMovePct", p.getMaxFavorableMovePct());
        payload.put("maxAdverseMovePct", p.getMaxAdverseMovePct());
        payload.put("barsInPosition", p.getBarsInPosition());
        payload.put("barsHeld", p.getBarsHeld());
        payload.put("minutesHeld", minutesHeld(p));
    }

    private void putStrategy(Map<String, Object> payload, PaperPositionEntity p, EntrySignal signal) {
        payload.put("scanRunId", null);
        payload.put("sourceScanType", null);
        payload.put("sourceClassification", enumName(p.getSourceClassification()));
        payload.put("directionBias", enumName(p.getDirectionBias()));
        payload.put("matchedSetup", p.getEntryReason());
        payload.put("entryReason", p.getEntryReason());
        payload.put("signalReason", p.getSignalReason());
        payload.put("marketRegime", signal == null ? enumName(p.getMarketRegime()) : enumName(signal.getMarketRegime()));
        payload.put("marketBreadthPct", p.getMarketBreadthPct());
        payload.put("entryScore", p.getEntryScore());
        payload.put("baseEntryScore", signal != null && signal.getBaseEntryScore() != null ? signal.getBaseEntryScore() : baseEntryScore(p));
        payload.put("bbScore", p.getEntryBbScore());
        payload.put("finalEntryScore", signal != null && signal.getFinalEntryScore() != null ? signal.getFinalEntryScore() : p.getEntryScore());
        payload.put("longScore", p.getLongScore());
        payload.put("shortScore", p.getShortScore());
        payload.put("riskLevel", enumName(p.getRiskLevel()));
        payload.put("reasons", jsonTextMapper.toStringList(p.getReasonsJson()));
        payload.put("warnings", jsonTextMapper.toStringList(p.getWarningsJson()));
    }

    private void putMarket(Map<String, Object> payload, PaperPositionEntity p) {
        payload.put("fundingRate", p.getFundingRate());
        payload.put("openInterest", p.getOpenInterest());
        payload.put("priceChange24hPct", p.getPriceChange24hPct());
        payload.put("spreadPct", p.getSpreadPct());
    }

    private void putIndicatorSnapshot(Map<String, Object> payload, EntrySignal signal) {
        payload.put("close1h", signal == null ? null : signal.getClose1h());
        payload.put("ema20_1h", signal == null ? null : signal.getEma20_1h());
        payload.put("ema50_1h", null);
        payload.put("ema200_1h", null);
        payload.put("rsi14_1h", signal == null ? null : signal.getRsi14_1h());
        payload.put("macdHist_1h", signal == null ? null : signal.getMacdHist_1h());
        payload.put("previousMacdHist_1h", signal == null ? null : signal.getPreviousMacdHist_1h());
        payload.put("atr14_1h", signal == null ? null : signal.getAtr14_1h());
        payload.put("volumeRatio_1h", signal == null ? null : signal.getVolumeRatio_1h());
    }

    private void putBollinger(Map<String, Object> payload, PaperPositionEntity p, EntrySignal signal) {
        payload.put("bbPercentB", p.getEntryBbPercentB());
        payload.put("bbWidth", p.getEntryBbWidth());
        payload.put("bbUpper", p.getEntryBbUpper());
        payload.put("bbMiddle", p.getEntryBbMiddle());
        payload.put("bbLower", p.getEntryBbLower());
        payload.put("bbUpperTouched", p.getEntryBbUpperTouched());
        payload.put("bbLowerTouched", p.getEntryBbLowerTouched());
        payload.put("bbUpperClosedOutside", p.getEntryBbUpperClosedOutside());
        payload.put("bbLowerClosedOutside", p.getEntryBbLowerClosedOutside());
        payload.put("bbReasons", signal != null && signal.getBbReasons() != null ? signal.getBbReasons() : jsonTextMapper.toStringList(p.getEntryBbReasonsJson()));
    }

    private BigDecimal baseEntryScore(PaperPositionEntity p) {
        if (p.getEntryScore() == null || p.getEntryBbScore() == null) {
            return p.getEntryScore() == null ? null : BigDecimal.valueOf(p.getEntryScore());
        }
        return BigDecimal.valueOf(p.getEntryScore()).subtract(p.getEntryBbScore());
    }

    private BigDecimal realizedPnl(PaperPositionEntity p, BigDecimal exitPrice) {
        if (p.getRealizedPnlUsdt() != null) {
            return p.getRealizedPnlUsdt();
        }
        if (p.getEntryPrice() == null || exitPrice == null || p.getQuantity() == null || p.getSide() == null) {
            return null;
        }
        BigDecimal diff = p.getSide() == PositionSide.SHORT
                ? p.getEntryPrice().subtract(exitPrice)
                : exitPrice.subtract(p.getEntryPrice());
        return diff.multiply(p.getQuantity()).stripTrailingZeros();
    }

    private int minutesHeld(PaperPositionEntity p) {
        if (p.getOpenedAt() == null || p.getClosedAt() == null) {
            return p.getMinutesHeld() == null ? 0 : p.getMinutesHeld();
        }
        long minutes = Math.max(0, Duration.between(p.getOpenedAt(), p.getClosedAt()).toMinutes());
        return Math.toIntExact(Math.min(minutes, Integer.MAX_VALUE));
    }

    private String format(Instant instant) {
        return IstanbulTimeUtil.format(instant);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @Builder
    public record PaperExitContext(
            BigDecimal exitPrice,
            String firstHit,
            String exitTrigger,
            String interval,
            Instant candleOpenTime,
            Instant candleCloseTime,
            BigDecimal candleHigh,
            BigDecimal candleLow,
            BigDecimal candleClose) {
    }
}
