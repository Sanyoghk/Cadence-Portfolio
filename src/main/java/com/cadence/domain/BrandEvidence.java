package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "brand_evidence",
    uniqueConstraints = @UniqueConstraint(columnNames = {"brand_id", "checksum"}))
public class BrandEvidence extends BaseEntity {

  @ManyToOne
  @JoinColumn(name = "brand_id", nullable = false)
  private Brand brand;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EvidenceType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EvidenceStatus status;

  @Column(name = "source_reference", nullable = false, length = 20000)
  private String sourceReference;

  @Column(nullable = false, length = 64)
  private String checksum;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @Column(length = 1000)
  private String warning;

  @Column(name = "original_file_name", length = 255)
  private String originalFileName;

  @Column(name = "media_type", length = 100)
  private String mediaType;

  @Column(name = "fetched_reference", length = 500)
  private String fetchedReference;

  @Column(name = "fetched_checksum", length = 64)
  private String fetchedChecksum;

  @Column(name = "fetched_media_type", length = 100)
  private String fetchedMediaType;

  @Column(name = "fetched_at")
  private Instant fetchedAt;

  @Column(name = "extracted_title", length = 300)
  private String extractedTitle;

  @Column(name = "extracted_description", length = 2000)
  private String extractedDescription;

  @Column(name = "extracted_text", length = 16000)
  private String extractedText;

  @Column(name = "extraction_warnings", length = 500)
  private String extractionWarnings;

  @Column(name = "extraction_version", length = 32)
  private String extractionVersion;

  @Column(name = "extracted_at")
  private Instant extractedAt;

  public void recordWebsiteMetadata(WebsiteMetadata metadata, Instant now) {
    if (type != EvidenceType.URL
        || status != EvidenceStatus.FETCHED
        || extractedAt != null
        || metadata == null
        || now == null
        || now.isBefore(fetchedAt))
      throw new IllegalStateException("Website metadata requires fetched URL evidence");
    extractedTitle = metadata.title();
    extractedDescription = metadata.description();
    extractedText = metadata.text();
    extractionWarnings = metadata.warnings();
    extractionVersion = metadata.version();
    extractedAt = now;
    warning = "Quarantined source; metadata extracted; content safety review is pending";
  }

  public WebsiteMetadata getWebsiteMetadata() {
    return extractedAt == null
        ? null
        : new WebsiteMetadata(
            extractedTitle,
            extractedDescription,
            extractedText,
            extractionWarnings,
            extractionVersion);
  }

  public Instant getExtractedAt() {
    return extractedAt;
  }

  public void recordFetched(String reference, String checksum, String mediaType, Instant now) {
    requirePendingUrl();
    if (reference == null || !reference.startsWith("quarantine:"))
      throw new IllegalArgumentException("Fetched evidence must be quarantined");
    if (checksum == null || !checksum.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid fetched checksum");
    if (!java.util.Set.of("text/html", "text/plain").contains(mediaType) || now == null)
      throw new IllegalArgumentException("Invalid fetched metadata");
    fetchedReference = DomainText.required(reference, "Fetched reference", 500);
    fetchedChecksum = checksum;
    fetchedMediaType = mediaType;
    fetchedAt = now;
    warning = "Quarantined source; extraction and content safety review are pending";
    status = EvidenceStatus.FETCHED;
  }

  public void failUrlFetch() {
    requirePendingUrl();
    warning = "Source could not be fetched";
    status = EvidenceStatus.FAILED;
  }

  public void failFileScan() {
    failMalwareScan();
  }

  public void failMalwareScan() {
    if (!((type == EvidenceType.FILE && status == EvidenceStatus.PENDING)
        || (type == EvidenceType.URL && status == EvidenceStatus.FETCHED)))
      throw new IllegalStateException("Only quarantined evidence can fail malware scanning");
    status = EvidenceStatus.FAILED;
    warning = "Quarantined evidence could not pass malware scanning";
  }

  private void requirePendingUrl() {
    if (type != EvidenceType.URL || status != EvidenceStatus.PENDING)
      throw new IllegalStateException("Only pending URL evidence can complete fetching");
  }

  public String getFetchedReference() {
    return fetchedReference;
  }

  public String getFetchedChecksum() {
    return fetchedChecksum;
  }

  public String getFetchedMediaType() {
    return fetchedMediaType;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  protected BrandEvidence() {}

  public BrandEvidence(
      Brand brand, EvidenceType type, String sourceReference, String checksum, Instant capturedAt) {
    if (brand == null || type == null || capturedAt == null) {
      throw new IllegalArgumentException("Evidence brand, type, and capture time are required");
    }
    this.brand = brand;
    this.type = type;
    this.sourceReference =
        DomainText.required(
            sourceReference, "Evidence source", type == EvidenceType.TEXT ? 20000 : 4096);
    this.checksum = DomainText.required(checksum, "Evidence checksum", 64);
    this.capturedAt = capturedAt;
    this.status = EvidenceStatus.PENDING;
  }

  public static BrandEvidence quarantinedFile(
      Brand brand,
      String storageReference,
      String checksum,
      Instant capturedAt,
      String originalFileName,
      String mediaType) {
    BrandEvidence evidence =
        new BrandEvidence(brand, EvidenceType.FILE, storageReference, checksum, capturedAt);
    evidence.originalFileName = DomainText.required(originalFileName, "Evidence filename", 255);
    evidence.mediaType = DomainText.required(mediaType, "Evidence media type", 100);
    return evidence;
  }

  public Brand getBrand() {
    return brand;
  }

  public EvidenceType getType() {
    return type;
  }

  public EvidenceStatus getStatus() {
    return status;
  }

  public String getSourceReference() {
    return sourceReference;
  }

  public String getChecksum() {
    return checksum;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public String getWarning() {
    return warning;
  }

  public String getOriginalFileName() {
    return originalFileName;
  }

  public String getMediaType() {
    return mediaType;
  }
}
