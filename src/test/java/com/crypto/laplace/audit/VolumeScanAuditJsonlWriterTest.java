package com.crypto.laplace.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VolumeScanAuditJsonlWriterTest {
    @TempDir Path temp;

    @Test void failedWriteRemainsPendingAndNextDrainAppendsValidJsonLine() throws Exception {
        VolumeScanAuditOutboxRepository repository=mock(VolumeScanAuditOutboxRepository.class);
        var row=VolumeScanAuditOutboxEntity.builder().eventId("id").idempotencyKey("key").scanRunId("run").eventType("SCAN_STARTED").payloadJson("{\"eventType\":\"SCAN_STARTED\"}").createdAt(Instant.now()).build();
        when(repository.findTop500ByJsonlWrittenFalseOrderByCreatedAtAsc()).thenReturn(List.of(row));
        LaplaceStrategyProperties properties=new LaplaceStrategyProperties();
        properties.getLaplace().getVolumeScan().setAuditJsonlPath(temp.toString());
        var writer=new VolumeScanAuditJsonlWriter(repository,properties);
        writer.drain();
        assertThat(row.isJsonlWritten()).isFalse(); verify(repository,never()).save(row);

        Path jsonl=temp.resolve("audit.jsonl"); properties.getLaplace().getVolumeScan().setAuditJsonlPath(jsonl.toString());
        writer.drain();
        assertThat(row.isJsonlWritten()).isTrue(); verify(repository).save(row);
        List<String> lines=Files.readAllLines(jsonl);
        assertThat(lines).hasSize(1);
        assertThat(new ObjectMapper().readTree(lines.get(0)).get("eventType").asText()).isEqualTo("SCAN_STARTED");
    }
}
