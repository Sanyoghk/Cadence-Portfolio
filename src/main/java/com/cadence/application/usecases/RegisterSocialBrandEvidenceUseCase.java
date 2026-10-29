package com.cadence.application.usecases;

import com.cadence.application.exceptions.SocialEvidenceUnavailableException;
import com.cadence.application.ports.ApprovedSocialEvidenceSource;
import com.cadence.application.repositories.EvidenceSocialOriginRepository;
import com.cadence.application.services.EvidenceProviderQuota;
import com.cadence.domain.EvidenceSocialOrigin;
import com.cadence.domain.EvidenceType;
import com.cadence.domain.SocialEvidenceProvider;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@PreAuthorize("denyAll()")
public class RegisterSocialBrandEvidenceUseCase {
  private final EvidenceProviderQuota quota;
  private final ApprovedSocialEvidenceSource source;
  private final RegisterBrandEvidenceUseCase registration;
  private final EvidenceSocialOriginRepository origins;
  private final TransactionTemplate publication;

  public RegisterSocialBrandEvidenceUseCase(
      EvidenceProviderQuota quota,
      ApprovedSocialEvidenceSource source,
      RegisterBrandEvidenceUseCase registration,
      EvidenceSocialOriginRepository origins,
      PlatformTransactionManager transactionManager) {
    this.quota = quota;
    this.source = source;
    this.registration = registration;
    this.origins = origins;
    this.publication = new TransactionTemplate(transactionManager);
    this.publication.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(propagation = Propagation.NEVER)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public RegisterBrandEvidenceUseCase.Registration register(
      UUID brandId, Set<UUID> scope, SocialEvidenceProvider provider) {
    Set<UUID> immutableScope = Set.copyOf(scope);
    // Scoped ownership, active status and committed quota preflight precede all external calls.
    UUID receipt = quota.reserve(brandId, immutableScope, provider);
    ApprovedSocialEvidenceSource.Snapshot snapshot;
    try {
      snapshot = source.fetch(brandId, provider);
    } catch (com.cadence.application.exceptions.EvidenceQuotaExceededException limit) {
      throw limit;
    } catch (RuntimeException failure) {
      throw new SocialEvidenceUnavailableException();
    }
    if (snapshot == null || snapshot.provider() != provider)
      throw new SocialEvidenceUnavailableException();
    // Existing use case rechecks current ownership/status in the publication transaction.
    ApprovedSocialEvidenceSource.Snapshot approved = snapshot;
    return publication.execute(
        status -> {
          var result =
              registration.register(
                  brandId, immutableScope, EvidenceType.TEXT, approved.canonicalJson());
          var origin = origins.findById(result.evidence().getId());
          if (origin.isPresent()) {
            EvidenceSocialOrigin existing = origin.get();
            if (existing.getProvider() != approved.provider()
                || !existing.getAccountId().equals(approved.accountId())
                || !existing.getApiVersion().equals(approved.apiVersion())
                || !existing.getChecksum().equals(result.evidence().getChecksum()))
              throw new SocialEvidenceUnavailableException();
          } else
            origins.saveAndFlush(
                new EvidenceSocialOrigin(
                    result.evidence().getId(),
                    receipt,
                    approved.provider(),
                    approved.accountId(),
                    approved.apiVersion(),
                    approved.retrievedAt(),
                    result.evidence().getChecksum()));
          return result;
        });
  }
}
