package com.crypto.laplace.scheduler;

import com.crypto.laplace.session.LaplaceSessionService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="trading.laplace",name="enabled",havingValue="true")
public class LaplaceSessionRolloverScheduler {
 private final LaplaceSessionService sessions;
 @Scheduled(cron="0 0 0,12 * * *",zone="UTC") public void rollover(){sessions.rollover(Instant.now());}
}
