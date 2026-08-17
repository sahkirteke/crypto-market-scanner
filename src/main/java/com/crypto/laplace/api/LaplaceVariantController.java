package com.crypto.laplace.api;
import com.crypto.api.dto.LaplaceOpenPaperPositionsResponse;import com.crypto.laplace.model.LaplacePaperVariant;import com.crypto.laplace.service.*;import java.util.List;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/paper") @RequiredArgsConstructor
public class LaplaceVariantController {
 private final LaplaceVariantApiService api;private final LaplaceRuntimeService trueRuntime;
 @GetMapping("/inverted-true/positions/open")public LaplaceOpenPaperPositionsResponse trueOpen(){return api.open(LaplacePaperVariant.INVERTED_TRUE);}
 @GetMapping("/inverted-true/positions/closed")public List<LaplacePaperPositionResponse>trueClosed(){return api.closed(LaplacePaperVariant.INVERTED_TRUE);}
 @GetMapping("/inverted-true/summary")public LaplaceVariantSummaryResponse trueSummary(){return api.summary(LaplacePaperVariant.INVERTED_TRUE);}
 @GetMapping("/inverted-true/runtime-state")public LaplaceRuntimeStatusResponse trueState(){return trueRuntime.status();}
}
