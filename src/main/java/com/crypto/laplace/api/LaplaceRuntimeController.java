package com.crypto.laplace.api;

import com.crypto.laplace.service.LaplaceRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/laplace/runtime")
@RequiredArgsConstructor
public class LaplaceRuntimeController {
    private final LaplaceRuntimeService runtime;

    @GetMapping
    public LaplaceRuntimeStatusResponse status() {
        return runtime.status();
    }
}
