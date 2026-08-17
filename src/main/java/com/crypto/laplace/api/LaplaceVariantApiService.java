package com.crypto.laplace.api;

import com.crypto.api.dto.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePnlCalculator;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** One calculation path; repository selection happens before any calculation. */
@Service @RequiredArgsConstructor
public class LaplaceVariantApiService {
 private final LaplacePaperPositionRepository trueRepository;
 private final BinanceFuturesClient prices;private final LaplacePnlCalculator pnl;
 public LaplaceOpenPaperPositionsResponse open(LaplacePaperVariant variant){return calculate(variant).response();}
 public List<LaplacePaperPositionResponse> closed(LaplacePaperVariant variant){return load(variant,LaplacePositionStatus.CLOSED).stream().map(this::closedResponse).toList();}
 public LaplaceVariantSummaryResponse summary(LaplacePaperVariant variant){Calculation x=calculate(variant);var a=x.response();return new LaplaceVariantSummaryResponse(variant.name(),a.openPositionCount(),x.closedCount(),a.closedPositionsNetPnlUsdt(),a.openPositionsCurrentPnlUsdt(),a.totalPnlUsdt());}
 private Calculation calculate(LaplacePaperVariant variant){
  List<View> open=load(variant,LaplacePositionStatus.OPEN),closed=load(variant,LaplacePositionStatus.CLOSED);
  Map<String,BookTicker> tickers=open.isEmpty()?Map.of():prices.getAllBookTickers().stream().filter(Objects::nonNull).filter(x->x.getSymbol()!=null).collect(Collectors.toMap(BookTicker::getSymbol,Function.identity(),(a,b)->a));
  List<LaplaceOpenPaperPositionResponse> responses=open.stream().sorted(Comparator.comparing(View::entryTime)).map(p->openResponse(p,tickers)).toList();
  BigDecimal openPnl=responses.stream().map(LaplaceOpenPaperPositionResponse::currentPnlUsdt).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal closedPnl=closed.stream().map(x->money(x.netPnl())).reduce(BigDecimal.ZERO,BigDecimal::add);
  return new Calculation(new LaplaceOpenPaperPositionsResponse(responses,responses.size(),closedPnl.add(openPnl),closedPnl,openPnl,closedPnl.add(openPnl)),closed.size());
 }
 private List<View> load(LaplacePaperVariant variant,LaplacePositionStatus status){
  if(variant!=LaplacePaperVariant.INVERTED_TRUE)throw new IllegalArgumentException("PAPER_VARIANT_DISABLED");
  return trueRepository.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,status).stream().map(this::view).toList();
 }
 private LaplaceOpenPaperPositionResponse openResponse(View p,Map<String,BookTicker> tickers){BookTicker q=tickers.get(p.symbol());BigDecimal current=q==null?null:p.side()==PositionSide.LONG?q.getBidPrice():q.getAskPrice();if(current==null||current.signum()<=0)throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE:"+p.symbol());BigDecimal exitFee=current.multiply(p.quantity()).multiply(p.entryFeeRate()).setScale(12,java.math.RoundingMode.HALF_UP);BigDecimal currentNet=pnl.gross(p.side(),p.entryPrice(),current,p.quantity()).subtract(money(p.entryFee())).subtract(exitFee);return new LaplaceOpenPaperPositionResponse(p.id(),p.symbol(),p.side(),p.entryTime(),p.entryPrice(),current,pnl.priceMovePct(p.side(),p.entryPrice(),current),currentNet,false);}
 private LaplacePaperPositionResponse closedResponse(View p){return new LaplacePaperPositionResponse(p.id(),p.symbol(),p.side(),p.status(),p.entryTime(),p.entryPrice(),p.margin(),p.quantity(),p.notional(),p.leverage(),p.entryFee(),p.exitTime(),p.exitPrice(),p.exitFee(),p.netPnl(),p.exitReason());}
 private View view(LaplacePaperPositionEntity p){return new View(p.getId(),p.getSymbol(),p.getSide(),p.getStatus(),p.getEntryTime(),p.getEntryExecutionPrice(),p.getMargin(),p.getQuantity(),p.getNotional(),p.getLeverage(),p.getEntryFee(),p.getEntryFeeRate(),p.getExitTime(),p.getExitExecutionPrice(),p.getExitFee(),p.getNetPnl(),p.getExitReason());}
 private BigDecimal money(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
 private record Calculation(LaplaceOpenPaperPositionsResponse response,long closedCount){}
 private record View(String id,String symbol,PositionSide side,LaplacePositionStatus status,Instant entryTime,BigDecimal entryPrice,BigDecimal margin,BigDecimal quantity,BigDecimal notional,int leverage,BigDecimal entryFee,BigDecimal entryFeeRate,Instant exitTime,BigDecimal exitPrice,BigDecimal exitFee,BigDecimal netPnl,String exitReason){}
}
