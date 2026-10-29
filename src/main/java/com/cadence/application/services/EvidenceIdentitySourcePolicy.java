package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.IdentityEvidenceProvenance;
import com.cadence.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Internal policy: callers authorize and lock parents before mutation/source validation. */
@Service
public class EvidenceIdentitySourcePolicy {
  private final BrandIdentityRepository identities;
  private final BrandAnalysisSourceRepository sources;
  private final BrandAnalysisCitationRepository citations;
  private final BrandEvidenceRepository evidence;
  private final EvidenceAdmissionWithdrawalRepository withdrawals;
  private final PdfAnalysisSourcePolicy pdf;
  private final UrlAnalysisSourcePolicy url;
  private final SocialAnalysisSourcePolicy social;

  public EvidenceIdentitySourcePolicy(
      BrandIdentityRepository identities,
      BrandAnalysisSourceRepository sources,
      BrandAnalysisCitationRepository citations,
      BrandEvidenceRepository evidence,
      EvidenceAdmissionWithdrawalRepository withdrawals,
      PdfAnalysisSourcePolicy pdf,
      UrlAnalysisSourcePolicy url,
      SocialAnalysisSourcePolicy social) {
    this.social = social;
    this.identities = identities;
    this.sources = sources;
    this.citations = citations;
    this.evidence = evidence;
    this.withdrawals = withdrawals;
    this.pdf = pdf;
    this.url = url;
  }

  public BrandIdentity origin(BrandIdentity identity) {
    var current = identity;
    for (int hops = 0; hops < BrandIdentity.MAX_REVISIONS_PER_BRAND; hops++) {
      if (current.getAnalysisAttempt() != null || current.getRevisedFromId() == null)
        return current;
      var prior =
          identities
              .findById(current.getRevisedFromId())
              .orElseThrow(() -> new IllegalStateException("Invalid identity lineage"));
      if (!prior.getBrand().getId().equals(identity.getBrand().getId())
          || prior.getVersion() >= current.getVersion())
        throw new IllegalStateException("Invalid identity lineage");
      current = prior;
    }
    throw new IllegalStateException("Identity lineage limit exceeded");
  }

  public List<EvidencePhysicalSource> validateForApproval(BrandIdentity identity, Brand brand) {
    var root = origin(identity);
    var receipt = root.getAnalysisAttempt();
    if (receipt != null && receipt.getRequestedBy() != null) return validate(brand, receipt);
    return List.of();
  }

  public List<EvidencePhysicalSource> validate(Brand brand, BrandAnalysisAttempt receipt) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("Evidence validation requires transaction");
    if (receipt == null
        || receipt.getRequestedBy() == null
        || receipt.getStatus() != BrandAnalysisAttempt.Status.SUCCEEDED
        || !receipt.getBrandId().equals(brand.getId())
        || receipt.getBrandVersion() + 1 != brand.getLockVersion()
        || receipt.getWorkspaceVersion() != brand.getWorkspace().getLockVersion()
        || brand.getStatus() != BrandStatus.ACTIVE
        || brand.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Evidence identity source is no longer current");
    var links = sources.findByAttemptIdOrderByPositionAsc(receipt.getId());
    if (links.isEmpty() || links.size() > BrandAnalysisCommand.MAX_SOURCES)
      throw new IllegalStateException("Invalid evidence source graph");
    var physical = new ArrayList<EvidencePhysicalSource>();
    var input = new ArrayList<AnalysisTextSource>();
    for (var link : links) {
      var a = link.getAdmission();
      var e =
          evidence
              .findLockedByIdAndBrandId(a.getEvidenceId(), brand.getId())
              .orElseThrow(() -> new IllegalStateException("Missing identity evidence source"));
      if (a.getSourceVersion() != e.getLockVersion()
          || a.getBrandVersion() != receipt.getBrandVersion()
          || a.getWorkspaceVersion() != receipt.getWorkspaceVersion()
          || !a.getSourceChecksum().equals(e.getChecksum())
          || withdrawals.existsForSourceVersion(e.getId(), a.getSourceVersion()))
        throw new IllegalStateException("Evidence identity source is no longer current");
      if (PdfReviewContent.POLICY_VERSION.equals(a.getPolicyVersion())) {
        physical.add(pdf.capture(a, e, brand, receipt.getBrandVersion()));
      } else if (UrlReviewContent.POLICY_VERSION.equals(a.getPolicyVersion())) {
        physical.add(url.capture(a, e, brand, receipt.getBrandVersion()));
      } else if (SocialTextProjection.POLICY.equals(a.getPolicyVersion())) {
        social.validate(a, e, receipt.getBrandVersion());
      } else {
        social.requireOrdinaryText(e);
        if (!EvidenceAdmission.POLICY_VERSION.equals(a.getPolicyVersion())
            || !EvidenceAdmission.reviewableText(e).equals(a.getReviewedText()))
          throw new IllegalStateException("Evidence identity source is no longer current");
      }
      input.add(AnalysisTextSource.from(a));
    }
    var command =
        new BrandAnalysisCommand(
            receipt.getId(),
            receipt.getBrandVersion(),
            receipt.getWorkspaceVersion(),
            receipt.getInputBrandName(),
            receipt.getInputIndustry(),
            input);
    if (!command.sourceChecksum().equals(receipt.getSourceChecksum())
        || !command.brandName().equals(brand.getName().strip())
        || !command.industry().equals(brand.getWorkspace().getIndustry().strip()))
      throw new IllegalStateException("Evidence identity input mismatch");
    command.validateCitations(
        citations.findByAttemptIdOrderByPositionAsc(receipt.getId()).stream()
            .map(c -> new AnalysisCitation(c.getAdmissionId(), c.getQuote(), c.getFieldPath()))
            .toList());
    return List.copyOf(physical);
  }

  public IdentityEvidenceProvenance describe(BrandIdentity identity) {
    var root = origin(identity);
    var receipt = root.getAnalysisAttempt();
    if (receipt == null || receipt.getRequestedBy() == null)
      return new IdentityEvidenceProvenance(identity.getId(), null, null, null, null, List.of());
    var selected = new HashMap<UUID, EvidenceAdmission>();
    for (var s : sources.findByAttemptIdOrderByPositionAsc(receipt.getId()))
      selected.put(s.getAdmission().getId(), s.getAdmission());
    var quotes = citations.findByAttemptIdOrderByPositionAsc(receipt.getId());
    if (selected.isEmpty() || selected.size() > 5 || quotes.isEmpty() || quotes.size() > 10)
      throw new IllegalStateException("Invalid evidence provenance");
    var result =
        quotes.stream()
            .map(
                c -> {
                  var a = selected.get(c.getAdmissionId());
                  if (a == null) throw new IllegalStateException("Invalid evidence citation");
                  return new IdentityEvidenceProvenance.Citation(
                      a.getId(),
                      a.getEvidenceId(),
                      a.getSourceVersion(),
                      c.getQuote(),
                      c.getFieldPath(),
                      a.getUrlSnapshotId(),
                      a.getSocialSnapshotId(),
                      a.getArtifactId(),
                      a.getOriginalScanId(),
                      a.getLatestScanId(),
                      withdrawals.existsForSourceVersion(a.getEvidenceId(), a.getSourceVersion()));
                })
            .toList();
    return new IdentityEvidenceProvenance(
        identity.getId(),
        root.getId(),
        receipt.getId(),
        root.getAnalysisCopiedBy(),
        receipt.getSourceChecksum(),
        result);
  }
}
