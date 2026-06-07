package com.crypto.paper.service;

import com.crypto.api.dto.ManualClosePaperPositionRequest;
import com.crypto.api.exception.BadRequestException;
import com.crypto.api.exception.ResourceNotFoundException;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.common.time.IstanbulTimeUtil;
import com.crypto.domain.model.Ticker24h;
import com.crypto.paper.model.PaperExitReason;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaperPositionManualCloseService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PCT_SCALE = 8;

    private final PaperPositionRepository paperPositionRepository;
    private final BinanceFuturesClient binanceFuturesClient;
    private final ExitEngineService exitEngineService;

    @Transactional
    public PaperPositionEntity closeManually(Long id, ManualClosePaperPositionRequest request) {
        PaperPositionEntity position = paperPositionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paper position not found: " + id));
        if (position.getStatus() == PaperPositionStatus.CLOSED) {
            throw new BadRequestException("Position already closed");
        }

        BigDecimal exitPrice = resolveExitPrice(position, request);
        Instant now = Instant.now();
        BigDecimal pnlPct = exitEngineService.calculateUnrealizedPnlPct(position, exitPrice);
        BigDecimal pnlUsdt = exitEngineService.calculateRealizedPnlUsdt(position, pnlPct);

        updatePricePath(position, exitPrice);
        updateHoldingDuration(position, now);
        position.setStatus(PaperPositionStatus.CLOSED);
        position.setClosedAt(now);
        position.setExitPrice(exitPrice);
        position.setRealizedPnlPct(pnlPct);
        position.setRealizedPnlUsdt(pnlUsdt);
        position.setExitReason(PaperExitReason.MANUAL_CLOSE.name());
        position.setExitDetail(resolveExitDetail(request));
        position.setCurrentPrice(exitPrice);
        position.setLastCheckedAt(now);

        PaperPositionEntity saved = paperPositionRepository.save(position);
        log.info("PAPER_POSITION_MANUALLY_CLOSED closedAt={} id={} symbol={} side={} exitPrice={} pnlPct={} pnlUsdt={}",
                IstanbulTimeUtil.format(saved.getClosedAt()), saved.getId(), saved.getSymbol(), saved.getSide(), exitPrice, pnlPct, pnlUsdt);
        return saved;
    }

    private BigDecimal resolveExitPrice(PaperPositionEntity position, ManualClosePaperPositionRequest request) {
        if (request != null && request.exitPrice() != null) {
            return request.exitPrice();
        }
        String symbol = position.getSymbol();
        List<Ticker24h> tickers = binanceFuturesClient.getAll24hTickers();
        return (tickers == null ? List.<Ticker24h>of() : tickers).stream()
                .filter(Objects::nonNull)
                .filter(ticker -> symbol.equals(ticker.getSymbol()))
                .map(Ticker24h::getLastPrice)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Current price not found for symbol: " + symbol));
    }

    private String resolveExitDetail(ManualClosePaperPositionRequest request) {
        if (request == null || request.exitDetail() == null || request.exitDetail().isBlank()) {
            return "Manual close";
        }
        return request.exitDetail();
    }

    private void updateHoldingDuration(PaperPositionEntity position, Instant now) {
        Instant openedAt = position.getOpenedAt() == null ? now : position.getOpenedAt();
        long minutesHeld = Math.max(0, Duration.between(openedAt, now).toMinutes());
        position.setMinutesHeld(Math.toIntExact(Math.min(minutesHeld, Integer.MAX_VALUE)));
        position.setBarsHeld(position.getBarsHeld() == null ? 0 : position.getBarsHeld());
    }

    private void updatePricePath(PaperPositionEntity position, BigDecimal currentPrice) {
        BigDecimal entryPrice = position.getEntryPrice();
        BigDecimal highestPrice = max(position.getHighestPrice() == null ? entryPrice : position.getHighestPrice(), currentPrice);
        BigDecimal lowestPrice = min(position.getLowestPrice() == null ? entryPrice : position.getLowestPrice(), currentPrice);
        position.setHighestPrice(highestPrice);
        position.setLowestPrice(lowestPrice);

        if (position.getSide() == PositionSide.SHORT) {
            position.setMaxFavorableMovePct(pct(entryPrice.subtract(lowestPrice), entryPrice));
            position.setMaxAdverseMovePct(pct(entryPrice.subtract(highestPrice), entryPrice));
        } else {
            position.setMaxFavorableMovePct(pct(highestPrice.subtract(entryPrice), entryPrice));
            position.setMaxAdverseMovePct(pct(lowestPrice.subtract(entryPrice), entryPrice));
        }
    }

    private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, PCT_SCALE + 4, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }
}
