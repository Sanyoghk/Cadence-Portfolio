package com.cadence.application.ports;

import com.cadence.domain.WebsiteMetadata;

public interface WebsiteMetadataExtractor {
  WebsiteMetadata extract(byte[] content, String mediaType);

  /** Strictly decoded UTF-8 rendering input; still untrusted and only for the isolated renderer. */
  default byte[] decodeForRendering(byte[] content, String mediaType) {
    throw new WebsiteExtractionFailure();
  }
}
