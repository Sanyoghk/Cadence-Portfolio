package com.cadence.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** Immutable receipt; original provider output remains in the sealed operation. */
@Entity
@Table(name = "content_generation_batches")
public class ContentGenerationBatch extends BaseEntity {
  @Column(name = "operation_id", nullable = false, updatable = false, unique = true)
  private UUID operationId;

  @Column(name = "attempt_number", nullable = false, updatable = false)
  private int attemptNumber;

  @Column(name = "output_checksum", nullable = false, length = 64, updatable = false)
  private String outputChecksum;

  protected ContentGenerationBatch() {}

  public ContentGenerationBatch(UUID operationId, int attempt, String outputChecksum) {
    if (operationId == null
        || attempt < 1
        || outputChecksum == null
        || !outputChecksum.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid Content receipt");
    this.operationId = operationId;
    this.attemptNumber = attempt;
    this.outputChecksum = outputChecksum;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public String getOutputChecksum() {
    return outputChecksum;
  }
}
