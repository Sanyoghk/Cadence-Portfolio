package com.cadence.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable raw human assertion and durable publication receipt. Raw data never leaves this
 * boundary.
 */
@Entity
@Table(name = "metric_imports")
public class MetricImport extends BaseEntity {
  @ManyToOne(optional = false)
  @JoinColumn(name = "brand_id", updatable = false)
  private Brand brand;

  @OneToOne(optional = false)
  @JoinColumn(name = "job_id", updatable = false)
  private DurableJob job;

  @Column(updatable = false, nullable = false)
  private UUID actorId;

  @Column(updatable = false, nullable = false, length = 128)
  private String clientKey;

  @Column(updatable = false, nullable = false, length = 16)
  private String origin;

  @JdbcTypeCode(SqlTypes.VARBINARY)
  @Column(updatable = false, nullable = false, columnDefinition = "bytea")
  private byte[] rawInput;

  @Column(updatable = false, nullable = false, length = 64)
  private String rawChecksum;

  @Column(updatable = false, nullable = false, length = 64)
  private String requestDigest;

  @Column(updatable = false, nullable = false)
  private int rowCount;

  @Column(updatable = false, nullable = false)
  private Instant submittedAt;

  @Column(updatable = false, nullable = false)
  private Instant deadline;

  private Instant completedAt;
  private Integer publishedAttempt;

  @Column(length = 255)
  private String publishedWorker;

  @ElementCollection
  @CollectionTable(name = "metric_import_results", joinColumns = @JoinColumn(name = "import_id"))
  @OrderColumn(name = "row_number")
  @Column(name = "observation_id", nullable = false)
  private List<UUID> results = new ArrayList<>();

  protected MetricImport() {}

  public MetricImport(
      Brand b,
      DurableJob j,
      UUID actor,
      String key,
      String origin,
      byte[] raw,
      int rows,
      Instant now) {
    if (b == null
        || j == null
        || actor == null
        || key == null
        || !key.matches("[A-Za-z0-9_-]{16,128}")
        || origin == null
        || !Set.of("MANUAL_JSON", "CSV").contains(origin)
        || raw == null
        || raw.length < 1
        || raw.length > 131072
        || rows < 1
        || rows > 200
        || now == null) throw new IllegalArgumentException("Invalid metric import");
    brand = b;
    job = j;
    actorId = actor;
    clientKey = key;
    this.origin = origin;
    rawInput = raw.clone();
    rawChecksum = MetricRow.sha(raw);
    requestDigest = digest(origin, raw);
    rowCount = rows;
    submittedAt = now;
    deadline = now.plusSeconds(300);
  }

  public static String digest(String origin, byte[] raw) {
    return MetricRow.sha(
        ("metric-import-v1|" + origin + "|" + MetricRow.sha(raw))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public void publish(List<UUID> ids, String worker, int attempt, Instant now) {
    if (completedAt != null
        || ids == null
        || ids.size() != rowCount
        || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size()
        || worker == null
        || worker.isBlank()
        || worker.length() > 255
        || attempt < 1
        || attempt > 3
        || now == null
        || now.isBefore(submittedAt)
        || !now.isBefore(deadline)) throw new IllegalStateException("Invalid metric publication");
    results.addAll(ids);
    publishedWorker = worker;
    publishedAttempt = attempt;
    completedAt = now;
  }

  public Brand getBrand() {
    return brand;
  }

  public DurableJob getJob() {
    return job;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getClientKey() {
    return clientKey;
  }

  public String getOrigin() {
    return origin;
  }

  public byte[] raw() {
    return rawInput.clone();
  }

  public String getRawChecksum() {
    return rawChecksum;
  }

  public String getRequestDigest() {
    return requestDigest;
  }

  public int getRowCount() {
    return rowCount;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public List<UUID> getResults() {
    return List.copyOf(results);
  }
}
