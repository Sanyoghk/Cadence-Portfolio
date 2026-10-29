package com.cadence.application.services;

import com.cadence.application.ports.ApprovedSocialEvidenceSource;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.*;
import org.springframework.stereotype.Service;

/**
 * Local origin/binding validation. Callers hold authorized parent/source locks. No external I/O.
 */
@Service
public class SocialAnalysisSourcePolicy {
  private final EvidenceSocialOriginRepository origins;
  private final SocialTextSnapshotRepository snapshots;
  private final ApprovedSocialEvidenceSource provider;
  private final Clock clock;

  public SocialAnalysisSourcePolicy(
      EvidenceSocialOriginRepository origins,
      SocialTextSnapshotRepository snapshots,
      ApprovedSocialEvidenceSource provider,
      Clock clock) {
    this.origins = origins;
    this.snapshots = snapshots;
    this.provider = provider;
    this.clock = clock;
  }

  public void requireOrdinaryText(BrandEvidence e) {
    if (origins.existsById(e.getId()))
      throw new IllegalStateException("Social origin requires typed social review");
  }

  public EvidenceSocialOrigin origin(BrandEvidence e) {
    var o =
        origins
            .findById(e.getId())
            .orElseThrow(() -> new IllegalStateException("Trusted social origin required"));
    if (o.getCapturedAt().isAfter(clock.instant()))
      throw new IllegalStateException("Invalid social capture time");
    SocialTextProjection.project(e, o);
    return o;
  }

  public Instant binding(BrandEvidence e, EvidenceSocialOrigin o) {
    var expiry =
        provider.requireApprovedAccount(e.getBrand().getId(), o.getProvider(), o.getAccountId());
    if (expiry == null || !expiry.isAfter(clock.instant().plusSeconds(30)))
      throw new IllegalStateException("Social binding unavailable");
    return expiry.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  public SocialTextSnapshot validate(EvidenceAdmission a, BrandEvidence e, long brandVersion) {
    var o = origin(e);
    var expiry = binding(e, o);
    if (a.getSocialSnapshotId() == null)
      throw new IllegalStateException("Social snapshot unavailable");
    var s =
        snapshots
            .findById(a.getSocialSnapshotId())
            .orElseThrow(() -> new IllegalStateException("Social snapshot unavailable"));
    s.requireCurrent(e, o);
    if (!SocialTextProjection.POLICY.equals(a.getPolicyVersion())
        || a.getSourceVersion() != e.getLockVersion()
        || a.getBrandVersion() != brandVersion
        || a.getWorkspaceVersion() != e.getBrand().getWorkspace().getLockVersion()
        || !a.getSourceChecksum().equals(e.getChecksum())
        || !a.getReviewedText().equals(s.getPayload())
        || !a.getContentChecksum().equals(s.getContentChecksum())
        || !expiry.equals(a.getBindingExpiresAt())
        || !a.getBindingExpiresAt().isAfter(clock.instant().plusSeconds(30)))
      throw new IllegalStateException("Social admission is no longer current");
    return s;
  }
}
