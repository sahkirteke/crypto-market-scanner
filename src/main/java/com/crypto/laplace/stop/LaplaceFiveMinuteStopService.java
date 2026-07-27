package com.crypto.laplace.stop;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class LaplaceFiveMinuteStopService {
    private static final int MAX_CANDLES=40;
    private static final long FIVE_MINUTE_SECONDS=300;
    private final BinanceFuturesClient client;
    private final LaplacePaperPositionRepository positions;
    private final LaplaceStopPersistenceService persistence;
    private final LaplacePaperExecutionService execution;

    public void checkOpenPositions(){
        Instant started=Instant.now();List<StopPositionSnapshot> open=snapshots();
        Set<String> marketErrors=new TreeSet<>(),checkpointConflicts=new TreeSet<>(),closeErrors=new TreeSet<>(),closed=new TreeSet<>(),advanced=new TreeSet<>(),alreadyClosed=new TreeSet<>();
        for(StopPositionSnapshot snapshot:open){
            List<Kline> candles;
            try{candles=client.getKlines(snapshot.symbol(),"5m",MAX_CANDLES);}
            catch(RuntimeException error){marketErrors.add(snapshot.symbol());log.error("LAPLACE_STOP_MARKET_DATA_LOAD_FAILED symbol={} positionId={}",snapshot.symbol(),snapshot.positionId(),error);continue;}
            boolean stopCandidate=containsStop(snapshot,candles,Instant.now());
            try{classify(snapshot,persistence.persist(snapshot,candles,Instant.now()),closed,advanced,alreadyClosed);}
            catch(ObjectOptimisticLockingFailureException conflict){checkpointConflicts.add(snapshot.symbol());log.error("LAPLACE_STOP_OPTIMISTIC_LOCK_CONFLICT symbol={} positionId={}",snapshot.symbol(),snapshot.positionId(),conflict);}
            catch(RuntimeException error){if(stopCandidate){closeErrors.add(snapshot.symbol());log.error("LAPLACE_STOP_CLOSE_FAILED symbol={} positionId={}",snapshot.symbol(),snapshot.positionId(),error);}else{checkpointConflicts.add(snapshot.symbol());log.error("LAPLACE_STOP_CHECKPOINT_PERSIST_FAILED symbol={} positionId={}",snapshot.symbol(),snapshot.positionId(),error);}}
        }
        if(!closed.isEmpty())execution.drainOutbox();
        log.info("LAPLACE_5M_STOP_CHECK openPositions={} symbols={} marketDataErrorSymbols={} checkpointConflictSymbols={} closeErrorSymbols={} stopClosedSymbols={} checkpointAdvancedSymbols={} alreadyClosedSymbols={} startedAt={} finishedAt={}",open.size(),open.stream().map(StopPositionSnapshot::symbol).distinct().count(),marketErrors,checkpointConflicts,closeErrors,closed,advanced,alreadyClosed,started,Instant.now());
    }

    public void catchUpOpenPositions(){
        Instant now=Instant.now();
        for(StopPositionSnapshot initial:snapshots()){
            StopPositionSnapshot snapshot=initial;
            Instant cursor=snapshot.lastStopCheckedCandleOpenTime()==null?entryCandleOpen(snapshot.entryTime()).plusSeconds(FIVE_MINUTE_SECONDS):snapshot.lastStopCheckedCandleOpenTime().plusSeconds(FIVE_MINUTE_SECONDS);
            while(cursor.isBefore(now)){
                List<Kline> batch;
                try{batch=client.getKlines(snapshot.symbol(),"5m",MAX_CANDLES,cursor);}
                catch(RuntimeException error){log.error("LAPLACE_STOP_MARKET_DATA_LOAD_FAILED symbol={} positionId={} catchUp=true",snapshot.symbol(),snapshot.positionId(),error);break;}
                LaplaceStopPersistenceService.Result result;
                boolean stopCandidate=containsStop(snapshot,batch,now);
                try{result=persistence.persist(snapshot,batch,now);}
                catch(ObjectOptimisticLockingFailureException conflict){log.error("LAPLACE_STOP_OPTIMISTIC_LOCK_CONFLICT symbol={} positionId={} catchUp=true",snapshot.symbol(),snapshot.positionId(),conflict);break;}
                catch(RuntimeException error){log.error(stopCandidate?"LAPLACE_STOP_CLOSE_FAILED symbol={} positionId={} catchUp=true":"LAPLACE_STOP_CHECKPOINT_PERSIST_FAILED symbol={} positionId={} catchUp=true",snapshot.symbol(),snapshot.positionId(),error);break;}
                if(result.outcome()==LaplaceStopPersistenceService.Outcome.STOP_CLOSED){execution.drainOutbox();break;}
                if(result.outcome()==LaplaceStopPersistenceService.Outcome.ALREADY_CLOSED||batch.size()<MAX_CANDLES)break;
                Instant next=result.checkpoint()==null?cursor:result.checkpoint().plusSeconds(FIVE_MINUTE_SECONDS);
                if(!next.isAfter(cursor))break;
                cursor=next;snapshot=snapshot.withCheckpoint(result.checkpoint());
            }
        }
    }

    private void classify(StopPositionSnapshot s,LaplaceStopPersistenceService.Result r,Set<String> closed,Set<String> advanced,Set<String> already){switch(r.outcome()){case STOP_CLOSED->closed.add(s.symbol());case CHECKPOINT_ADVANCED->advanced.add(s.symbol());case ALREADY_CLOSED->already.add(s.symbol());case NO_CHANGE->{}}}
    private boolean containsStop(StopPositionSnapshot s,List<Kline> candles,Instant now){BigDecimal stop=s.stopPrice()!=null?s.stopPrice():(s.side()==PositionSide.LONG?s.entryExecutionPrice().multiply(new BigDecimal("0.945")):s.entryExecutionPrice().multiply(new BigDecimal("1.055")));Instant entryOpen=entryCandleOpen(s.entryTime());return candles.stream().filter(c->c!=null&&c.getOpenTime()!=null&&c.getCloseTime()!=null&&!c.getCloseTime().isAfter(now)&&!Boolean.FALSE.equals(c.getClosed())&&c.getOpenTime().isAfter(entryOpen)&&(s.lastStopCheckedCandleOpenTime()==null||c.getOpenTime().isAfter(s.lastStopCheckedCandleOpenTime()))).anyMatch(c->s.side()==PositionSide.LONG?c.getLow().compareTo(stop)<=0:c.getHigh().compareTo(stop)>=0);}
    static boolean touchesStop(LaplacePaperPositionEntity p,Kline c){return p.getSide()==PositionSide.LONG?c.getLow().compareTo(p.getStopPrice())<=0:c.getHigh().compareTo(p.getStopPrice())>=0;}
    static BigDecimal historicalExecutionPrice(LaplacePaperPositionEntity p,Kline c){if(p.getSide()==PositionSide.LONG)return c.getOpen().compareTo(p.getStopPrice())<0?c.getOpen():p.getStopPrice();return c.getOpen().compareTo(p.getStopPrice())>0?c.getOpen():p.getStopPrice();}
    static Instant entryCandleOpen(LaplacePaperPositionEntity p){return entryCandleOpen(p.getEntryTime());}
    static Instant entryCandleOpen(Instant entry){long epoch=entry.getEpochSecond();return Instant.ofEpochSecond((epoch/FIVE_MINUTE_SECONDS)*FIVE_MINUTE_SECONDS);}
    private List<StopPositionSnapshot> snapshots(){return positions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN).stream().map(StopPositionSnapshot::from).toList();}
    public record StopPositionSnapshot(String positionId,String symbol,PositionSide side,BigDecimal entryExecutionPrice,BigDecimal stopPrice,Instant entryTime,Instant lastStopCheckedCandleOpenTime){static StopPositionSnapshot from(LaplacePaperPositionEntity p){return new StopPositionSnapshot(p.getId(),p.getSymbol(),p.getSide(),p.getEntryExecutionPrice(),p.getStopPrice(),p.getEntryTime(),p.getLastStopCheckedCandleOpenTime());}StopPositionSnapshot withCheckpoint(Instant checkpoint){return new StopPositionSnapshot(positionId,symbol,side,entryExecutionPrice,stopPrice,entryTime,checkpoint);}}
}
