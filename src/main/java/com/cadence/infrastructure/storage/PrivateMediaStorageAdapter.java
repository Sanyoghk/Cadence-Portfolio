package com.cadence.infrastructure.storage;

import com.cadence.application.exceptions.MediaUnavailableException;
import com.cadence.application.ports.*;
import com.cadence.domain.MediaObjectKey;
import org.springframework.stereotype.Component;

/** Separate capability from quarantine; delegates only to explicitly reviewed private adapters. */
@Component
public class PrivateMediaStorageAdapter implements PrivateMediaStorage {
  private final EvidenceObjectStorage storage;

  public PrivateMediaStorageAdapter(EvidenceObjectStorage storage) {
    this.storage = storage;
  }

  public void storeMedia(MediaObjectKey key, byte[] content) {
    if (storage instanceof FileSystemEvidenceObjectStorage files) files.storeMedia(key, content);
    else if (storage instanceof S3EvidenceObjectStorage s3) s3.storeMedia(key, content);
    else throw new MediaUnavailableException();
  }

  public byte[] readMedia(MediaObjectKey key, int maxBytes) {
    if (storage instanceof FileSystemEvidenceObjectStorage files)
      return files.readMedia(key, maxBytes);
    if (storage instanceof S3EvidenceObjectStorage s3) return s3.readMedia(key, maxBytes);
    throw new MediaUnavailableException();
  }
}
