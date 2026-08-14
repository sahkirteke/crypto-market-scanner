package com.crypto.laplace.api;

import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.execution.LaplacePaperExecutionService;
import com.crypto.laplace.execution.LaplacePnlCalculator;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import com.crypto.laplace.service.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class LaplaceDualSummaryService {
 private static final int SCALE=8;private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);
 private final LaplacePaperPositionRepository truePositions;private final LaplaceInvertedFalsePositionRepository falsePositions;
 private final LaplaceRuntimeService trueRuntime;private final LaplaceInvertedFalseRuntimeService falseRuntime;
 private final BinanceFuturesClient binance;private final LaplacePnlCalculator pnl;
 @Transactional(readOnly=true)
 public List<LaplaceVariantAnalysisSummaryResponse> summaries(){
  List<View> trueOpen=truePositions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN).stream().map(this::view).toList();
  List<View> trueClosed=truePositions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.CLOSED).stream().map(this::view).toList();
  List<View> falseOpen=falsePositions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.OPEN).stream().map(this::view).toList();
  List<View> falseClosed=falsePositions.findByStrategyAndStatus(LaplacePaperExecutionService.STRATEGY,LaplacePositionStatus.CLOSED).stream().map(this::view).toList();
  boolean needsPrices=!trueOpen.isEmpty()||!falseOpen.isEmpty();
  Map<String,BookTicker> tickers=needsPrices?binance.getAllBookTickers().stream().filter(Objects::nonNull).filter(x->x.getSymbol()!=null).collect(Collectors.toMap(BookTicker::getSymbol,Function.identity(),(a,b)->a)):Map.of();
  var ts=trueRuntime.current();var fs=falseRuntime.current();
  return List.of(build(LaplacePaperVariant.INVERTED_TRUE,trueOpen,trueClosed,tickers,new Session(ts.getSessionId(),ts.getRuntimeState(),ts.getSessionStartCapital(),ts.getMarginPerPosition(),ts.getNextSessionCapital(),ts.getNextMarginPerPosition(),ts.getCooldownUntil())),build(LaplacePaperVariant.INVERTED_FALSE,falseOpen,falseClosed,tickers,new Session(fs.getSessionId(),fs.getRuntimeState(),fs.getSessionStartCapital(),fs.getMarginPerPosition(),fs.getNextSessionCapital(),fs.getNextMarginPerPosition(),fs.getCooldownUntil())));
 }
 private LaplaceVariantAnalysisSummaryResponse build(LaplacePaperVariant variant,List<View> open,List<View> closed,Map<String,BookTicker> tickers,Session session){
  long trades=closed.size(),longs=closed.stream().filter(x->x.side()==PositionSide.LONG).count(),shorts=closed.stream().filter(x->x.side()==PositionSide.SHORT).count();
  long wins=closed.stream().filter(x->money(x.net()).signum()>0).count(),losses=closed.stream().filter(x->money(x.net()).signum()<0).count(),even=trades-wins-losses;
  long longWins=closed.stream().filter(x->x.side()==PositionSide.LONG&&money(x.net()).signum()>0).count(),shortWins=closed.stream().filter(x->x.side()==PositionSide.SHORT&&money(x.net()).signum()>0).count();
  BigDecimal gross=sum(closed.stream().map(View::gross).toList()),entryFees=sum(closed.stream().map(View::entryFee).toList()),exitFees=sum(closed.stream().map(View::exitFee).toList()),net=sum(closed.stream().map(View::net).toList());
  BigDecimal longNet=sum(closed.stream().filter(x->x.side()==PositionSide.LONG).map(View::net).toList()),shortNet=sum(closed.stream().filter(x->x.side()==PositionSide.SHORT).map(View::net).toList());
  BigDecimal best=closed.stream().map(x->money(x.net())).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),worst=closed.stream().map(x->money(x.net())).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
  Instant first=closed.stream().map(View::entryTime).filter(Objects::nonNull).min(Instant::compareTo).orElse(null),last=closed.stream().map(View::exitTime).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
  List<View> currentClosed=closed.stream().filter(x->Objects.equals(session.id(),x.sessionId())).toList(),currentOpen=open.stream().filter(x->Objects.equals(session.id(),x.sessionId())).toList();
  BigDecimal currentPnl=sum(currentClosed.stream().map(View::net).toList()).add(currentOpen.stream().map(x->openNet(x,tickers)).reduce(BigDecimal.ZERO,BigDecimal::add));
  boolean down=session.state()!=LaplaceRuntimeState.ACTIVE;boolean next=down&&session.nextCapital()!=null;
  BigDecimal balance=next?session.nextCapital():session.capital().add(currentPnl),margin=down&&session.nextMargin()!=null?session.nextMargin():session.margin();
  Instant reopen=session.state()==LaplaceRuntimeState.COOLDOWN||session.state()==LaplaceRuntimeState.INITIALIZING?session.cooldownUntil():null;
  return new LaplaceVariantAnalysisSummaryResponse(variant.name(),variant.signalInverted(),LaplacePaperExecutionService.STRATEGY,LaplacePaperExecutionService.VERSION,trades,open.size(),trades,longs,shorts,wins,losses,even,pct(wins,trades),gross,entryFees,exitFees,entryFees.add(exitFees),net,avg(gross,trades),avg(net,trades),avg(sum(closed.stream().map(View::netPct).toList()),trades),best,worst,longNet,shortNet,pct(longWins,longs),pct(shortWins,shorts),first,last,last,balance,margin,down,session.state(),reopen);
 }
 private BigDecimal openNet(View x,Map<String,BookTicker> tickers){BookTicker q=tickers.get(x.symbol());BigDecimal price=q==null?null:x.side()==PositionSide.LONG?q.getBidPrice():q.getAskPrice();if(price==null||price.signum()<=0)throw new IllegalStateException("EXECUTION_PRICE_UNAVAILABLE:"+x.symbol());BigDecimal exitFee=price.multiply(x.quantity()).multiply(x.feeRate()).setScale(12,RoundingMode.HALF_UP);return pnl.gross(x.side(),x.entryPrice(),price,x.quantity()).subtract(money(x.entryFee())).subtract(exitFee);}
 private View view(LaplacePaperPositionEntity x){return new View(x.getSessionId(),x.getSymbol(),x.getSide(),x.getEntryTime(),x.getExitTime(),x.getEntryExecutionPrice(),x.getQuantity(),x.getEntryFeeRate(),x.getEntryFee(),x.getExitFee(),x.getGrossPnl(),x.getNetPnl(),x.getNetPnlPct());}
 private View view(LaplaceInvertedFalsePositionEntity x){return new View(x.getSessionId(),x.getSymbol(),x.getSide(),x.getEntryTime(),x.getExitTime(),x.getEntryExecutionPrice(),x.getQuantity(),x.getEntryFeeRate(),x.getEntryFee(),x.getExitFee(),x.getGrossPnl(),x.getNetPnl(),x.getNetPnlPct());}
 private BigDecimal sum(List<BigDecimal>x){return x.stream().map(this::money).reduce(BigDecimal.ZERO,BigDecimal::add);}private BigDecimal money(BigDecimal x){return x==null?BigDecimal.ZERO:x;}private BigDecimal avg(BigDecimal x,long n){return n==0?BigDecimal.ZERO:x.divide(BigDecimal.valueOf(n),SCALE,RoundingMode.HALF_UP);}private BigDecimal pct(long x,long n){return n==0?BigDecimal.ZERO:BigDecimal.valueOf(x).multiply(HUNDRED).divide(BigDecimal.valueOf(n),SCALE,RoundingMode.HALF_UP);}
 private record View(String sessionId,String symbol,PositionSide side,Instant entryTime,Instant exitTime,BigDecimal entryPrice,BigDecimal quantity,BigDecimal feeRate,BigDecimal entryFee,BigDecimal exitFee,BigDecimal gross,BigDecimal net,BigDecimal netPct){}
 private record Session(String id,LaplaceRuntimeState state,BigDecimal capital,BigDecimal margin,BigDecimal nextCapital,BigDecimal nextMargin,Instant cooldownUntil){}
}
