package com.crypto.laplace.api;
import com.crypto.api.dto.LaplaceOpenPaperPositionsResponse;import com.crypto.laplace.model.LaplacePaperVariant;import com.crypto.laplace.service.*;import java.util.List;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/paper") @RequiredArgsConstructor
public class LaplaceVariantController {
 private final LaplaceVariantApiService api;private final LaplaceRuntimeService trueRuntime;private final LaplaceInvertedFalseRuntimeService falseRuntime;
 @GetMapping("/inverted-true/positions/open")public LaplaceOpenPaperPositionsResponse trueOpen(){return api.open(LaplacePaperVariant.INVERTED_TRUE);}
 @GetMapping("/inverted-false/positions/open")public LaplaceOpenPaperPositionsResponse falseOpen(){return api.open(LaplacePaperVariant.INVERTED_FALSE);}
 @GetMapping("/inverted-true/positions/closed")public List<LaplacePaperPositionResponse>trueClosed(){return api.closed(LaplacePaperVariant.INVERTED_TRUE);}
 @GetMapping("/inverted-false/positions/closed")public List<LaplacePaperPositionResponse>falseClosed(){return api.closed(LaplacePaperVariant.INVERTED_FALSE);}
 @GetMapping("/inverted-true/summary")public LaplaceVariantSummaryResponse trueSummary(){return api.summary(LaplacePaperVariant.INVERTED_TRUE);}
 @GetMapping("/inverted-false/summary")public LaplaceVariantSummaryResponse falseSummary(){return api.summary(LaplacePaperVariant.INVERTED_FALSE);}
 @GetMapping("/inverted-true/runtime-state")public LaplaceRuntimeStatusResponse trueState(){return trueRuntime.status();}
 @GetMapping("/inverted-false/runtime-state")public LaplaceRuntimeStatusResponse falseState(){return falseRuntime.status();}
}
