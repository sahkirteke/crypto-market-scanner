package com.crypto.laplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.crypto.laplace.execution.*;
import com.crypto.laplace.scheduler.*;
import com.crypto.laplace.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LaplaceFalseRuntimeDisabledTest {
 @Test void productionDefaultsCreateNoFalseRuntimeBeans(){new ApplicationContextRunner().withPropertyValues("trading.laplace.paper.inverted-false.enabled=false").withUserConfiguration(LaplaceInvertedFalseTradeCoordinator.class,LaplaceInvertedFalseExecutionService.class,LaplaceInvertedFalseTradeJsonlWriter.class,LaplaceInvertedFalseRuntimeService.class,LaplaceInvertedFalseProfitLockService.class,LaplaceInvertedFalseSessionLifecycleScheduler.class,LaplaceInvertedFalseProfitLockScheduler.class,LaplaceInvertedFalseStopLossScheduler.class).run(context->{assertThat(context).hasNotFailed();assertThat(context.getBeansOfType(LaplaceInvertedFalseTradeCoordinator.class)).isEmpty();assertThat(context.getBeansOfType(LaplaceInvertedFalseExecutionService.class)).isEmpty();assertThat(context.getBeansOfType(LaplaceInvertedFalseRuntimeService.class)).isEmpty();assertThat(context.getBeansOfType(LaplaceInvertedFalseTradeJsonlWriter.class)).isEmpty();assertThat(context.getBeansOfType(LaplaceInvertedFalseProfitLockService.class)).isEmpty();assertThat(context.getBeansOfType(LaplaceInvertedFalseSessionLifecycleScheduler.class)).isEmpty();assertThat(context.getBeansOfType(LaplaceInvertedFalseProfitLockScheduler.class)).isEmpty();assertThat(context.getBeansOfType(LaplaceInvertedFalseStopLossScheduler.class)).isEmpty();});}
}
