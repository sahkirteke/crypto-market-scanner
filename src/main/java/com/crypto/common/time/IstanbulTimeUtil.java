package com.crypto.common.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class IstanbulTimeUtil {
    public static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    public static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'TRT'")
                    .withZone(ISTANBUL_ZONE);

    private IstanbulTimeUtil() {
    }

    public static String format(Instant instant) {
        return instant == null ? null : DISPLAY_FORMATTER.format(instant);
    }

    public static Instant nowInstant() {
        return Instant.now();
    }

    public static String nowText() {
        return format(nowInstant());
    }
}
