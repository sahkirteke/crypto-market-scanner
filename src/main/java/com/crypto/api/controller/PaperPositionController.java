package com.crypto.api.controller;

import com.crypto.api.dto.PaperPositionResponse;
import com.crypto.api.mapper.PaperPositionApiMapper;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.paper.service.ExitEngineService;
import com.crypto.paper.service.PaperPositionService;
import com.crypto.persistence.repository.PaperPositionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class PaperPositionController {
    private final PaperPositionService paperPositionService;
    private final ExitEngineService exitEngineService;
    private final PaperPositionRepository paperPositionRepository;
    private final PaperPositionApiMapper paperPositionApiMapper;

    @PostMapping("/open-from-latest-signals")
    public List<PaperPositionResponse> openFromLatestSignals() {
        return paperPositionApiMapper.toResponseList(paperPositionService.openPositionsFromLatestSignals());
    }

    @GetMapping("/positions/open")
    public List<PaperPositionResponse> getOpenPositions() {
        return paperPositionApiMapper.toResponseList(paperPositionService.getOpenPositions());
    }

    @PostMapping("/evaluate-open-positions")
    public List<PaperPositionResponse> evaluateOpenPositions() {
        return paperPositionApiMapper.toResponseList(exitEngineService.evaluateOpenPositions());
    }

    @GetMapping("/positions/closed")
    public List<PaperPositionResponse> getClosedPositions(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return paperPositionApiMapper.toResponseList(paperPositionRepository
                .findByStatusOrderByClosedAtDesc(PaperPositionStatus.CLOSED, PageRequest.of(0, safeLimit))
                .getContent());
    }
}
