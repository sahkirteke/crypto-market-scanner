package com.crypto.laplace.api;

import com.crypto.api.dto.LaplaceClosedPaperPositionResponse;
import com.crypto.api.dto.LaplaceOpenPaperPositionResponse;
import com.crypto.api.dto.LaplaceOpenPaperPositionsResponse;
import com.crypto.api.dto.LaplaceVariantSummaryResponse;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePnlCalculator;
import com.crypto.laplace.model.LaplacePaperVariant;
import com.crypto.laplace.model.LaplacePositionStatus;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionEntity;
import com.crypto.laplace.persistence.LaplaceInvertedFalsePositionRepository;
import com.crypto.laplace.service.LaplaceInvertedFalseRuntimeService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LaplaceInvertedFalseApiService {
    private final LaplaceInvertedFalsePositionRepository repository;private final BinanceFuturesClient client;private final LaplacePnlCalculator pnl;private final LaplaceInvertedFalseRuntimeService runtime;
    public LaplaceOpenPaperPositionsResponse open(){
        List<LaplaceInvertedFalsePositionEntity> open=unique(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN),LaplacePositionStatus.OPEN);
        List<LaplaceInvertedFalsePositionEntity> closed=unique(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.CLOSED),LaplacePositionStatus.CLOSED);
        BigDecimal closedPnl=closed.stream().map(x->x.getNetPnl()==null?BigDecimal.ZERO:x.getNetPnl()).reduce(BigDecimal.ZERO,BigDecimal::add);var session=runtime.current();
        if(open.isEmpty())return response(session,List.of(),closed.size(),closedPnl,BigDecimal.ZERO);
        if(!runtime.isActive())throw new IllegalStateException("INVERTED_FALSE_MARKET_DATA_DISABLED");
        Map<String,BookTicker> tickers=client.getAllBookTickers().stream().collect(Collectors.toMap(BookTicker::getSymbol,Function.identity(),(a,b)->a));
        List<LaplaceOpenPaperPositionResponse> rows=open.stream().map(p->{BookTicker t=tickers.get(p.getSymbol());BigDecimal price=t==null?null:p.getSide()==PositionSide.LONG?t.getBidPrice():t.getAskPrice();if(price==null)throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE");return new LaplaceOpenPaperPositionResponse(p.getId(),p.getSymbol(),p.getSide(),p.getEntryTime(),p.getEntryExecutionPrice(),price,pnl.priceMovePct(p.getSide(),p.getEntryExecutionPrice(),price),pnl.gross(p.getSide(),p.getEntryExecutionPrice(),price,p.getQuantity()),false);}).toList();
        BigDecimal openPnl=rows.stream().map(LaplaceOpenPaperPositionResponse::currentPnlUsdt).reduce(BigDecimal.ZERO,BigDecimal::add);return response(session,rows,closed.size(),closedPnl,openPnl);
    }
    public List<LaplaceClosedPaperPositionResponse> closed(){return unique(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.CLOSED),LaplacePositionStatus.CLOSED).stream().map(p->new LaplaceClosedPaperPositionResponse(p.getId(),p.getSymbol(),p.getSide(),p.getEntryTime(),p.getExitTime(),p.getNetPnl(),p.getExitReason())).toList();}
    public LaplaceVariantSummaryResponse summary(){var open=repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN);var closed=unique(repository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.CLOSED),LaplacePositionStatus.CLOSED);return new LaplaceVariantSummaryResponse(LaplacePaperVariant.INVERTED_FALSE,open.size(),closed.size(),closed.stream().map(p->p.getNetPnl()==null?BigDecimal.ZERO:p.getNetPnl()).reduce(BigDecimal.ZERO,BigDecimal::add));}
    private LaplaceOpenPaperPositionsResponse response(com.crypto.laplace.persistence.LaplaceInvertedFalseSessionEntity s,List<LaplaceOpenPaperPositionResponse> p,int cc,BigDecimal cp,BigDecimal op){return new LaplaceOpenPaperPositionsResponse(LaplacePaperVariant.INVERTED_FALSE,false,p,p.size(),cc,cp,op,cp.add(op),s.getSessionStartCapital(),s.getMarginPerPosition(),s.getLeverage(),s.getMarginPerPosition().multiply(BigDecimal.valueOf(s.getLeverage())),s.getRuntimeState(),s.getCooldownUntil());}
    private List<LaplaceInvertedFalsePositionEntity> unique(List<LaplaceInvertedFalsePositionEntity> input,LaplacePositionStatus status){Map<String,LaplaceInvertedFalsePositionEntity> map=new LinkedHashMap<>();for(var p:input)if(p!=null&&p.getStatus()==status&&LaplacePaperExecutionService.STRATEGY.equals(p.getStrategy()))map.putIfAbsent(p.getId(),p);return List.copyOf(map.values());}
}
