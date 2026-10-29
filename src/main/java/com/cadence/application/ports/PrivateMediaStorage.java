package com.cadence.application.ports;

import com.cadence.domain.MediaObjectKey;

/** Private retained media, never quarantine references or client-selected URLs/filenames. */
public interface PrivateMediaStorage {
  void storeMedia(MediaObjectKey key, byte[] content);

  byte[] readMedia(MediaObjectKey key, int maxBytes);
  // No delete capability until a separate retention/deletion policy is approved.
}
