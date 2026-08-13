package com.crypto.laplace.session;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LaplaceSessionWindowTest {
 @Test void utcMidnightWindowEndsAtNoon(){var w=LaplaceSessionWindow.at(Instant.parse("2026-08-13T09:00:00Z"),12,10);assertThat(w.startTime()).isEqualTo("2026-08-13T00:00:00Z");assertThat(w.entryCutoffTime()).isEqualTo("2026-08-13T10:00:00Z");assertThat(w.endTime()).isEqualTo("2026-08-13T12:00:00Z");}
 @Test void utcNoonWindowEndsAtNextMidnight(){var w=LaplaceSessionWindow.at(Instant.parse("2026-08-13T12:00:00Z"),12,10);assertThat(w.startTime()).isEqualTo("2026-08-13T12:00:00Z");assertThat(w.endTime()).isEqualTo("2026-08-14T00:00:00Z");}
 @Test void exactCutoffIsClosed(){var w=LaplaceSessionWindow.at(Instant.parse("2026-08-13T00:00:00Z"),12,10);assertThat(w.acceptsEntryAt(Instant.parse("2026-08-13T09:59:59Z"))).isTrue();assertThat(w.acceptsEntryAt(Instant.parse("2026-08-13T10:00:00Z"))).isFalse();}
}
