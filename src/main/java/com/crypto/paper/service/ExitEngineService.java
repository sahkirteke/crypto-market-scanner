package com.crypto.paper.service;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Ticker24h;
import com.crypto.paper.model.PaperExitReason;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.scanner.config.ScannerProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExitEngineService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PCT_SCALE = 8;

    private final PaperPositionRepository paperPositionRepository;
    private final BinanceFuturesClient binanceFuturesClient;
    private final ScannerProperties scannerProperties;

    public List<PaperPositionEntity> evaluateOpenPositions() {
        ScannerProperties.PaperExit config = paperExitConfig();
        if (!booleanValue(config.getEnabled(), true)) {
            log.info("PAPER_EXIT_DISABLED");
            return List.of();
        }

        List<PaperPositionEntity> openPositions = paperPositionRepository.findByStatusOrderByOpenedAtDesc(
                PaperPositionStatus.OPEN
        );
        Map<String, BigDecimal> priceBySymbol = priceBySymbol();

        List<PaperPositionEntity> evaluated = openPositions.stream()
                .map(position -> evaluateWithPrice(position, priceBySymbol.get(position.getSymbol())))
                .filter(Objects::nonNull)
                .map(paperPositionRepository::save)
                .toList();
        List<PaperPositionEntity> closed = evaluated.stream()
                .filter(position -> position.getStatus() == PaperPositionStatus.CLOSED)
                .toList();
        log.info("PAPER_EXIT_EVALUATION_DONE openChecked={} closed={}", openPositions.size(), closed.size());
        return closed;
    }

    public PaperPositionEntity evaluatePosition(PaperPositionEntity position, BigDecimal currentPrice) {
        Instant now = Instant.now();
        BigDecimal pnlPct = calculateUnrealizedPnlPct(position, currentPrice);
        updatePricePath(position, currentPrice);
        updateHoldingDuration(position, now);
        position.setLastCheckedAt(now);

        log.info(
                "PAPER_POSITION_EVALUATED id={} symbol={} side={} currentPrice={} pnlPct={} maxFav={} maxAdv={} minutesHeld={}",
                position.getId(),
                position.getSymbol(),
                position.getSide(),
                position.getCurrentPrice(),
                pnlPct,
                position.getMaxFavorableMovePct(),
                position.getMaxAdverseMovePct(),
                position.getMinutesHeld()
        );

        PaperExitReason exitReason = resolveExitReason(position, pnlPct);
        if (exitReason != null) {
            closePosition(position, currentPrice, pnlPct, exitReason, now);
        }
        return position;
    }

    public BigDecimal calculateUnrealizedPnlPct(PaperPositionEntity position, BigDecimal currentPrice) {
        BigDecimal entryPrice = position.getEntryPrice();
        if (position.getSide() == PositionSide.SHORT) {
            return entryPrice.subtract(currentPrice)
                    .divide(entryPrice, PCT_SCALE + 4, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED)
                    .setScale(PCT_SCALE, RoundingMode.HALF_UP);
        }
        return currentPrice.subtract(entryPrice)
                .divide(entryPrice, PCT_SCALE + 4, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateRealizedPnlUsdt(PaperPositionEntity position, BigDecimal pnlPct) {
        BigDecimal notionalUsdt = position.getNotionalUsdt() == null ? BigDecimal.ZERO : position.getNotionalUsdt();
        return notionalUsdt.multiply(pnlPct)
                .divide(ONE_HUNDRED, PCT_SCALE, RoundingMode.HALF_UP);
    }

    private PaperPositionEntity evaluateWithPrice(PaperPositionEntity position, BigDecimal currentPrice) {
        if (currentPrice == null) {
            log.warn("PAPER_EXIT_PRICE_MISSING symbol={}", position.getSymbol());
            return null;
        }
        return evaluatePosition(position, currentPrice);
    }

    private Map<String, BigDecimal> priceBySymbol() {
        List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
        return (tickers == null ? List.<Ticker24h>of() : tickers).stream()
                .filter(Objects::nonNull)
                .filter(ticker -> ticker.getSymbol() != null)
                .filter(ticker -> ticker.getLastPrice() != null)
                .collect(Collectors.toMap(Ticker24h::getSymbol, Ticker24h::getLastPrice, (left, right) -> left));
    }

    private void updatePricePath(PaperPositionEntity position, BigDecimal currentPrice) {
        BigDecimal entryPrice = position.getEntryPrice();
        position.setCurrentPrice(currentPrice);
        position.setHighestPrice(max(position.getHighestPrice() == null ? entryPrice : position.getHighestPrice(), currentPrice));
        position.setLowestPrice(min(position.getLowestPrice() == null ? entryPrice : position.getLowestPrice(), currentPrice));

        if (position.getSide() == PositionSide.SHORT) {
            position.setMaxFavorableMovePct(pct(entryPrice.subtract(position.getLowestPrice()), entryPrice));
            position.setMaxAdverseMovePct(pct(entryPrice.subtract(position.getHighestPrice()), entryPrice));
        } else {
            position.setMaxFavorableMovePct(pct(position.getHighestPrice().subtract(entryPrice), entryPrice));
            position.setMaxAdverseMovePct(pct(position.getLowestPrice().subtract(entryPrice), entryPrice));
        }
    }

    private void updateHoldingDuration(PaperPositionEntity position, Instant now) {
        Instant openedAt = position.getOpenedAt() == null ? now : position.getOpenedAt();
        long minutesHeld = Math.max(0, Duration.between(openedAt, now).toMinutes());
        int barMinutes = Math.max(1, intValue(paperExitConfig().getBarMinutes(), 60));
        position.setMinutesHeld(Math.toIntExact(Math.min(minutesHeld, Integer.MAX_VALUE)));
        position.setBarsHeld(Math.toIntExact(Math.min(minutesHeld / barMinutes, Integer.MAX_VALUE)));
    }

    private PaperExitReason resolveExitReason(PaperPositionEntity position, BigDecimal pnlPct) {
        BigDecimal stopLossPct = defaultBigDecimal(position.getStopLossPct(), paperExitConfig().getStopLossPct(), "0.6");
        BigDecimal takeProfitPct = defaultBigDecimal(position.getTakeProfitPct(), paperExitConfig().getTakeProfitPct(), "1.0");
        int timeStopMinutes = intValue(position.getTimeStopMinutes(), intValue(paperExitConfig().getTimeStopMinutes(), 240));

        if (pnlPct.compareTo(stopLossPct.negate()) <= 0) {
            return PaperExitReason.STOP_LOSS;
        }
        if (pnlPct.compareTo(takeProfitPct) >= 0) {
            return PaperExitReason.TAKE_PROFIT;
        }
        if (position.getMinutesHeld() != null && position.getMinutesHeld() >= timeStopMinutes) {
            boolean closeOnlyIfNonPositive = booleanValue(paperExitConfig().getTimeStopCloseOnlyIfNonPositive(), true);
            if (!closeOnlyIfNonPositive || pnlPct.compareTo(BigDecimal.ZERO) <= 0) {
                return PaperExitReason.TIME_STOP;
            }
        }
        return null;
    }

    private void closePosition(
            PaperPositionEntity position,
            BigDecimal currentPrice,
            BigDecimal pnlPct,
            PaperExitReason exitReason,
            Instant now
    ) {
        position.setStatus(PaperPositionStatus.CLOSED);
        position.setClosedAt(now);
        position.setExitPrice(currentPrice);
        position.setRealizedPnlPct(pnlPct);
        position.setRealizedPnlUsdt(calculateRealizedPnlUsdt(position, pnlPct));
        position.setExitReason(exitReason.name());
        position.setExitDetail("paper exit reason=" + exitReason.name()
                + " pnlPct=" + pnlPct
                + " leverage=" + position.getLeverage());
        log.info(
                "PAPER_POSITION_CLOSED id={} symbol={} side={} exitReason={} exitPrice={} pnlPct={} pnlUsdt={}",
                position.getId(),
                position.getSymbol(),
                position.getSide(),
                position.getExitReason(),
                position.getExitPrice(),
                position.getRealizedPnlPct(),
                position.getRealizedPnlUsdt()
        );
    }

    private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, PCT_SCALE + 4, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private ScannerProperties.PaperExit paperExitConfig() {
        return scannerProperties.getPaperExit() == null ? new ScannerProperties.PaperExit() : scannerProperties.getPaperExit();
    }

    private BigDecimal defaultBigDecimal(BigDecimal primary, BigDecimal fallback, String defaultValue) {
        if (primary != null) {
            return primary;
        }
        return fallback == null ? new BigDecimal(defaultValue) : fallback;
    }

    private boolean booleanValue(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int intValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }
}
