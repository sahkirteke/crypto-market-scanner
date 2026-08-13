package com.crypto.laplace.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

class LaplacePaperPositionEntitySchemaTest {
    @Test void strategyVersionColumnStoresSessionVersion() throws Exception {
        Column column=LaplacePaperPositionEntity.class.getDeclaredField("strategyVersion").getAnnotation(Column.class);
        assertThat(column.length()).isGreaterThanOrEqualTo("KURAL5_V3_5D_SESSION".length());
    }
}
