package com.cadence.infrastructure.storage;

import com.cadence.application.ports.EvidenceRenderFailure;
import com.cadence.application.ports.EvidenceRenderer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Missing operational configuration cannot invoke an in-process parser or browser. */
@Component
@Profile("!renderer")
public final class DisabledEvidenceRenderer implements EvidenceRenderer {
  @Override
  public Screenshot screenshot(byte[] utf8Html) {
    throw new EvidenceRenderFailure(EvidenceRenderFailure.Code.UNAVAILABLE);
  }

  @Override
  public DocumentText extractPdf(byte[] pdf) {
    throw new EvidenceRenderFailure(EvidenceRenderFailure.Code.UNAVAILABLE);
  }
}
