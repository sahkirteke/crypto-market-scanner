package com.crypto.system.controller;

import com.crypto.system.dto.DbConsistencyResponse;
import com.crypto.system.dto.SystemStatusResponse;
import com.crypto.system.service.SystemStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
public class SystemStatusController {
    private final SystemStatusService systemStatusService;

    @GetMapping("/status")
    public SystemStatusResponse getStatus() {
        SystemStatusResponse response = systemStatusService.getStatus();
        log.info(
                "SYSTEM_STATUS_REQUEST status={} safeMode={} schedulerEnabled={} profiles={}",
                response.status(),
                response.safeMode(),
                response.schedulerEnabled(),
                response.activeProfiles()
        );
        return response;
    }

    @GetMapping("/db-consistency")
    public DbConsistencyResponse getDbConsistency() {
        log.info("SYSTEM_DB_CONSISTENCY_REQUEST");
        return systemStatusService.getDbConsistency();
    }
}
