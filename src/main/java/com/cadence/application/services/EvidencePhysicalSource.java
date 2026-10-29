package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.domain.*;
import java.util.UUID;

/** Immutable physical witness; private references never enter the model command. */
public record EvidencePhysicalSource(
    UUID admissionId,
    UUID objectId,
    long objectVersion,
    String reference,
    PdfReviewContent pdf,
    UrlReviewContent url) {
  public EvidencePhysicalSource {
    if ((pdf == null) == (url == null))
      throw new IllegalArgumentException("Exactly one physical kind required");
  }

  public void verify(EvidenceObjectStorage storage, WebsiteMetadataExtractor extractor) {
    long size = pdf != null ? pdf.sourceBytes() : url.sourceBytes();
    byte[] bytes = storage.readQuarantined(reference, Math.toIntExact(size));
    if (pdf != null) pdf.verifyBytes(bytes);
    else {
      url.verifyBytes(bytes);
      if (!url.metadata().equals(extractor.extract(bytes, url.mediaType())))
        throw new IllegalStateException("URL extraction changed");
    }
  }
}
