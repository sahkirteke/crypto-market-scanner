package com.crypto.laplace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.crypto.binance.client.BinanceFuturesClient;
import com.crypto.common.enums.PositionSide;
import com.crypto.domain.model.BookTicker;
import com.crypto.laplace.execution.*;
import com.crypto.laplace.model.*;
import com.crypto.laplace.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LaplaceVariantApiServiceTest {

 @Test void openAndAggregatePnlDeductEntryAndEstimatedExitFees() {
  var t=mock(LaplacePaperPositionRepository.class);var prices=mock(BinanceFuturesClient.class);
  var open=LaplacePaperPositionEntity.builder().id("t").symbol("BTCUSDT").side(PositionSide.LONG).status(LaplacePositionStatus.OPEN).entryTime(Instant.parse("2026-08-14T00:00:00Z")).entryExecutionPrice(new BigDecimal("100")).quantity(BigDecimal.ONE).entryFee(new BigDecimal("0.04")).entryFeeRate(new BigDecimal("0.0004")).leverage(15).build();
  var closed=LaplacePaperPositionEntity.builder().id("c").status(LaplacePositionStatus.CLOSED).netPnl(new BigDecimal("2.00")).leverage(15).build();
  when(t.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.OPEN))).thenReturn(List.of(open));when(t.findByStrategyAndStatus(any(),eq(LaplacePositionStatus.CLOSED))).thenReturn(List.of(closed));when(prices.getAllBookTickers()).thenReturn(List.of(BookTicker.builder().symbol("BTCUSDT").bidPrice(new BigDecimal("110")).askPrice(new BigDecimal("111")).build()));
  var response=new LaplaceVariantApiService(t,prices,new LaplacePnlCalculator()).open(LaplacePaperVariant.INVERTED_TRUE);
  assertThat(response.positions().getFirst().currentPnlUsdt()).isEqualByComparingTo("9.916000000000");
  assertThat(response.openPositionsCurrentPnlUsdt()).isEqualByComparingTo("9.916000000000");assertThat(response.totalPnlUsdt()).isEqualByComparingTo("11.916000000000");assertThat(response.totalCurrentPnlUsdt()).isEqualByComparingTo(response.totalPnlUsdt());
 }
}
