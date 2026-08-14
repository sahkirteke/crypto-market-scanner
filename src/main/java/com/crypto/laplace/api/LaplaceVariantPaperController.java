package com.crypto.laplace.api;

import com.crypto.api.dto.LaplaceClosedPaperPositionResponse;
import com.crypto.api.dto.LaplaceOpenPaperPositionsResponse;
import com.crypto.api.dto.LaplaceVariantSummaryResponse;
import com.crypto.api.dto.LaplaceAnalysisSummaryResponse;
import com.crypto.laplace.persistence.LaplaceInvertedFalseSessionEntity;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import com.crypto.laplace.service.LaplaceRuntimeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/paper") @RequiredArgsConstructor
public class LaplaceVariantPaperController {
    private final LaplacePaperApiService trueApi;private final LaplaceInvertedFalseApiService falseApi;private final LaplaceRuntimeService trueRuntime;private final LaplaceInvertedFalseRuntimeService falseRuntime;
    @GetMapping("/inverted-true/positions/open") public LaplaceOpenPaperPositionsResponse trueOpen(){return trueApi.findOpenPositions();}
    @GetMapping("/inverted-false/positions/open") public LaplaceOpenPaperPositionsResponse falseOpen(){return falseApi.open();}
    @GetMapping("/inverted-false/positions/closed") public List<LaplaceClosedPaperPositionResponse> falseClosed(){return falseApi.closed();}
    @GetMapping("/inverted-true/positions/closed") public List<LaplaceClosedPaperPositionResponse> trueClosed(){return trueApi.findClosedPositions();}
    @GetMapping("/inverted-true/summary") public LaplaceAnalysisSummaryResponse trueSummary(){return trueApi.summary();}
    @GetMapping("/inverted-false/summary") public LaplaceVariantSummaryResponse falseSummary(){return falseApi.summary();}
    @GetMapping("/inverted-true/runtime-state") public LaplaceRuntimeStatusResponse trueRuntime(){return trueRuntime.status();}
    @GetMapping("/inverted-false/runtime-state") public LaplaceInvertedFalseSessionEntity falseRuntime(){return falseRuntime.current();}
}
