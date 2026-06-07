package com.crypto.api.controller;

import com.crypto.api.dto.ManualClosePaperPositionRequest;
import com.crypto.api.dto.PaperPositionResponse;
import com.crypto.api.dto.PaperTradeSummaryResponse;
import com.crypto.api.mapper.PaperPositionApiMapper;
import com.crypto.paper.service.ExitEngineService;
import com.crypto.paper.service.PaperPositionManualCloseService;
import com.crypto.paper.service.PaperPositionQueryService;
import com.crypto.paper.service.PaperPositionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
@Slf4j
public class PaperPositionController {
    private final PaperPositionService paperPositionService;
    private final ExitEngineService exitEngineService;
    private final PaperPositionQueryService paperPositionQueryService;
    private final PaperPositionManualCloseService paperPositionManualCloseService;
    private final PaperPositionApiMapper paperPositionApiMapper;

    @GetMapping("/positions/open")
    public List<PaperPositionResponse> getOpenPositions() {
        log.info("PAPER_API_OPEN_POSITIONS_REQUEST");
        return paperPositionQueryService.getOpenPositions();
    }

    @GetMapping("/positions/closed")
    public List<PaperPositionResponse> getClosedPositions(@RequestParam(defaultValue = "50") int limit) {
        log.info("PAPER_API_CLOSED_POSITIONS_REQUEST limit={}", limit);
        return paperPositionQueryService.getClosedPositions(limit);
    }

    @GetMapping("/positions/{id}")
    public PaperPositionResponse getPositionDetail(@PathVariable Long id) {
        log.info("PAPER_API_POSITION_DETAIL_REQUEST id={}", id);
        return paperPositionQueryService.getPositionDetail(id);
    }

    @GetMapping("/positions/symbol/{symbol}")
    public List<PaperPositionResponse> getSymbolPositions(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "50") int limit
    ) {
        log.info("PAPER_API_SYMBOL_POSITIONS_REQUEST symbol={} limit={}", symbol, limit);
        return paperPositionQueryService.getSymbolPositions(symbol, limit);
    }

    @PostMapping("/open-from-latest-signals")
    public List<PaperPositionResponse> openFromLatestSignals() {
        log.info("PAPER_API_OPEN_FROM_SIGNALS_REQUEST");
        return paperPositionApiMapper.toResponseList(paperPositionService.openPositionsFromLatestSignals());
    }

    @PostMapping("/evaluate-open-positions")
    public List<PaperPositionResponse> evaluateOpenPositions() {
        log.info("PAPER_API_EVALUATE_OPEN_REQUEST");
        return paperPositionApiMapper.toResponseList(exitEngineService.evaluateOpenPositions());
    }

    @PostMapping("/positions/{id}/manual-close")
    public PaperPositionResponse closeManually(
            @PathVariable Long id,
            @RequestBody(required = false) ManualClosePaperPositionRequest request
    ) {
        log.info("PAPER_API_MANUAL_CLOSE_REQUEST id={}", id);
        return paperPositionApiMapper.toResponse(paperPositionManualCloseService.closeManually(id, request));
    }

    @GetMapping("/summary")
    public PaperTradeSummaryResponse getSummary() {
        log.info("PAPER_API_SUMMARY_REQUEST");
        return paperPositionQueryService.getSummary();
    }
}
