package com.crypto.laplace.execution;
import com.crypto.common.enums.PositionSide;import java.math.*;import org.springframework.stereotype.Component;
@Component
public class LaplacePnlCalculator {
 private static final int SCALE=12; private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);
 public Pnl calculate(PositionSide side,BigDecimal entry,BigDecimal exit,BigDecimal quantity,BigDecimal entryFee,BigDecimal exitFee){
  BigDecimal move=(side==PositionSide.LONG?exit.subtract(entry):entry.subtract(exit));BigDecimal gross=move.multiply(quantity).setScale(SCALE,RoundingMode.HALF_UP);BigDecimal grossPct=move.divide(entry,SCALE,RoundingMode.HALF_UP).multiply(HUNDRED);BigDecimal net=gross.subtract(entryFee).subtract(exitFee);BigDecimal notional=entry.multiply(quantity);BigDecimal netPct=net.divide(notional,SCALE,RoundingMode.HALF_UP).multiply(HUNDRED);return new Pnl(gross,grossPct,net,netPct);
 }
 public record Pnl(BigDecimal gross,BigDecimal grossPct,BigDecimal net,BigDecimal netPct){}
}
