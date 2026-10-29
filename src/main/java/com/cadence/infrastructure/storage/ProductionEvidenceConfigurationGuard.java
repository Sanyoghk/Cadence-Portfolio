package com.cadence.infrastructure.storage;

import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Activation is explicit; profile combinations cannot import development providers into prod. */
@Component
public final class ProductionEvidenceConfigurationGuard {
  public ProductionEvidenceConfigurationGuard(Environment environment) {
    Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
    if (environment.getProperty("cadence.evidence.artifacts-enabled", Boolean.class, false)
        && !profiles.contains("renderer"))
      throw new IllegalStateException("Evidence artifacts require the isolated renderer profile");
    if (!profiles.contains("prod")) return;
    if (profiles.contains("local")
        || profiles.contains("test")
        || profiles.contains("postgres-test"))
      throw new IllegalStateException("Production and development profiles cannot be combined");
    boolean providers =
        profiles.contains("s3") || profiles.contains("clamd") || profiles.contains("renderer");
    boolean workers =
        environment.getProperty("cadence.evidence.url-worker-enabled", Boolean.class, false)
            || environment.getProperty(
                "cadence.evidence.file-scan-worker-enabled", Boolean.class, false)
            || environment.getProperty("cadence.evidence.cleanup-enabled", Boolean.class, false)
            || environment.getProperty("cadence.evidence.artifacts-enabled", Boolean.class, false);
    if (providers || workers) {
      if (!(profiles.contains("s3") && profiles.contains("clamd")))
        throw new IllegalStateException("Production evidence requires both S3 and clamd profiles");
      for (String gate :
          Set.of(
              "production-approved", "egress-policy-acknowledged", "retention-policy-acknowledged"))
        if (!environment.getProperty("cadence.evidence." + gate, Boolean.class, false))
          throw new IllegalStateException(
              "Production evidence activation requires explicit operational approvals");
    }
  }
}
