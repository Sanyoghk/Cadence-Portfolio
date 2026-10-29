package com.cadence.application.ports;

import com.cadence.domain.SocialEvidenceProvider;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only official APIs for an operator-approved credential binding; never a client URL/token.
 */
public interface ApprovedSocialEvidenceSource {
  record Snapshot(
      SocialEvidenceProvider provider,
      String accountId,
      String apiVersion,
      Instant retrievedAt,
      String canonicalJson) {
    public Snapshot {
      if (provider == null
          || canonicalJson == null
          || canonicalJson.isBlank()
          || canonicalJson.length() > 20_000
          || accountId == null
          || !accountId.matches("[A-Za-z0-9_-]{1,128}")
          || apiVersion == null
          || !apiVersion.matches("v[1-9][0-9]*\\.0|v2")
          || retrievedAt == null)
        throw new IllegalArgumentException("Invalid social evidence snapshot");
    }
  }

  /** Check the server binding locally; this operation must perform no network or storage I/O. */
  void requireApprovedBinding(UUID brandId, SocialEvidenceProvider provider);

  /** Same-account local authorization; never sends HTTP or returns a credential. */
  default Instant requireApprovedAccount(
      UUID brandId, SocialEvidenceProvider provider, String accountId) {
    throw new com.cadence.application.exceptions.SocialEvidenceUnavailableException();
  }

  Snapshot fetch(UUID brandId, SocialEvidenceProvider provider);
}
