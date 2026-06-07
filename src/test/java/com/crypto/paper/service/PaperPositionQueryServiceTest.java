package com.crypto.paper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crypto.api.dto.PaperPositionResponse;
import com.crypto.api.dto.PaperTradeSummaryResponse;
import com.crypto.api.exception.ResourceNotFoundException;
import com.crypto.api.mapper.PaperPositionApiMapper;
import com.crypto.common.enums.PositionSide;
import com.crypto.paper.model.PaperPositionStatus;
import com.crypto.persistence.entity.PaperPositionEntity;
import com.crypto.persistence.repository.PaperPositionRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class PaperPositionQueryServiceTest {
    private PaperPositionRepository repository;
    private PaperPositionApiMapper mapper;
    private PaperPositionQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaperPositionRepository.class);
        mapper = mock(PaperPositionApiMapper.class);
        service = new PaperPositionQueryService(repository, mapper);
    }

    @Test
    void getOpenPositionsMapsOpenPositionsToResponses() {
        List<PaperPositionEntity> positions = List.of(position(PaperPositionStatus.OPEN, PositionSide.LONG, "1.5", "2.0"));
        List<PaperPositionResponse> responses = Collections.singletonList(null);
        when(repository.findByStatusOrderByOpenedAtDesc(PaperPositionStatus.OPEN)).thenReturn(positions);
        when(mapper.toResponseList(positions)).thenReturn(responses);

        assertThat(service.getOpenPositions()).isSameAs(responses);
        verify(mapper).toResponseList(positions);
    }

    @Test
    void getClosedPositionsLimitsResultToMaxTwoHundred() {
        when(repository.findByStatusOrderByClosedAtDesc(eq(PaperPositionStatus.CLOSED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toResponseList(List.of())).thenReturn(List.of());

        service.getClosedPositions(999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStatusOrderByClosedAtDesc(eq(PaperPositionStatus.CLOSED), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void getPositionDetailThrowsResourceNotFoundWhenMissing() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPositionDetail(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Paper position not found: 42");
    }

    @Test
    void getSymbolPositionsNormalizesLowercaseSymbolToUppercase() {
        when(repository.findBySymbolOrderByOpenedAtDesc(eq("HYPEUSDT"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(mapper.toResponseList(List.of())).thenReturn(List.of());

        service.getSymbolPositions("hypeusdt", 25);

        verify(repository).findBySymbolOrderByOpenedAtDesc(eq("HYPEUSDT"), any(Pageable.class));
    }

    @Test
    void getSummaryCalculatesWinLossWinRateAndTotalPnl() {
        when(repository.countByStatus(PaperPositionStatus.OPEN)).thenReturn(2L);
        when(repository.countByStatus(PaperPositionStatus.CLOSED)).thenReturn(3L);
        when(repository.countByStatusAndSide(PaperPositionStatus.OPEN, PositionSide.LONG)).thenReturn(1L);
        when(repository.countByStatusAndSide(PaperPositionStatus.OPEN, PositionSide.SHORT)).thenReturn(1L);
        when(repository.findByStatus(PaperPositionStatus.CLOSED)).thenReturn(List.of(
                position(PaperPositionStatus.CLOSED, PositionSide.LONG, "1.5", "3.0"),
                position(PaperPositionStatus.CLOSED, PositionSide.LONG, "-0.5", "-1.0"),
                position(PaperPositionStatus.CLOSED, PositionSide.LONG, "0", "0")
        ));

        PaperTradeSummaryResponse summary = service.getSummary();

        assertThat(summary.totalRealizedPnlUsdt()).isEqualByComparingTo("1.0");
        assertThat(summary.totalWinCount()).isEqualTo(1L);
        assertThat(summary.totalLossCount()).isEqualTo(1L);
        assertThat(summary.winRatePct()).isEqualByComparingTo("50");
        assertThat(summary.avgRealizedPnlPct()).isEqualByComparingTo("0.66666667");
    }

    private PaperPositionEntity position(PaperPositionStatus status, PositionSide side, String pnlUsdt, String pnlPct) {
        return PaperPositionEntity.builder()
                .symbol("HYPEUSDT")
                .status(status)
                .side(side)
                .realizedPnlUsdt(new BigDecimal(pnlUsdt))
                .realizedPnlPct(new BigDecimal(pnlPct))
                .build();
    }
}
