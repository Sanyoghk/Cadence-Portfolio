package com.cadence.infrastructure.storage;

import com.cadence.application.ports.EvidenceMalwareScanner;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.ports.MalwareScanReport;
import com.cadence.application.ports.MalwareScanResult;
import com.cadence.application.ports.QuarantineReadFailure;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod & !s3 & !clamd")
public class ProductionEvidenceSafetyGate implements EvidenceObjectStorage, EvidenceMalwareScanner {

  private static final String MESSAGE =
      "Production file evidence is disabled until object storage and malware scanning are configured";

  @Override
  public String storeQuarantined(String objectKey, byte[] content) {
    throw new IllegalStateException(MESSAGE);
  }

  @Override
  public MalwareScanReport scan(byte[] content) {
    return new MalwareScanReport(
        MalwareScanResult.UNAVAILABLE, "production-safety-gate", "disabled");
  }

  @Override
  public byte[] readQuarantined(String reference, int maxBytes) {
    throw new QuarantineReadFailure(QuarantineReadFailure.Code.UNAVAILABLE);
  }
}
