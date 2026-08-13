package com.crypto.laplace.session;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record LaplaceSessionWindow(String sessionId, Instant startTime, Instant entryCutoffTime, Instant endTime) {
    public static LaplaceSessionWindow at(Instant now, int durationHours, int entryWindowHours) {
        if (durationHours <= 0 || 24 % durationHours != 0 || entryWindowHours < 0 || entryWindowHours > durationHours)
            throw new IllegalArgumentException("Invalid session configuration");
        Instant day = now.truncatedTo(ChronoUnit.DAYS);
        long elapsedHours = ChronoUnit.HOURS.between(day, now);
        Instant start = day.plus((elapsedHours / durationHours) * durationHours, ChronoUnit.HOURS);
        return new LaplaceSessionWindow(start.toString(), start, start.plus(entryWindowHours, ChronoUnit.HOURS),
                start.plus(durationHours, ChronoUnit.HOURS));
    }

    public boolean acceptsEntryAt(Instant now) { return now.isBefore(entryCutoffTime); }
}
