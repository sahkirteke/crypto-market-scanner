package com.crypto.laplace.stop;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePaperTradeCoordinator;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplacePaperPositionEntity;
import com.crypto.laplace.persistence.LaplacePaperPositionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class LaplaceStopPersistenceService {
    private final LaplacePaperPositionRepository positions;
    private final LaplacePaperExecutionService execution;
    private final LaplacePaperTradeCoordinator coordinator;

    @Transactional
    public Result persist(LaplaceFiveMinuteStopService.StopPositionSnapshot snapshot,List<Kline> marketCandles,Instant now) {
        LaplacePaperPositionEntity position=positions.findByIdForUpdate(snapshot.positionId()).orElse(null);
        if(position==null||position.getStatus()!=LaplacePositionStatus.OPEN)return new Result(Outcome.ALREADY_CLOSED,null);
        if(!snapshot.symbol().equals(position.getSymbol())||snapshot.side()!=position.getSide())throw new IllegalStateException("STOP_POSITION_STATE_CONFLICT");
        ensureStopPrice(position);
        Instant entryOpen=LaplaceFiveMinuteStopService.entryCandleOpen(position);
        Instant checkpoint=position.getLastStopCheckedCandleOpenTime();
        List<Kline> closed=marketCandles.stream().filter(c->c!=null&&c.getOpenTime()!=null&&c.getCloseTime()!=null)
                .filter(c->!c.getCloseTime().isAfter(now)&&!Boolean.FALSE.equals(c.getClosed()))
                .filter(c->c.getOpenTime().isAfter(entryOpen))
                .filter(c->checkpoint==null||c.getOpenTime().isAfter(checkpoint))
                .sorted(Comparator.comparing(Kline::getOpenTime)).toList();
        for(Kline candle:closed){
            if(LaplaceFiveMinuteStopService.touchesStop(position,candle)){
                position.setLastStopCheckedCandleOpenTime(candle.getOpenTime());
                position.setLastChecked5mCandleCloseTime(candle.getCloseTime());
                BigDecimal price=LaplaceFiveMinuteStopService.historicalExecutionPrice(position,candle);
                var closedPosition=execution.closeByStop(position.getId(),candle,price,"FIVE_MINUTE_STOP_SIMULATION");
                if(closedPosition.isEmpty())return new Result(Outcome.ALREADY_CLOSED,candle.getOpenTime());
                var p=closedPosition.get();
                coordinator.onStopLossClosed(p.getSymbol(),p.getEntryRawSignal(),p.getSide().name(),p.getId(),p.getExitTime());
                return new Result(Outcome.STOP_CLOSED,candle.getOpenTime());
            }
        }
        if(!closed.isEmpty()){
            Kline last=closed.getLast();
            if(checkpoint==null||last.getOpenTime().isAfter(checkpoint)){
                position.setLastStopCheckedCandleOpenTime(last.getOpenTime());
                position.setLastChecked5mCandleCloseTime(last.getCloseTime());
                return new Result(Outcome.CHECKPOINT_ADVANCED,last.getOpenTime());
            }
        }
        return new Result(Outcome.NO_CHANGE,checkpoint);
    }

    private void ensureStopPrice(LaplacePaperPositionEntity p){if(p.getStopPrice()!=null||p.getEntryExecutionPrice()==null)return;p.setStopLossPct(new BigDecimal("0.055"));p.setStopPrice(p.getSide()==com.crypto.common.enums.PositionSide.LONG?p.getEntryExecutionPrice().multiply(new BigDecimal("0.945")):p.getEntryExecutionPrice().multiply(new BigDecimal("1.055")));}
    public enum Outcome { CHECKPOINT_ADVANCED, STOP_CLOSED, ALREADY_CLOSED, NO_CHANGE }
    public record Result(Outcome outcome,Instant checkpoint) {}
}
