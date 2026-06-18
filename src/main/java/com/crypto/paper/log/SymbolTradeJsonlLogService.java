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
        if (!hasRequiredSourceIdentity(position) || !hasRequiredEntryIndicators(position, signal)) {
            log.warn("SYMBOL_TRADE_JSONL_ENTRY_SKIPPED_DATA_NOT_READY symbol={} positionId={}", position.getSymbol(), position.getId());
            return false;
        }
        return append(position, "ENTRY", entryPayload(position, signal));
    }

    public boolean logEntry(PaperPositionEntity position) {
        return logEntry(position, null);
    }

    public boolean logExit(PaperPositionEntity position, PaperExitContext exitContext) {
        PaperExitContext context = exitContext == null ? PaperExitContext.builder().build() : exitContext;
        if (position == null || exitAlreadyLogged(position, context.exitReason())) {
            return false;
        }
        if (!hasRequiredSourceIdentity(position) || !hasRequiredExitCandle(context)) {
            log.warn("SYMBOL_TRADE_JSONL_EXIT_SKIPPED_DATA_NOT_READY symbol={} positionId={} reason={}", position.getSymbol(), position.getId(), context.exitReason());
            return false;
        }
        return append(position, "EXIT", exitPayload(position, context));
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

    private boolean hasRequiredSourceIdentity(PaperPositionEntity position) {
        return position.getSourceScanRunId() != null
                && position.getSourceScanType() != null
                && position.getSourceClassification() != null
                && position.getSourceCandidateId() != null
                && position.getId() != null;
    }

    private boolean hasRequiredEntryIndicators(PaperPositionEntity position, EntrySignal signal) {
        return firstNonNull(signal == null ? null : signal.getClose1h(), position.getEntryClose1h()) != null
                && firstNonNull(signal == null ? null : signal.getEma20_1h(), position.getEntryEma20_1h()) != null
                && firstNonNull(signal == null ? null : signal.getRsi14_1h(), position.getEntryRsi14_1h()) != null
                && firstNonNull(signal == null ? null : signal.getMacdHist_1h(), position.getEntryMacdHist_1h()) != null
                && firstNonNull(signal == null ? null : signal.getAtr14_1h(), position.getEntryAtr14_1h()) != null
                && firstNonNull(signal == null ? null : signal.getVolumeRatio_1h(), position.getEntryVolumeRatio_1h()) != null;
    }

    private boolean hasRequiredExitCandle(PaperExitContext context) {
        return context.candleHigh() != null && context.candleLow() != null && context.candleClose() != null;
    }

    private BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        return primary == null ? fallback : primary;
    }

    private Path resolvePath(PaperPositionEntity position) {
        return logDirectory.resolve(position.getSymbol() + ".jsonl");
    }

    private Map<String, Object> entryPayload(PaperPositionEntity p, EntrySignal signal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putCoreEntry(payload, p);
        payload.put("tp1", p.getTp1());
        payload.put("tp2", p.getTp2());
        payload.put("slPrice", p.getInitialStop());
        payload.put("initialStop", p.getInitialStop());
        payload.put("currentStop", p.getInitialStop());
        payload.put("riskPerUnit", p.getRiskPerUnit());
        payload.put("tp1Raw", p.getTp1());
        payload.put("tp2Raw", p.getTp2());
        payload.put("slRaw", p.getInitialStop());
        payload.put("tickSize", null);
        payload.put("stepSize", null);
        putStrategy(payload, p, signal);
        putMarket(payload, p);
        putIndicatorSnapshot(payload, p, signal);
        putBollinger(payload, p, signal);
        payload.put("tp1Hit", false);
        payload.put("tp2Hit", false);
        payload.put("trailingActive", false);
        payload.put("remainingPositionPct", BigDecimal.valueOf(100));
        payload.put("highestPriceSinceEntry", p.getEntryPrice());
        payload.put("lowestPriceSinceEntry", p.getEntryPrice());
        payload.put("openedAt", format(p.getOpenedAt()));
        return payload;
    }

    private void putCoreEntry(Map<String, Object> payload, PaperPositionEntity p) {
        payload.put("type", "ENTRY");
        payload.put("positionId", p.getId());
        putSourceIdentity(payload, p);
        payload.put("symbol", p.getSymbol());
        payload.put("time", format(p.getOpenedAt()));
        payload.put("side", enumName(p.getSide()));
        payload.put("signalSide", enumName(p.getSourceSignalSide() == null ? p.getSide() : p.getSourceSignalSide()));
        payload.put("executionSide", enumName(effectiveExecutionSide(p)));
        payload.put("signalInverted", Boolean.TRUE.equals(p.getSignalInverted()));
        payload.put("inversionReason", p.getInversionReason() == null ? "" : p.getInversionReason());
        payload.put("entryAction", enumName(p.getEntryAction()));
        payload.put("entryPrice", p.getEntryPrice());
        payload.put("entryPriceAdjusted", p.getEntryPriceAdjusted());
        payload.put("bidPrice", p.getBidPrice());
        payload.put("askPrice", p.getAskPrice());
        payload.put("midPrice", p.getMidPrice());
        payload.put("qty", p.getQuantity());
        payload.put("quantity", p.getQuantity());
        payload.put("initialBalance", contextOrDefaultInitialBalance());
        payload.put("currentBalance", contextOrDefaultInitialBalance());
        payload.put("marginUsdt", p.getNotionalUsdt() == null || p.getLeverage() == null ? null : p.getNotionalUsdt().divide(BigDecimal.valueOf(p.getLeverage()), 8, java.math.RoundingMode.HALF_UP));
        payload.put("notionalUsdt", p.getNotionalUsdt());
        payload.put("leverage", p.getLeverage());
    }

    private Map<String, Object> exitPayload(PaperPositionEntity p, PaperExitContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        BigDecimal exitPrice = context.exitPrice() == null ? p.getExitPrice() : context.exitPrice();
        Instant exitTime = context.exitTime() == null ? p.getClosedAt() : context.exitTime();
        String exitReason = context.exitReason() == null ? p.getExitReason() : context.exitReason();
        BigDecimal closedPositionPct = context.closedPositionPct();
        BigDecimal remainingBefore = context.remainingPositionPctBefore();
        BigDecimal remainingAfter = context.remainingPositionPctAfter() == null ? p.getRemainingPositionPct() : context.remainingPositionPctAfter();
        BigDecimal closedQty = closedPositionPct == null || p.getQuantity() == null
                ? null
                : p.getQuantity().multiply(closedPositionPct).divide(BigDecimal.valueOf(100));
        BigDecimal realizedPnl = context.realizedPnlUsdt() == null ? realizedPnl(p, exitPrice) : context.realizedPnlUsdt();
        payload.put("type", "EXIT");
        payload.put("positionId", p.getId());
        putSourceIdentity(payload, p);
        payload.put("symbol", p.getSymbol());
        payload.put("time", format(exitTime));
        payload.put("side", enumName(p.getSide()));
        payload.put("signalSide", enumName(p.getSourceSignalSide() == null ? p.getSide() : p.getSourceSignalSide()));
        payload.put("executionSide", enumName(effectiveExecutionSide(p)));
        payload.put("signalInverted", Boolean.TRUE.equals(p.getSignalInverted()));
        payload.put("inversionReason", p.getInversionReason() == null ? "" : p.getInversionReason());
        payload.put("exitSeq", context.exitSeq());
        payload.put("exitReason", exitReason);
        payload.put("exitTrigger", context.exitTrigger());
        payload.put("firstHit", context.firstHit());
        payload.put("interval", context.interval());
        payload.put("entryPrice", p.getEntryPrice());
        payload.put("entryPriceAdjusted", p.getEntryPriceAdjusted());
        payload.put("exitPrice", exitPrice);
        payload.put("exitPriceAdjusted", context.exitPriceAdjusted() == null ? p.getExitPriceAdjusted() : context.exitPriceAdjusted());
        payload.put("qty", p.getQuantity());
        payload.put("quantity", p.getQuantity());
        payload.put("closedQty", context.closedQty() == null ? closedQty : context.closedQty());
        payload.put("remainingQty", context.remainingQty());
        payload.put("closedPositionPct", closedPositionPct);
        payload.put("remainingPositionPctBefore", remainingBefore);
        payload.put("remainingPositionPctAfter", remainingAfter);
        payload.put("initialBalance", context.initialBalance());
        payload.put("currentBalance", context.currentBalance());
        payload.put("marginUsdt", context.marginUsdt());
        payload.put("notionalUsdt", p.getNotionalUsdt());
        payload.put("leverage", p.getLeverage());
        payload.put("grossPnl", context.grossPnl());
        payload.put("entryFeePart", context.entryFeePart());
        payload.put("exitFee", context.exitFee());
        payload.put("totalFee", context.totalFee());
        payload.put("feeType", context.feeType());
        payload.put("netPnl", context.netPnl());
        payload.put("cumulativeRealizedPnl", context.cumulativeRealizedPnl());
        payload.put("netPnlPctOnMargin", context.netPnlPctOnMargin());
        payload.put("netPnlPctOnNotional", context.netPnlPctOnNotional());
        payload.put("realizedPnl", realizedPnl);
        payload.put("realizedPnlUsdt", realizedPnl);
        payload.put("realizedPnlPct", context.realizedPnlPct() == null ? p.getRealizedPnlPct() : context.realizedPnlPct());
        payload.put("rawRealizedPnlPct", context.rawRealizedPnlPct() == null ? p.getRawRealizedPnlPct() : context.rawRealizedPnlPct());
        payload.put("netRealizedPnlPct", context.netRealizedPnlPct() == null ? p.getNetRealizedPnlPct() : context.netRealizedPnlPct());
        payload.put("leveragedNetRealizedPnlPct", context.leveragedNetRealizedPnlPct() == null ? p.getLeveragedNetRealizedPnlPct() : context.leveragedNetRealizedPnlPct());
        payload.put("cumulativeRealizedPnlUsdt", p.getRealizedPnlUsdt());
        payload.put("cumulativeRealizedPnlPct", p.getRealizedPnlPct());
        payload.put("totalFeePct", p.getTotalFeePct());
        payload.put("totalSlippagePct", p.getTotalSlippagePct());
        payload.put("exitDetail", p.getExitDetail());
        putExitState(payload, p, context);
        putCandle(payload, context);
        putDevelopment(payload, p);
        payload.put("openedAt", format(p.getOpenedAt()));
        payload.put("closedAt", format(p.getClosedAt()));
        payload.put("exitTime", format(exitTime));
        payload.put("lastCheckedAt", format(p.getLastCheckedAt()));
        putStrategy(payload, p, null);
        return payload;
    }

    private void putExitState(Map<String, Object> payload, PaperPositionEntity p, PaperExitContext context) {
        payload.put("tp1", p.getTp1());
        payload.put("tp2", p.getTp2());
        payload.put("slPrice", p.getCurrentStop());
        payload.put("stopLoss", p.getInitialStop());
        payload.put("currentStopLoss", p.getCurrentStop());
        payload.put("currentStop", p.getCurrentStop());
        payload.put("initialStop", p.getInitialStop());
        payload.put("riskPerUnit", p.getRiskPerUnit());
        payload.put("tp1Hit", p.getTp1Hit());
        payload.put("tp2Hit", p.getTp2Hit());
        payload.put("trailingActive", p.getTrailingActive());
        payload.put("tp1HitBefore", context.tp1HitBefore());
        payload.put("tp1HitAfter", context.tp1HitAfter() == null ? p.getTp1Hit() : context.tp1HitAfter());
        payload.put("tp2HitBefore", context.tp2HitBefore());
        payload.put("tp2HitAfter", context.tp2HitAfter() == null ? p.getTp2Hit() : context.tp2HitAfter());
        payload.put("trailingActiveBefore", context.trailingActiveBefore());
        payload.put("trailingActiveAfter", context.trailingActiveAfter() == null ? p.getTrailingActive() : context.trailingActiveAfter());
        payload.put("trailingActivatedAtBarCloseTime", format(p.getTrailingActivatedAtBarCloseTime()));
        payload.put("remainingPositionPct", p.getRemainingPositionPct());
    }

    private void putCandle(Map<String, Object> payload, PaperExitContext context) {
        payload.put("candleOpen", context.candleOpen());
        payload.put("candleOpenTime", format(context.candleOpenTime()));
        payload.put("candleCloseTime", format(context.candleCloseTime()));
        payload.put("candleHigh", context.candleHigh());
        payload.put("candleLow", context.candleLow());
        payload.put("candleClose", context.candleClose());
        payload.put("touchedLevel", context.touchedLevel());
        payload.put("targetPrice", context.targetPrice());
        payload.put("fillPrice", context.fillPrice());
        payload.put("exitExecutionMode", context.exitExecutionMode());
        payload.put("intrabarAmbiguous", context.intrabarAmbiguous());
        payload.put("intrabarExecutionMode", context.intrabarExecutionMode());
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
        payload.put("scanRunId", p.getSourceScanRunId());
        payload.put("sourceScanType", enumName(p.getSourceScanType()));
        payload.put("sourceClassification", enumName(p.getSourceClassification()));
        payload.put("candidateId", p.getSourceCandidateId());
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

    private void putSourceIdentity(Map<String, Object> payload, PaperPositionEntity p) {
        payload.put("scanRunId", p.getSourceScanRunId());
        payload.put("sourceScanType", enumName(p.getSourceScanType()));
        payload.put("sourceClassification", enumName(p.getSourceClassification()));
        payload.put("candidateId", p.getSourceCandidateId());
    }

    private void putMarket(Map<String, Object> payload, PaperPositionEntity p) {
        payload.put("fundingRate", p.getFundingRate());
        payload.put("openInterest", p.getOpenInterest());
        payload.put("priceChange24hPct", p.getPriceChange24hPct());
        payload.put("spreadPct", p.getSpreadPct());
    }

    private void putIndicatorSnapshot(Map<String, Object> payload, PaperPositionEntity p, EntrySignal signal) {
        payload.put("close1h", signal != null && signal.getClose1h() != null ? signal.getClose1h() : p.getEntryClose1h());
        payload.put("ema20_1h", signal != null && signal.getEma20_1h() != null ? signal.getEma20_1h() : p.getEntryEma20_1h());
        payload.put("ema50_1h", null);
        payload.put("ema200_1h", null);
        payload.put("rsi14_1h", signal != null && signal.getRsi14_1h() != null ? signal.getRsi14_1h() : p.getEntryRsi14_1h());
        payload.put("macdHist_1h", signal != null && signal.getMacdHist_1h() != null ? signal.getMacdHist_1h() : p.getEntryMacdHist_1h());
        payload.put("previousMacdHist_1h", signal == null ? null : signal.getPreviousMacdHist_1h());
        payload.put("atr14_1h", signal != null && signal.getAtr14_1h() != null ? signal.getAtr14_1h() : p.getEntryAtr14_1h());
        payload.put("volumeRatio_1h", signal != null && signal.getVolumeRatio_1h() != null ? signal.getVolumeRatio_1h() : p.getEntryVolumeRatio_1h());
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

    private BigDecimal contextOrDefaultInitialBalance() { return new BigDecimal("100"); }

    private PositionSide effectiveExecutionSide(PaperPositionEntity p) {
        return p.getExecutionSide() == null ? p.getSide() : p.getExecutionSide();
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

    private boolean exitAlreadyLogged(PaperPositionEntity position, String exitReason) {
        if ("PARTIAL_TP1".equals(exitReason)) {
            return Boolean.TRUE.equals(position.getSymbolTradeTp1ExitLogged());
        }
        if ("PARTIAL_TP2".equals(exitReason)) {
            return Boolean.TRUE.equals(position.getSymbolTradeTp2ExitLogged());
        }
        return Boolean.TRUE.equals(position.getSymbolTradeFinalExitLogged()) || Boolean.TRUE.equals(position.getSymbolTradeExitLogged());
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
            BigDecimal exitPriceAdjusted,
            Instant exitTime,
            Integer exitSeq,
            String exitReason,
            String firstHit,
            String exitTrigger,
            String interval,
            BigDecimal closedPositionPct,
            BigDecimal remainingPositionPctBefore,
            BigDecimal remainingPositionPctAfter,
            BigDecimal realizedPnlUsdt,
            BigDecimal realizedPnlPct,
            BigDecimal rawRealizedPnlPct,
            BigDecimal netRealizedPnlPct,
            BigDecimal leveragedNetRealizedPnlPct,
            BigDecimal initialBalance,
            BigDecimal currentBalance,
            BigDecimal marginUsdt,
            BigDecimal closedQty,
            BigDecimal remainingQty,
            BigDecimal grossPnl,
            BigDecimal entryFeePart,
            BigDecimal exitFee,
            BigDecimal totalFee,
            String feeType,
            BigDecimal netPnl,
            BigDecimal cumulativeRealizedPnl,
            BigDecimal netPnlPctOnMargin,
            BigDecimal netPnlPctOnNotional,
            Boolean tp1HitBefore,
            Boolean tp1HitAfter,
            Boolean tp2HitBefore,
            Boolean tp2HitAfter,
            Boolean trailingActiveBefore,
            Boolean trailingActiveAfter,
            Instant candleOpenTime,
            Instant candleCloseTime,
            BigDecimal candleOpen,
            BigDecimal candleHigh,
            BigDecimal candleLow,
            BigDecimal candleClose,
            String touchedLevel,
            BigDecimal targetPrice,
            BigDecimal fillPrice,
            String exitExecutionMode,
            Boolean intrabarAmbiguous,
            String intrabarExecutionMode) {
    }
}
