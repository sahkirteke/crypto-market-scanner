package com.crypto.laplace.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VolumeScanAuditServiceTest {
    @Test void newYorkVolumeScanConfigurationKeepsTenAmAcrossDstOffsets() {
        com.crypto.laplace.config.LaplaceStrategyProperties properties=new com.crypto.laplace.config.LaplaceStrategyProperties();
        assertThat(properties.getLaplace().getVolumeScan().getCron()).isEqualTo("5 0 10 * * *");
        assertThat(properties.getLaplace().getVolumeScan().getTimezone()).isEqualTo("America/New_York");
        ZonedDateTime winter=ZonedDateTime.of(2026,1,15,10,0,5,0,VolumeScanAuditService.ZONE);
        ZonedDateTime summer=ZonedDateTime.of(2026,7,15,10,0,5,0,VolumeScanAuditService.ZONE);
        assertThat(winter.getHour()).isEqualTo(10); assertThat(summer.getHour()).isEqualTo(10);
        assertThat(winter.getOffset()).isNotEqualTo(summer.getOffset());
    }

    @Test void transitionsCoverPoolEntryRemovalAndReactivation() {
        assertThat(VolumeScanAuditService.transition(null,VolumeScanAuditStatus.WATCHLIST)).isEqualTo(VolumeScanTransition.ENTERED_POOL);
        assertThat(VolumeScanAuditService.transition(VolumeScanAuditStatus.ACTIVE,VolumeScanAuditStatus.ELIMINATED)).isEqualTo(VolumeScanTransition.ELIMINATED_FROM_POOL);
        assertThat(VolumeScanAuditService.transition(VolumeScanAuditStatus.ELIMINATED,VolumeScanAuditStatus.WATCHLIST)).isEqualTo(VolumeScanTransition.REACTIVATED);
        assertThat(VolumeScanAuditService.transition(VolumeScanAuditStatus.ACTIVE,VolumeScanAuditStatus.NOT_EVALUATED)).isEqualTo(VolumeScanTransition.NOT_EVALUATED);
    }

    @Test void writesOneStartedCoinAndConsistentCompletedSummaryWithIdempotencyKeys() throws Exception {
        VolumeScanAuditOutboxRepository repository=mock(VolumeScanAuditOutboxRepository.class);
        when(repository.existsByIdempotencyKey(any())).thenReturn(false);
        VolumeScanAuditService service=new VolumeScanAuditService(repository,new ObjectMapper().findAndRegisterModules(),new com.crypto.laplace.config.LaplaceStrategyProperties());
        var run=new VolumeScanAuditService.Run("volume-scan-test",LocalDate.of(2026,7,27),OffsetDateTime.parse("2026-07-27T10:00:05-04:00"));
        var coin=new VolumeScanCoinEvaluation("BTCUSDT",VolumeScanAuditStatus.WATCHLIST,VolumeScanAuditStatus.ACTIVE,VolumeScanTransition.ENTERED_POOL,true,true,"VOLUME_CRITERIA_PASSED",List.of("VOLUME_CRITERIA_PASSED"),List.of(),List.of("MIN_VOLUME"),run.startedAt(),new java.math.BigDecimal("26000000"),new java.math.BigDecimal("24000000"),new java.math.BigDecimal("8.333333"),1,new java.math.BigDecimal("100"),new java.math.BigDecimal("2"),false,false,true,null,null);
        service.started(run,1); service.coin(run,coin); service.completed(run,List.of(coin));
        ArgumentCaptor<VolumeScanAuditOutboxEntity> captor=ArgumentCaptor.forClass(VolumeScanAuditOutboxEntity.class);
        verify(repository,times(3)).save(captor.capture());
        List<VolumeScanAuditOutboxEntity> rows=captor.getAllValues();
        assertThat(rows).extracting(VolumeScanAuditOutboxEntity::getEventType).containsExactly("SCAN_STARTED","COIN_EVALUATED","SCAN_COMPLETED");
        Map<?,?> completed=new ObjectMapper().findAndRegisterModules().readValue(rows.get(2).getPayloadJson(),Map.class);
        assertThat(completed.get("evaluatedCount")).isEqualTo(1);
        assertThat(completed.get("activeCount")).isEqualTo(1);
        assertThat(rows.get(1).getIdempotencyKey()).isEqualTo("volume-scan-test:COIN_EVALUATED:BTCUSDT");
    }

    @Test void duplicateRunCoinIsNotInserted() {
        VolumeScanAuditOutboxRepository repository=mock(VolumeScanAuditOutboxRepository.class);
        when(repository.existsByIdempotencyKey("run:COIN_EVALUATED:BTCUSDT")).thenReturn(true);
        VolumeScanAuditService service=new VolumeScanAuditService(repository,new ObjectMapper(),new com.crypto.laplace.config.LaplaceStrategyProperties());
        service.persist(new VolumeScanAuditService.Run("run",LocalDate.now(),OffsetDateTime.now()),"COIN_EVALUATED","BTCUSDT",new LinkedHashMap<>());
        verify(repository,never()).save(any());
    }
}
