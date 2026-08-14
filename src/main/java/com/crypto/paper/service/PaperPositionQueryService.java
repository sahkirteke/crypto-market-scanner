package com.crypto.paper.service;

import com.crypto.api.dto.PaperPositionResponse;
import com.crypto.api.dto.PaperTradeSummaryResponse;
import com.crypto.api.exception.ResourceNotFoundException;
import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import com.crypto.api.mapper.PaperPositionApiMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaperPositionQueryService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PCT_SCALE = 8;

    private final PaperPositionRepository paperPositionRepository;
    private final PaperPositionApiMapper paperPositionApiMapper;
    public List<PaperPositionResponse> getOpenPositions() {
        List<PaperPositionResponse> responses = paperPositionApiMapper.toResponseList(
                paperPositionRepository.findByStatusInOrderByOpenedAtDesc(List.of(PaperPositionStatus.OPEN, PaperPositionStatus.PARTIALLY_CLOSED))
        );
        log.info("PAPER_POSITIONS_OPEN_READY count={}", responses.size());
        return responses;
    }

    public List<PaperPositionResponse> getClosedPositions(int limit) {
        int safeLimit = normalizeLimit(limit);
        List<PaperPositionResponse> responses = paperPositionApiMapper.toResponseList(paperPositionRepository
                .findByStatusOrderByClosedAtDesc(PaperPositionStatus.CLOSED, PageRequest.of(0, safeLimit))
                .getContent());
        log.info("PAPER_POSITIONS_CLOSED_READY count={}", responses.size());
        return responses;
    }

    public PaperPositionResponse getPositionDetail(Long id) {
        PaperPositionResponse response = paperPositionApiMapper.toResponse(paperPositionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paper position not found: " + id)));
        log.info("PAPER_POSITION_DETAIL_READY id={}", id);
        return response;
    }

    public List<PaperPositionResponse> getSymbolPositions(String symbol, int limit) {
        String normalizedSymbol = normalizeSymbol(symbol);
        int safeLimit = normalizeLimit(limit);
        return paperPositionApiMapper.toResponseList(paperPositionRepository
                .findBySymbolOrderByOpenedAtDesc(normalizedSymbol, PageRequest.of(0, safeLimit))
                .getContent());
    }

    public PaperTradeSummaryResponse getSummary() {
        long openCount = paperPositionRepository.countByStatus(PaperPositionStatus.OPEN) + paperPositionRepository.countByStatus(PaperPositionStatus.PARTIALLY_CLOSED);
        long closedCount = paperPositionRepository.countByStatus(PaperPositionStatus.CLOSED);
        long openLongCount = paperPositionRepository.countByStatusAndSide(PaperPositionStatus.OPEN, PositionSide.LONG);
        long openShortCount = paperPositionRepository.countByStatusAndSide(PaperPositionStatus.OPEN, PositionSide.SHORT);
        List<PaperPositionEntity> closedPositions = paperPositionRepository.findByStatus(PaperPositionStatus.CLOSED);

        BigDecimal totalRealizedPnlUsdt = BigDecimal.ZERO;
        BigDecimal totalRealizedPnlPct = BigDecimal.ZERO;
        long pnlPctCount = 0;
        long winCount = 0;
        long lossCount = 0;

        for (PaperPositionEntity position : closedPositions) {
            BigDecimal realizedPnlUsdt = position.getRealizedPnlUsdt();
            if (realizedPnlUsdt != null) {
                totalRealizedPnlUsdt = totalRealizedPnlUsdt.add(realizedPnlUsdt);
                if (realizedPnlUsdt.compareTo(BigDecimal.ZERO) > 0) {
                    winCount++;
                } else if (realizedPnlUsdt.compareTo(BigDecimal.ZERO) < 0) {
                    lossCount++;
                }
            }

            BigDecimal realizedPnlPct = position.getRealizedPnlPct();
            if (realizedPnlPct != null) {
                totalRealizedPnlPct = totalRealizedPnlPct.add(realizedPnlPct);
                pnlPctCount++;
            }
        }

        long winLossCount = winCount + lossCount;
        BigDecimal winRatePct = winLossCount == 0
                ? BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(winCount)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(winLossCount), PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal avgRealizedPnlPct = pnlPctCount == 0
                ? BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP)
                : totalRealizedPnlPct.divide(BigDecimal.valueOf(pnlPctCount), PCT_SCALE, RoundingMode.HALF_UP);

        PaperTradeSummaryResponse response = new PaperTradeSummaryResponse(
                openCount,
                closedCount,
                openLongCount,
                openShortCount,
                totalRealizedPnlUsdt,
                winCount,
                lossCount,
                winRatePct,
                avgRealizedPnlPct
        );
        log.info("PAPER_TRADE_SUMMARY_READY open={} closed={} winRate={} pnlUsdt={}",
                openCount, closedCount, winRatePct, totalRealizedPnlUsdt);
        return response;
    }

    private int normalizeLimit(int limit) {
        int requestedLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
