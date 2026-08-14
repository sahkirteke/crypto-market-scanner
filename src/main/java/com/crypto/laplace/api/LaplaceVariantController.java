package com.crypto.laplace.api;

import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import com.crypto.laplace.service.LaplaceRuntimeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class LaplaceVariantController {
    private final LaplaceVariantApiService api;
    private final LaplaceRuntimeService invertedTrueRuntime;
    private final LaplaceInvertedFalseRuntimeService invertedFalseRuntime;

    @GetMapping("/inverted-true/positions/open") public List<LaplacePaperPositionResponse> trueOpen() { return api.positions(true, LaplacePositionStatus.OPEN); }
    @GetMapping("/inverted-false/positions/open") public List<LaplacePaperPositionResponse> falseOpen() { return api.positions(false, LaplacePositionStatus.OPEN); }
    @GetMapping("/inverted-true/positions/closed") public List<LaplacePaperPositionResponse> trueClosed() { return api.positions(true, LaplacePositionStatus.CLOSED); }
    @GetMapping("/inverted-false/positions/closed") public List<LaplacePaperPositionResponse> falseClosed() { return api.positions(false, LaplacePositionStatus.CLOSED); }
    @GetMapping("/inverted-true/summary") public LaplaceVariantSummaryResponse trueSummary() { return api.summary(true); }
    @GetMapping("/inverted-false/summary") public LaplaceVariantSummaryResponse falseSummary() { return api.summary(false); }
    @GetMapping("/inverted-true/runtime-state") public LaplaceRuntimeStatusResponse trueRuntime() { return invertedTrueRuntime.status(); }
    @GetMapping("/inverted-false/runtime-state") public LaplaceRuntimeStatusResponse falseRuntime() { return invertedFalseRuntime.status(); }
}
