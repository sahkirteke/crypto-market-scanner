package com.crypto.system.dto;

import java.time.Instant;

public record SystemStatusResponse(
        String status,
        Instant time,
        Boolean schedulerEnabled,
        Boolean paperAutoEnabled,
        Boolean paperAutoOpenAfterScan,
        Boolean paperAutoEvaluateEnabled,
        String paperAutoEvaluateCron,
        Boolean paperEnabled,
        Boolean paperExitEnabled,
        Boolean safeMode,
        String timezone,
        String activeProfiles,
        String appName
) {
}
