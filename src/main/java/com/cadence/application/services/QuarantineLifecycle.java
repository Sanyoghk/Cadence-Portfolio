package com.cadence.application.services;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.ports.EvidenceTransactionIsolation;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuarantineLifecycle {
  public record Reservation(UUID id, String objectKey, Instant deadline) {
    public String reference() {
      return "quarantine:" + objectKey;
    }
  }

  private final QuarantineObjectRepository objects;
  private final WorkspaceRepository workspaces;
  private final BrandRepository brands;
  private final BrandEvidenceRepository evidence;
  private final QuarantineLifecyclePolicy policy;
  private final Clock clock;
  private final EvidenceArtifactRepository artifacts;
  private final EvidenceTransactionIsolation isolation;

  public QuarantineLifecycle(
      QuarantineObjectRepository objects,
      WorkspaceRepository workspaces,
      BrandRepository brands,
      BrandEvidenceRepository evidence,
      QuarantineLifecyclePolicy policy,
      Clock clock,
      EvidenceArtifactRepository artifacts,
      EvidenceTransactionIsolation isolation) {
    this.objects = objects;
    this.workspaces = workspaces;
    this.brands = brands;
    this.evidence = evidence;
    this.policy = policy;
    this.clock = clock;
    this.artifacts = artifacts;
    this.isolation = isolation;
  }

  /** Call only after the request's ownership check; this method performs no external I/O. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public Reservation reserve(UUID workspaceId, UUID brandId, long maxBytes, String extension) {
    isolation.requireReadCommitted();
    Workspace workspace =
        workspaces
            .findLockedById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid quarantine workspace"));
    Brand brand =
        brands
            .findByIdAndWorkspaceId(brandId, workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid quarantine brand"));
    if (workspace.getStatus() != WorkspaceStatus.ACTIVE || brand.getStatus() != BrandStatus.ACTIVE)
      throw new IllegalStateException("Cannot reserve evidence for an archived brand or workspace");
    Instant now = now();
    QuarantineObject object =
        new QuarantineObject(
            workspaceId,
            brandId,
            maxBytes,
            extension,
            now,
            policy.writeDeadline(),
            policy.orphanRetention());
    // The workspace row serializes reservations across every brand and worker in the tenant.
    long retained =
        Math.addExact(objects.retainedBytes(workspaceId), objects.legacyBytes(workspaceId));
    if (maxBytes > policy.maxWorkspaceBytes() - retained
        || Math.addExact(objects.retainedObjects(workspaceId), objects.legacyObjects(workspaceId))
            >= policy.maxWorkspaceObjects()
        || objects.writesAfter(workspaceId, now.minus(Duration.ofHours(1)))
            >= policy.maxWorkspaceWritesPerHour()) throw new EvidenceQuotaExceededException();
    objects.saveAndFlush(object);
    return new Reservation(object.getId(), object.getObjectKey(), object.getWriteDeadline());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void requireWritable(UUID reservationId) {
    objects
        .findLockedById(reservationId)
        .orElseThrow(() -> new IllegalStateException("Quarantine reservation is unavailable"))
        .requireWritable(now());
  }

  /** The evidence, its job and the reference transition must share the caller's transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void reference(UUID reservationId, UUID evidenceId, String reference, long actualBytes) {
    QuarantineObject object =
        objects
            .findLockedById(reservationId)
            .orElseThrow(() -> new IllegalStateException("Quarantine reservation is unavailable"));
    BrandEvidence item =
        evidence
            .findById(evidenceId)
            .orElseThrow(() -> new IllegalStateException("Quarantine evidence is unavailable"));
    if (!object.reference().equals(reference)
        || !object.getBrandId().equals(item.getBrand().getId())
        || !object.getWorkspaceId().equals(item.getBrand().getWorkspace().getId())
        || !(reference.equals(item.getSourceReference())
            || reference.equals(item.getFetchedReference())))
      throw new IllegalStateException("Quarantine reference does not match its reservation");
    object.reference(evidenceId, actualBytes, now());
    objects.flush();
  }

  /** Derived artifacts retain their parent Evidence ownership but own a separate generation. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void referenceArtifact(
      UUID reservationId, UUID artifactId, String reference, long actualBytes) {
    EvidenceArtifact artifact =
        artifacts
            .findById(artifactId)
            .orElseThrow(() -> new IllegalStateException("Quarantine artifact is unavailable"));
    QuarantineObject object =
        objects
            .findLockedById(reservationId)
            .orElseThrow(() -> new IllegalStateException("Quarantine reservation is unavailable"));
    BrandEvidence item = evidence.findById(artifact.getEvidenceId()).orElseThrow();
    if (!object.reference().equals(reference)
        || !reference.equals(artifact.getStorageReference())
        || !object.getBrandId().equals(item.getBrand().getId())
        || !object.getWorkspaceId().equals(item.getBrand().getWorkspace().getId()))
      throw new IllegalStateException("Quarantine artifact does not match its reservation");
    object.reference(item.getId(), actualBytes, now());
    objects.flush();
  }

  private Instant now() {
    // Persisted timestamps have microsecond precision; use the same exact boundaries after reload.
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
