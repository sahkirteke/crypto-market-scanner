package com.crypto.laplace.service;

import com.crypto.domain.model.Kline;
import com.crypto.laplace.model.*;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class LaplaceSignalService {
 public static final double ENTRY=.03, REVERSAL=.04;
 private final LaplaceKernelRegressionCalculator regression; private final Atr14Calculator atr;
 public LaplaceSignalResult calculate(String symbol,List<Kline> candles,int newBars) {
  RegressionValues r=regression.calculate(candles); int end=candles.size()-1;
  double ca=atr.at(candles,end), pa=atr.at(candles,end-1), cs=r.current()-r.previous(), ps=r.previous()-r.twoBarsAgo();
  double cn=cs/ca,pn=ps/pa; if(!Double.isFinite(cn)||!Double.isFinite(pn)) throw new IllegalArgumentException("Invalid normalized slope");
  Kline candle=candles.get(end); boolean warmed=newBars>=1; List<RejectionReason> reasons=new ArrayList<>();
  LaplaceSignal entry=LaplaceSignal.NONE, reversal=LaplaceSignal.NONE;
  if(!warmed) reasons.add(RejectionReason.STARTUP_WARMUP);
  else if(cn>=ENTRY && pn>=ENTRY) { if(candle.getClose().doubleValue()>r.current()) entry=LaplaceSignal.LONG; else reasons.add(RejectionReason.CLOSE_NOT_ABOVE_REGRESSION); }
  else if(cn<=-ENTRY && pn<=-ENTRY) { if(candle.getClose().doubleValue()<r.current()) entry=LaplaceSignal.SHORT; else reasons.add(RejectionReason.CLOSE_NOT_BELOW_REGRESSION); }
  else { if(cn>-ENTRY&&cn<ENTRY) reasons.add(RejectionReason.SLOPE_IN_DEAD_ZONE); else reasons.add(RejectionReason.SECOND_CONFIRMATION_MISSING); }
  if(warmed && cn>=REVERSAL&&pn>=REVERSAL&&candle.getClose().doubleValue()>r.current()) reversal=LaplaceSignal.LONG;
  if(warmed && cn<=-REVERSAL&&pn<=-REVERSAL&&candle.getClose().doubleValue()<r.current()) reversal=LaplaceSignal.SHORT;
  double atrPercentage=ca/candle.getClose().doubleValue()*100.0;
  double previousRawTakerImbalance=rawDirectedTakerImbalance(candles.get(end-1),entry);
  return new LaplaceSignalResult("LAPLACE_KERNEL_REGRESSION_30M","1.0",symbol,"30m","LAPLACE",14,"CLOSE",false,candle.getOpenTime(),candle.getCloseTime(),candle.getClose().doubleValue(),r.current(),r.previous(),r.twoBarsAgo(),cs,ps,ca,pa,atrPercentage,previousRawTakerImbalance,cn,pn,ENTRY,REVERSAL,2,entry,reversal,warmed?StartupState.ACTIVE:StartupState.READY_WAITING_NEXT_CLOSE,newBars,warmed && entry!=LaplaceSignal.NONE,reasons);
 }
 private double rawDirectedTakerImbalance(Kline candle,LaplaceSignal rawSignal){BigDecimal volume=candle.getVolume(),buy=candle.getTakerBuyBaseVolume();if(volume==null||buy==null||volume.signum()<=0)return Double.NaN;double market=buy.multiply(BigDecimal.valueOf(2)).subtract(volume).divide(volume,12,RoundingMode.HALF_UP).doubleValue();return rawSignal==LaplaceSignal.SHORT?-market:market;}
}
