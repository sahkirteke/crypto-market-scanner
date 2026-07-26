package com.crypto.laplace.audit;

import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class VolumeScanAuditService {
    public static final ZoneId ZONE = ZoneId.of("America/New_York");
    private final VolumeScanAuditOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public record Run(String id, LocalDate date, OffsetDateTime startedAt) {}

    public Run newRun() {
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        return new Run("volume-scan-" + now, now.toLocalDate(), now);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void started(Run run, int candidates) {
        Map<String,Object> p = base(run, "SCAN_STARTED");
        p.put("startedAt", run.startedAt()); p.put("candidateSymbolCount", candidates);
        p.put("strategyVersion", LaplacePaperExecutionService.VERSION);
        persist(run, "SCAN_STARTED", null, p);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void coin(Run run, VolumeScanCoinEvaluation e) {
        Map<String,Object> p = base(run, "COIN_EVALUATED");
        p.put("scanStartedAt", run.startedAt()); p.put("evaluatedAt", e.evaluatedAt());
        p.put("symbol", e.symbol()); p.put("previousStatus", e.previousStatus()); p.put("newStatus", e.newStatus());
        p.put("transition", e.transition()); p.put("eligible", e.eligible()); p.put("tradable", true);
        p.put("includedInPool", e.includedInPool()); p.put("primaryReasonCode", e.primaryReasonCode());
        p.put("reasonCodes", e.reasonCodes()); p.put("failedRules", e.failedRules()); p.put("passedRules", e.passedRules());
        p.put("quoteVolume24h", e.quoteVolume24h()); p.put("previousVolume24h", e.previousVolume24h());
        p.put("volumeChangePct", e.volumeChangePct()); p.put("volumeRank", e.volumeRank()); p.put("lastPrice", e.lastPrice());
        p.put("priceChange24hPct", e.priceChange24hPct()); p.put("appliedEntryMinVolume", 25_000_000);
        p.put("appliedRetentionMinVolume", 20_000_000); p.put("dataComplete", e.dataComplete());
        p.put("dataSource", "BINANCE_FUTURES"); p.put("evaluationVersion", LaplacePaperExecutionService.VERSION);
        p.put("positionOpen", e.positionOpen()); p.put("reentryBlocked", e.reentryBlocked());
        p.put("errorCode", e.errorCode()); p.put("errorMessage", e.errorMessage());
        persist(run, "COIN_EVALUATED", e.symbol(), p);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void completed(Run run, List<VolumeScanCoinEvaluation> coins) {
        OffsetDateTime completed = OffsetDateTime.now(ZONE);
        Map<String,Object> p = base(run, "SCAN_COMPLETED");
        p.put("startedAt", run.startedAt()); p.put("completedAt", completed);
        p.put("durationMs", Duration.between(run.startedAt(), completed).toMillis()); p.put("candidateCount", coins.size());
        p.put("evaluatedCount", coins.size());
        for (VolumeScanAuditStatus s : VolumeScanAuditStatus.values()) p.put(key(s) + "Count", countStatus(coins,s));
        p.put("enteredPoolCount", countTransition(coins, VolumeScanTransition.ENTERED_POOL));
        p.put("reactivatedCount", countTransition(coins, VolumeScanTransition.REACTIVATED));
        p.put("remainedActiveCount", countTransition(coins, VolumeScanTransition.REMAINED_ACTIVE));
        p.put("movedToWatchlistCount", countTransition(coins, VolumeScanTransition.MOVED_TO_WATCHLIST));
        p.put("eliminatedFromPoolCount", countTransition(coins, VolumeScanTransition.ELIMINATED_FROM_POOL));
        for (VolumeScanAuditStatus s : VolumeScanAuditStatus.values()) p.put(key(s) + "Symbols", symbols(coins,s));
        p.put("success", true); persist(run, "SCAN_COMPLETED", null, p);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(Run run, Throwable error, int processed) {
        Map<String,Object> p = base(run, "SCAN_FAILED"); p.put("startedAt", run.startedAt());
        p.put("failedAt", OffsetDateTime.now(ZONE)); p.put("errorType", "BINANCE_API_ERROR");
        p.put("errorMessage", error.getMessage()); p.put("processedSymbolCount", processed); p.put("success", false);
        persist(run, "SCAN_FAILED", null, p);
    }

    public static VolumeScanTransition transition(VolumeScanAuditStatus oldStatus, VolumeScanAuditStatus next) {
        if (next == VolumeScanAuditStatus.NOT_EVALUATED) return VolumeScanTransition.NOT_EVALUATED;
        if (oldStatus == VolumeScanAuditStatus.ELIMINATED && next != VolumeScanAuditStatus.ELIMINATED) return VolumeScanTransition.REACTIVATED;
        if (oldStatus == null) return next == VolumeScanAuditStatus.ELIMINATED ? VolumeScanTransition.REMAINED_ELIMINATED : VolumeScanTransition.ENTERED_POOL;
        if (oldStatus == VolumeScanAuditStatus.ACTIVE && next == VolumeScanAuditStatus.ELIMINATED) return VolumeScanTransition.ELIMINATED_FROM_POOL;
        if (oldStatus == VolumeScanAuditStatus.ACTIVE && next == VolumeScanAuditStatus.WATCHLIST) return VolumeScanTransition.MOVED_TO_WATCHLIST;
        if (oldStatus == VolumeScanAuditStatus.WATCHLIST && next == VolumeScanAuditStatus.ACTIVE) return VolumeScanTransition.ENTERED_POOL;
        if (next == VolumeScanAuditStatus.ACTIVE) return VolumeScanTransition.REMAINED_ACTIVE;
        if (next == VolumeScanAuditStatus.WATCHLIST) return VolumeScanTransition.REMAINED_WATCHLIST;
        return VolumeScanTransition.REMAINED_ELIMINATED;
    }

    private Map<String,Object> base(Run run,String type) { Map<String,Object> p=new LinkedHashMap<>(); p.put("eventType",type);p.put("scanRunId",run.id());p.put("scanDate",run.date());p.put("timezone",ZONE.getId());return p; }
    public void persist(Run run,String type,String symbol,Map<String,Object> payload) {
        String key=run.id()+":"+type+":"+(symbol==null?"RUN":symbol);
        if(repository.existsByIdempotencyKey(key)) return;
        String eventId=UUID.randomUUID().toString(); payload.put("auditEventId",eventId); payload.put("idempotencyKey",key);
        try { repository.save(VolumeScanAuditOutboxEntity.builder().eventId(eventId).idempotencyKey(key).scanRunId(run.id()).eventType(type).symbol(symbol).payloadJson(objectMapper.writeValueAsString(payload)).jsonlWritten(false).createdAt(Instant.now()).build()); }
        catch(DataIntegrityViolationException duplicate) { /* Concurrent retry already persisted this idempotency key. */ }
        catch(JsonProcessingException e){ throw new IllegalStateException("VOLUME_SCAN_AUDIT_SERIALIZATION_FAILED",e); }
    }
    private static String key(VolumeScanAuditStatus s){return switch(s){case ACTIVE->"active";case WATCHLIST->"watchlist";case ELIMINATED->"eliminated";case NOT_EVALUATED->"notEvaluated";};}
    private static long countStatus(List<VolumeScanCoinEvaluation> c,VolumeScanAuditStatus s){return c.stream().filter(x->x.newStatus()==s).count();}
    private static long countTransition(List<VolumeScanCoinEvaluation> c,VolumeScanTransition t){return c.stream().filter(x->x.transition()==t).count();}
    private static List<String> symbols(List<VolumeScanCoinEvaluation> c,VolumeScanAuditStatus s){return c.stream().filter(x->x.newStatus()==s).map(VolumeScanCoinEvaluation::symbol).sorted().toList();}
}
