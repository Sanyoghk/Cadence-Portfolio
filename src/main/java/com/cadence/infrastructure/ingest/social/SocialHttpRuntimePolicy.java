package com.cadence.infrastructure.ingest.social;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Provider cost budgets must include HTTP implementation retries, not just application calls. */
final class SocialHttpRuntimePolicy {
  private static final Map<String, String> REQUIRED =
      Map.of(
          "jdk.httpclient.disableRetryConnect", "true",
          "jdk.httpclient.redirects.retrylimit", "1",
          "jdk.httpclient.enableAllMethodRetry", "false");
  private static final Set<String> DIAGNOSTIC_PROPERTIES =
      Set.of(
          "jdk.httpclient.HttpClient.log",
          "jdk.internal.httpclient.debug",
          "jdk.internal.httpclient.hpack.debug");
  private static final String DISABLE_HOSTNAME_VERIFICATION =
      "jdk.internal.httpclient.disableHostnameVerification";

  private SocialHttpRuntimePolicy() {}

  static void require(boolean enabled) {
    if (!enabled) return;
    Map<String, String> properties = new HashMap<>();
    REQUIRED.keySet().forEach(key -> properties.put(key, System.getProperty(key, "")));
    DIAGNOSTIC_PROPERTIES.forEach(key -> properties.put(key, System.getProperty(key, "")));
    String hostnamePolicy = System.getProperty(DISABLE_HOSTNAME_VERIFICATION);
    if (hostnamePolicy != null) properties.put(DISABLE_HOSTNAME_VERIFICATION, hostnamePolicy);
    validate(true, ManagementFactory.getRuntimeMXBean().getInputArguments(), properties);
  }

  static void validate(
      boolean enabled, List<String> startupArguments, Map<String, String> properties) {
    if (!enabled) return;
    if (startupArguments == null || properties == null) throw invalid();
    for (var requirement : REQUIRED.entrySet()) {
      if (!requirement.getValue().equals(properties.get(requirement.getKey()))
          || !startupArguments.contains("-D" + requirement.getKey() + "=" + requirement.getValue()))
        throw invalid();
    }
    // A property cleared after JDK static initialization cannot disable an already active logger.
    for (String property : DIAGNOSTIC_PROPERTIES) {
      if (!properties.getOrDefault(property, "").isEmpty()
          || startupArguments.stream()
              .anyMatch(
                  argument ->
                      argument.startsWith("-D" + property + "=")
                          && !argument.equals("-D" + property + "="))) throw invalid();
    }
    // In JDK 21 even an empty disableHostnameVerification property disables verification.
    if ((properties.containsKey(DISABLE_HOSTNAME_VERIFICATION)
            && !"false".equals(properties.get(DISABLE_HOSTNAME_VERIFICATION)))
        || startupArguments.stream()
            .anyMatch(
                argument ->
                    argument.equals("-D" + DISABLE_HOSTNAME_VERIFICATION)
                        || (argument.startsWith("-D" + DISABLE_HOSTNAME_VERIFICATION + "=")
                            && !argument.equals("-D" + DISABLE_HOSTNAME_VERIFICATION + "=false"))))
      throw invalid();
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid social HTTP runtime policy");
  }
}
