package com.crypto.laplace.execution;

import java.time.Instant;
import java.time.LocalDate;

public record LaplaceVolumeProfileDecision(
        LocalDate previousNySessionDate, Instant previousNySessionStart, Instant previousNySessionEnd,
        double previousSessionLow, double previousSessionHigh, double previousSessionPoc, int volumeProfileRows,
        double entryPrice, boolean entryInsidePreviousSessionRange, Double localDeltaRatio, Double currentDelta60m,
        boolean insideProfileLongAllowed, boolean insideProfileShortAllowed,
        boolean outsideProfileLongAllowed, boolean outsideProfileShortAllowed,
        boolean volumeProfileAllowed, VolumeProfileRejectionReason rejectionReason) {
}
