package com.crypto.laplace.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

class LaplaceVariantTableIsolationTest {
 @Test void variantsUsePhysicallySeparateTables() {
  assertThat(LaplacePaperPositionEntity.class.getAnnotation(Table.class).name()).isEqualTo("laplace_paper_positions");
  assertThat(LaplaceInvertedFalsePositionEntity.class.getAnnotation(Table.class).name()).isEqualTo("laplace_inverted_false_positions");
  assertThat(LaplaceTradeEventEntity.class.getAnnotation(Table.class).name()).isEqualTo("laplace_trade_events");
  assertThat(LaplaceInvertedFalseTradeEventEntity.class.getAnnotation(Table.class).name()).isEqualTo("laplace_inverted_false_trade_events");
  assertThat(LaplaceTradingSessionEntity.class.getAnnotation(Table.class).name()).isEqualTo("laplace_trading_sessions");
  assertThat(LaplaceInvertedFalseSessionEntity.class.getAnnotation(Table.class).name()).isEqualTo("laplace_inverted_false_sessions");
 }
}
