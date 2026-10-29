package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.GenericContainer;

/** Shared, input-free kernel/LSM preflight before real renderer/provider payloads are tested. */
final class RendererContainerSupport {
  private static final String PROFILE = "cadence-evidence-renderer";

  private RendererContainerSupport() {}

  static List<String> securityOptions(String seccomp) {
    var options = new ArrayList<>(List.of("no-new-privileges", "seccomp=" + seccomp));
    if (requestedProfile()) options.add("apparmor=" + PROFILE);
    return options;
  }

  static void verifyNamespaceBoundary(GenericContainer<?> container) throws Exception {
    var command =
        new ArrayList<>(List.of("python3", "-I", "/opt/evidence-renderer/namespace_probe.py"));
    if (requestedProfile()) command.add(PROFILE);
    var result = container.execInContainer(command.toArray(String[]::new));
    String diagnostic = result.getStderr().strip();
    if (!diagnostic.matches("namespace-probe failed: [a-z-]+(?: exit=-?[0-9]{1,3})?"))
      diagnostic = "unclassified-safe-diagnostic";
    assertThat(result.getExitCode())
        .withFailMessage(
            "Renderer kernel/LSM preflight failed: exit=%d, %s", result.getExitCode(), diagnostic)
        .isZero();
    assertThat(result.getStdout()).isEqualTo("namespace-probe ok\n");
  }

  private static boolean requestedProfile() {
    String configured = System.getenv("CADENCE_RENDERER_APPARMOR_PROFILE");
    if (configured == null || configured.isBlank()) return false;
    if (!configured.equals(PROFILE))
      throw new IllegalStateException("Only the reviewed renderer AppArmor profile is permitted");
    return true;
  }
}
