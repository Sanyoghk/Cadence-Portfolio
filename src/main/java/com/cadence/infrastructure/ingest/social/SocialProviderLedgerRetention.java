package com.cadence.infrastructure.ingest.social;

import com.cadence.application.repositories.EvidenceProviderCallRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bounded maintenance; the database independently rejects deletion inside the 90-day window. */
@Component
@ConditionalOnProperty(
    name = "cadence.evidence.social.ledger-retention-enabled",
    havingValue = "true")
public class SocialProviderLedgerRetention {
  private final EvidenceProviderCallRepository calls;
  private final Clock clock;

  public SocialProviderLedgerRetention(EvidenceProviderCallRepository calls, Clock clock) {
    this.calls = calls;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${cadence.evidence.social.ledger-retention-interval:PT1H}")
  @Transactional
  public void purgeExpired() {
    var expired =
        calls.findUnreferencedBefore(
            Instant.now(clock).minus(Duration.ofDays(90)), PageRequest.of(0, 500));
    calls.deleteAll(expired);
    calls.flush();
  }
}
