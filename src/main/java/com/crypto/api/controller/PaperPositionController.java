package com.crypto.api.controller;

import com.crypto.api.dto.PaperPositionResponse;
import com.crypto.api.mapper.PaperPositionApiMapper;
import com.crypto.paper.service.PaperPositionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class PaperPositionController {
    private final PaperPositionService paperPositionService;
    private final PaperPositionApiMapper paperPositionApiMapper;

    @PostMapping("/open-from-latest-signals")
    public List<PaperPositionResponse> openFromLatestSignals() {
        return paperPositionApiMapper.toResponseList(paperPositionService.openPositionsFromLatestSignals());
    }

    @GetMapping("/positions/open")
    public List<PaperPositionResponse> getOpenPositions() {
        return paperPositionApiMapper.toResponseList(paperPositionService.getOpenPositions());
    }
}
