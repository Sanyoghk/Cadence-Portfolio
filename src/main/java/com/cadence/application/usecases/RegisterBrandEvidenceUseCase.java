package com.cadence.application.usecases;

import com.cadence.application.exceptions.EvidenceScanUnavailableException;
import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.ports.EvidenceMalwareScanner;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.ports.MalwareScanReport;
import com.cadence.application.ports.MalwareScanResult;
import com.cadence.application.repositories.BrandEvidenceRepository;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.repositories.QuarantineWriteIntentRepository;
import com.cadence.application.services.DurableJobQueue;
import com.cadence.application.services.QuarantineLifecycle;
import com.cadence.domain.Brand;
import com.cadence.domain.BrandEvidence;
import com.cadence.domain.BrandStatus;
import com.cadence.domain.DurableJob;
import com.cadence.domain.EvidenceType;
import com.cadence.domain.QuarantineWriteIntent;
import com.cadence.domain.WorkspaceStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@PreAuthorize("denyAll()")
@Transactional(readOnly = true)
public class RegisterBrandEvidenceUseCase {

  public record Registration(BrandEvidence evidence, DurableJob job) {}

  private static final int MAX_TEXT_LENGTH = 20_000;
  private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
  private final BrandRepository brands;
  private final BrandEvidenceRepository evidence;
  private final QuarantineWriteIntentRepository writeIntents;
  private final DurableJobQueue jobs;
  private final EvidenceObjectStorage storage;
  private final EvidenceMalwareScanner malwareScanner;
  private final Clock clock;
  private final TransactionTemplate fileTransactions;
  private final QuarantineLifecycle lifecycle;
  private final com.cadence.application.services.EvidenceRegistrationQuota registrationQuota;

  public RegisterBrandEvidenceUseCase(
      BrandRepository brands,
      BrandEvidenceRepository evidence,
      QuarantineWriteIntentRepository writeIntents,
      DurableJobQueue jobs,
      EvidenceObjectStorage storage,
      EvidenceMalwareScanner malwareScanner,
      PlatformTransactionManager transactionManager,
      Clock clock,
      QuarantineLifecycle lifecycle,
      com.cadence.application.services.EvidenceRegistrationQuota registrationQuota) {
    this.brands = brands;
    this.evidence = evidence;
    this.writeIntents = writeIntents;
    this.jobs = jobs;
    this.storage = storage;
    this.malwareScanner = malwareScanner;
    this.clock = clock;
    this.fileTransactions = new TransactionTemplate(transactionManager);
    this.fileTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.lifecycle = lifecycle;
    this.registrationQuota = registrationQuota;
  }

  @Transactional(propagation = Propagation.NEVER)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Registration registerFile(
      UUID brandId,
      Set<UUID> permittedWorkspaceIds,
      String originalFilename,
      String declaredMediaType,
      byte[] content) {
    Set<UUID> scope = Set.copyOf(permittedWorkspaceIds);
    UUID workspaceId =
        fileTransactions.execute(status -> findActiveBrand(brandId, scope).getWorkspace().getId());
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("Evidence file must not be empty");
    }
    if (content.length > MAX_FILE_BYTES) {
      throw new IllegalArgumentException("Evidence file must not exceed 10 MiB");
    }
    byte[] ownedContent = content.clone();
    FileType fileType = detectFileType(ownedContent);
    if (declaredMediaType != null
        && !declaredMediaType.isBlank()
        && !fileType.mediaType().equalsIgnoreCase(declaredMediaType)) {
      throw new IllegalArgumentException("Declared media type does not match file content");
    }
    String safeFilename = safeFilename(originalFilename);
    String checksum = sha256(ownedContent);
    var reservation =
        lifecycle.reserve(workspaceId, brandId, ownedContent.length, fileType.extension());
    requireCleanScan(ownedContent.clone());
    Registration existing =
        fileTransactions.execute(
            status -> {
              Brand brand = findLockedActiveBrand(brandId, scope);
              return evidence
                  .findByBrandIdAndChecksum(brand.getId(), checksum)
                  .map(stored -> enqueue(brandId, checksum, stored))
                  .orElse(null);
            });
    if (existing != null) return existing;
    String objectKey = reservation.objectKey();
    UUID intentId =
        fileTransactions.execute(
            status ->
                writeIntents
                    .saveAndFlush(new QuarantineWriteIntent(brandId, objectKey, Instant.now(clock)))
                    .getId());
    String storageReference;
    try {
      lifecycle.requireWritable(reservation.id());
      storageReference = storage.storeQuarantined(objectKey, ownedContent.clone());
    } catch (RuntimeException failure) {
      throw new com.cadence.application.exceptions.EvidenceStorageUnavailableException();
    }
    if (!("quarantine:" + objectKey).equals(storageReference))
      throw new com.cadence.application.exceptions.EvidenceStorageUnavailableException();
    return fileTransactions.execute(
        status -> {
          Brand brand = findLockedActiveBrand(brandId, scope);
          BrandEvidence stored =
              evidence
                  .findByBrandIdAndChecksum(brandId, checksum)
                  .orElseGet(
                      () -> {
                        registrationQuota.requireAvailable(brand.getWorkspace().getId());
                        return evidence.save(
                            BrandEvidence.quarantinedFile(
                                brand,
                                storageReference,
                                checksum,
                                Instant.now(clock),
                                safeFilename,
                                fileType.mediaType()));
                      });
          Registration registration = enqueue(brandId, checksum, stored);
          if (storageReference.equals(stored.getSourceReference())) {
            lifecycle.reference(
                reservation.id(), stored.getId(), storageReference, ownedContent.length);
            writeIntents
                .findById(intentId)
                .orElseThrow()
                .registered(stored.getId(), Instant.now(clock));
          }
          return registration;
        });
  }

  private void requireCleanScan(byte[] content) {
    String expectedChecksum = sha256(content);
    MalwareScanReport report;
    try {
      report = malwareScanner.scan(content);
    } catch (RuntimeException failure) {
      // Provider exceptions may contain credentials, filenames or internal endpoints.
      throw new EvidenceScanUnavailableException();
    }
    if (report == null) throw new EvidenceScanUnavailableException();
    if (report.result() == MalwareScanResult.INFECTED) {
      throw new com.cadence.application.exceptions.EvidenceMalwareRejectedException();
    }
    // Includes a broken adapter returning null: absence of a verdict is never success.
    if (report.result() != MalwareScanResult.CLEAN || !expectedChecksum.equals(sha256(content))) {
      throw new EvidenceScanUnavailableException();
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Registration register(
      UUID brandId, Set<UUID> permittedWorkspaceIds, EvidenceType type, String value) {
    Brand brand = findLockedActiveBrand(brandId, permittedWorkspaceIds);
    if (type == EvidenceType.FILE) {
      throw new IllegalArgumentException("Use the file upload endpoint for FILE evidence");
    }
    String normalized = type == EvidenceType.URL ? normalizeUrl(value) : normalizeText(value);
    String checksum = sha256(type.name() + "\n" + normalized);
    BrandEvidence stored =
        evidence
            .findByBrandIdAndChecksum(brandId, checksum)
            .orElseGet(
                () -> {
                  registrationQuota.requireAvailable(brand.getWorkspace().getId());
                  return evidence.save(
                      new BrandEvidence(brand, type, normalized, checksum, Instant.now(clock)));
                });
    return enqueue(brandId, checksum, stored);
  }

  private Registration enqueue(UUID brandId, String checksum, BrandEvidence stored) {
    DurableJob job =
        jobs.enqueue(
            "BRAND_EVIDENCE_INGEST",
            "evidence:" + stored.getId(),
            "brand-evidence:" + brandId + ":" + checksum,
            3,
            Instant.now(clock));
    return new Registration(stored, job);
  }

  private Brand findActiveBrand(UUID brandId, Set<UUID> permittedWorkspaceIds) {
    Brand brand =
        brands
            .findByIdAndWorkspaceIdIn(brandId, permittedWorkspaceIds)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + brandId));
    requireActive(brand);
    return brand;
  }

  private Brand findLockedActiveBrand(UUID brandId, Set<UUID> scope) {
    Brand snapshot = findActiveBrand(brandId, scope);
    registrationQuota.lockWorkspace(snapshot.getWorkspace().getId());
    Brand brand =
        brands
            .findLockedWithWorkspaceIn(brandId, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + brandId));
    requireActive(brand);
    return brand;
  }

  private void requireActive(Brand brand) {
    if (brand.getStatus() == BrandStatus.ARCHIVED
        || brand.getWorkspace().getStatus() == WorkspaceStatus.ARCHIVED) {
      throw new IllegalStateException("Cannot add evidence to an archived brand or workspace");
    }
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Page<BrandEvidence> findAll(
      UUID brandId, Set<UUID> permittedWorkspaceIds, Pageable pageable) {
    brands
        .findByIdAndWorkspaceIdIn(brandId, permittedWorkspaceIds)
        .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + brandId));
    return evidence.findAllByBrandId(brandId, pageable);
  }

  private String normalizeText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Evidence text must not be blank");
    }
    String normalized = value.strip().replace("\r\n", "\n");
    if (normalized.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("Evidence text must not exceed " + MAX_TEXT_LENGTH);
    }
    return normalized;
  }

  private String normalizeUrl(String value) {
    String raw = normalizeText(value);
    URI uri;
    try {
      uri = URI.create(raw).normalize();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Evidence URL is invalid", exception);
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    if (!(scheme.equals("https") || scheme.equals("http"))
        || host.isBlank()
        || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Evidence URL must be an HTTP(S) URL without credentials");
    }
    if (isLocalHost(host)) {
      throw new IllegalArgumentException("Evidence URL cannot target a local or private host");
    }
    String path = uri.getPath();
    try {
      return new URI(
              scheme,
              null,
              host,
              uri.getPort(),
              path == null || path.isBlank() ? "/" : path,
              uri.getQuery(),
              null)
          .toASCIIString();
    } catch (java.net.URISyntaxException exception) {
      throw new IllegalArgumentException("Evidence URL is invalid", exception);
    }
  }

  private boolean isLocalHost(String host) {
    return host.equals("localhost")
        || host.endsWith(".localhost")
        || host.endsWith(".local")
        || host.equals("0.0.0.0")
        || host.equals("::1")
        || host.startsWith("127.")
        || host.startsWith("10.")
        || host.startsWith("192.168.")
        || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
  }

  private String sha256(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private FileType detectFileType(byte[] content) {
    if (startsWith(content, new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D})) {
      return new FileType("application/pdf", "pdf");
    }
    if (startsWith(content, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
      return new FileType("image/png", "png");
    }
    if (startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
      return new FileType("image/jpeg", "jpg");
    }
    throw new IllegalArgumentException("Only PDF, PNG, and JPEG evidence files are supported");
  }

  private boolean startsWith(byte[] content, byte[] signature) {
    if (content.length < signature.length) {
      return false;
    }
    for (int index = 0; index < signature.length; index++) {
      if (content[index] != signature[index]) {
        return false;
      }
    }
    return true;
  }

  private String safeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new IllegalArgumentException("Evidence filename must not be blank");
    }
    String normalized = originalFilename.replace('\\', '/');
    String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    if (name.isBlank() || name.length() > 255 || name.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Evidence filename is invalid");
    }
    return name;
  }

  private record FileType(String mediaType, String extension) {}
}
