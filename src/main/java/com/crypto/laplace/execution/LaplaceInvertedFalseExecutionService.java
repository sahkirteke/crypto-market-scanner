package com.crypto.laplace.execution;

import com.crypto.common.enums.PositionSide;
import com.crypto.laplace.config.LaplaceStrategyProperties;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.model.LaplaceSignalResult;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionEntity;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionRepository;
import com.crypto.laplace.persistence.LaplaceInvertedFalseTradeEventEntity;
import com.crypto.laplace.persistence.LaplaceInvertedFalseTradeEventRepository;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class LaplaceInvertedFalseExecutionService {
    private static final int SCALE=12;
    private final LaplaceInvertedFalsePositionRepository positions;
    private final LaplaceInvertedFalseTradeEventRepository events;
    private final LaplaceExecutionPriceProvider prices;
    private final LaplacePnlCalculator pnl;
    private final LaplaceStrategyProperties properties;
    private final LaplaceInvertedFalseRuntimeService runtime;
    private final LaplaceInvertedFalseJsonlWriter writer;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public LaplaceInvertedFalsePositionEntity open(LaplaceSignalResult signal, PositionSide side) {
        if(!runtime.isActive()) throw new IllegalStateException("LAPLACE_FALSE_RUNTIME_NOT_ACTIVE");
        if(!positions.findByStrategyAndSymbolAndStatus(LaplacePaperExecutionService.STRATEGY,signal.symbol(),LaplacePositionStatus.OPEN).isEmpty()) throw new IllegalStateException("POSITION_STATE_CONFLICT");
        var session=runtime.current(); var action=side==PositionSide.LONG?MarketExecutionAction.LONG_OPEN:MarketExecutionAction.SHORT_OPEN;
        var quote=prices.quote(signal.symbol(),action); BigDecimal notional=session.getMarginPerPosition().multiply(BigDecimal.valueOf(session.getLeverage()));
        BigDecimal quantity=notional.divide(quote.value(),SCALE,RoundingMode.DOWN);BigDecimal fee=notional.multiply(properties.getLaplace().getTakerFeeRate()).setScale(SCALE,RoundingMode.HALF_UP);
        var position=LaplaceInvertedFalsePositionEntity.builder().id(UUID.randomUUID().toString()).strategy(LaplacePaperExecutionService.STRATEGY).strategyVersion(LaplacePaperExecutionService.VERSION)
                .symbol(signal.symbol()).side(side).status(LaplacePositionStatus.OPEN).entrySignalId(LaplacePaperExecutionService.entryKey(signal,side)+":FALSE")
                .sessionId(session.getSessionId()).entryRawSignal(signal.entrySignal().name()).signalInverted(false).entryCandleCloseTime(signal.signalCandleCloseTime())
                .entryTime(clock.instant()).entrySignalClosePrice(BigDecimal.valueOf(signal.signalCandleClose())).entryExecutionPrice(quote.value())
                .margin(session.getMarginPerPosition()).quantity(quantity).notional(notional).leverage(session.getLeverage())
                .entryFeeRate(properties.getLaplace().getTakerFeeRate()).entryFee(fee).build();positions.saveAndFlush(position);
        event("ENTRY",position,signal,quote);return position;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void close(LaplaceInvertedFalsePositionEntity position, LaplaceSignalResult signal) {
        var current=positions.findByIdForUpdate(position.getId()).orElseThrow();if(current.getStatus()!=LaplacePositionStatus.OPEN)return;
        var action=current.getSide()==PositionSide.LONG?MarketExecutionAction.LONG_CLOSE:MarketExecutionAction.SHORT_CLOSE;var quote=prices.quote(current.getSymbol(),action);
        BigDecimal exitFee=current.getQuantity().multiply(quote.value()).multiply(current.getEntryFeeRate()).setScale(SCALE,RoundingMode.HALF_UP);
        var result=pnl.calculate(current.getSide(),current.getEntryExecutionPrice(),quote.value(),current.getQuantity(),current.getNotional(),current.getEntryFee(),exitFee);
        current.setStatus(LaplacePositionStatus.CLOSED);current.setExitTime(clock.instant());current.setExitExecutionPrice(quote.value());current.setExitFee(exitFee);current.setGrossPnl(result.gross());current.setGrossPnlPct(result.grossPct());current.setNetPnl(result.net());current.setNetPnlPct(result.netPct());current.setHoldingMinutes(Duration.between(current.getEntryTime(),clock.instant()).toMinutes());current.setExitReason("OPPOSITE_CONFIRMED_LAPLACE_SIGNAL");current.setExitSignalId(LaplacePaperExecutionService.reversalKey(signal,current.getSide())+":FALSE");positions.saveAndFlush(current);event("EXIT",current,signal,quote);
    }
    private void event(String type,LaplaceInvertedFalsePositionEntity p,LaplaceSignalResult signal,LaplaceExecutionPriceProvider.Price quote){try{String id=UUID.randomUUID().toString();Map<String,Object> m=new LinkedHashMap<>();m.put("eventId",id);m.put("eventType",type);m.put("paperVariant","INVERTED_FALSE");m.put("signalInverted",false);m.put("rawEntrySignal",signal.entrySignal());m.put("effectiveExecutionSide",p.getSide());m.put("positionId",p.getId());m.put("sessionId",p.getSessionId());m.put("symbol",p.getSymbol());m.put("executionPrice",quote.value());events.save(LaplaceInvertedFalseTradeEventEntity.builder().eventId(id).eventType(type).positionId(p.getId()).symbol(p.getSymbol()).payloadJson(mapper.writeValueAsString(m)).jsonlWritten(false).createdAt(clock.instant()).build());writer.drain();}catch(Exception e){throw new IllegalStateException("FALSE_EVENT_WRITE_FAILED",e);}}
}
