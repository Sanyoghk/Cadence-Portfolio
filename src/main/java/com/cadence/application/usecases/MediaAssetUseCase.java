package com.cadence.application.usecases;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.MediaDetails;
import com.cadence.application.services.MediaVerification;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

@Service
@PreAuthorize("denyAll()")
public class MediaAssetUseCase {
  private final MediaAssetRepository assets;
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final CadenceUserRepository users;
  private final PrivateMediaStorage storage;
  private final DemoMediaSource source;
  private final EvidenceMalwareScanner scanner;
  private final MediaVerification verification;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

  public MediaAssetUseCase(
      MediaAssetRepository assets,
      BrandRepository brands,
      WorkspaceRepository workspaces,
      CadenceUserRepository users,
      PrivateMediaStorage storage,
      DemoMediaSource source,
      EvidenceMalwareScanner scanner,
      MediaVerification verification,
      Clock clock,
      PlatformTransactionManager manager) {
    this.assets = assets;
    this.brands = brands;
    this.workspaces = workspaces;
    this.users = users;
    this.storage = storage;
    this.source = source;
    this.scanner = scanner;
    this.verification = verification;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MediaDetails create(UUID brandId, Set<UUID> scope, DemoMediaFixture fixture) {
    requireNoTransaction();
    source.requireEnabled();
    if (fixture == null) throw new IllegalArgumentException("Fixture required");
    if (!slots.tryAcquire()) throw new MediaUnavailableException();
    try {
      var intent =
          Objects.requireNonNull(
              tx.execute(
                  s -> {
                    var brand = parents(brandId, scope, true);
                    if (assets.countByBrandId(brandId) >= 1000
                        || assets.reservedBytes(brandId) > 100L * 1024 * 1024 - fixture.size())
                      throw new IllegalStateException("Media quota exhausted");
                    return MediaDetails.from(
                        assets.saveAndFlush(
                            new MediaAsset(brand, fixture, principal().userId(), clock.instant())));
                  }));
      MalwareScanReport report = null;
      Instant scannedAt = null;
      try {
        byte[] bytes = source.bytes(fixture).clone();
        fixture.verify(bytes);
        intent.key().verify(bytes);
        storage.storeMedia(intent.key(), bytes.clone());
        bytes = storage.readMedia(intent.key(), intent.byteSize()).clone();
        fixture.verify(bytes);
        intent.key().verify(bytes);
        report = scanner.scan(bytes);
        fixture.verify(bytes);
        scannedAt = clock.instant();
      } catch (RuntimeException failed) {
        report = null;
        scannedAt = null;
      }
      final var scan = report;
      final var time = scannedAt;
      var result =
          Objects.requireNonNull(
              tx.execute(
                  s -> {
                    if (!principal().userId().equals(intent.requestedBy()))
                      throw new AccessDeniedException("Media actor changed");
                    var a = locked(intent.id(), scope, true);
                    expected(a, intent.lockVersion());
                    a.complete(
                        scan == null ? null : ScanVerdict.valueOf(scan.result().name()),
                        scan == null
                            ? null
                            : new ScanProvenance(scan.engine(), scan.signatureVersion()),
                        time,
                        clock.instant());
                    assets.flush();
                    return MediaDetails.from(a);
                  }));
      if (result.status() != MediaAsset.State.AVAILABLE) throw new MediaUnavailableException();
      return result;
    } finally {
      slots.release();
    }
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MediaDetails read(UUID id, Set<UUID> scope) {
    return tx.execute(s -> MediaDetails.from(locked(id, scope, false)));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public Page<MediaDetails> list(UUID brandId, Set<UUID> scope, Pageable page) {
    if (page == null
        || page.isUnpaged()
        || page.getPageSize() > 100
        || page.getPageNumber() > 10000
        || page.getSort().stream()
            .anyMatch(o -> !Set.of("createdAt", "id").contains(o.getProperty())))
      throw new IllegalArgumentException("Invalid media pagination");
    return tx.execute(
        s -> {
          parents(brandId, scope, false);
          return assets.findAllByBrandId(brandId, page).map(MediaDetails::from);
        });
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MediaDetails review(UUID id, Set<UUID> scope, long version, boolean approved) {
    requireNoTransaction();
    source.requireEnabled();
    return verification.execute(
        () -> {
          var a = locked(id, scope, true);
          expected(a, version);
          a.requirePreview();
          if (a.getModeration() != MediaAsset.Moderation.PENDING)
            throw new IllegalStateException("Media already reviewed");
          return List.of(MediaDetails.from(a));
        },
        () -> {
          var a = locked(id, scope, true);
          expected(a, version);
          a.review(approved, principal().userId(), clock.instant());
          assets.flush();
          return MediaDetails.from(a);
        });
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MediaDetails archive(UUID id, Set<UUID> scope, long version) {
    return tx.execute(
        s -> {
          var a = locked(id, scope, true);
          expected(a, version);
          a.archive(principal().userId(), clock.instant());
          assets.flush();
          return MediaDetails.from(a);
        });
  }

  public record Delivery(MediaDetails asset, byte[] bytes) {
    public Delivery {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public Delivery delivery(UUID id, Set<UUID> scope, boolean preview) {
    requireNoTransaction();
    source.requireEnabled();
    var captured = new java.util.concurrent.atomic.AtomicReference<MediaDetails>();
    return verification.execute(
        () -> {
          var a = locked(id, scope, true);
          if (preview) a.requirePreview();
          else a.requireUsable();
          var details = MediaDetails.from(a);
          captured.set(details);
          return List.of(details);
        },
        bytes -> new Delivery(captured.get(), bytes.getFirst()));
  }

  private MediaAsset locked(UUID id, Set<UUID> scope, boolean active) {
    UUID brand = assets.findBrandIdIn(id, scope).orElseThrow(MediaAssetUseCase::missing);
    parents(brand, scope, active);
    return assets.findLocked(id, brand).orElseThrow(MediaAssetUseCase::missing);
  }

  private Brand parents(UUID id, Set<UUID> scope, boolean active) {
    UUID workspace = brands.findWorkspaceIdIn(id, scope).orElseThrow(MediaAssetUseCase::missing);
    var actor =
        users
            .findLockedById(principal().userId())
            .orElseThrow(() -> new AccessDeniedException("Media access denied"));
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || !users.hasWorkspaceMembership(actor.getId(), workspace))
      throw new AccessDeniedException("Media access denied");
    var w =
        workspaces.findLockedByIdAndIdIn(workspace, scope).orElseThrow(MediaAssetUseCase::missing);
    var b = brands.findLockedWithWorkspaceIn(id, scope).orElseThrow(MediaAssetUseCase::missing);
    if (active && (w.getStatus() != WorkspaceStatus.ACTIVE || b.getStatus() != BrandStatus.ACTIVE))
      throw new IllegalStateException("Media parent archived");
    return b;
  }

  private CadencePrincipal principal() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !(a.getPrincipal() instanceof CadencePrincipal p))
      throw new AccessDeniedException("Media access denied");
    return p;
  }

  private void expected(MediaAsset a, long v) {
    if (v < 0 || a.getLockVersion() != v) throw new IllegalStateException("Media version changed");
  }

  private void requireNoTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Media operation requires nontransactional caller");
  }

  private static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Media not found");
  }
}
