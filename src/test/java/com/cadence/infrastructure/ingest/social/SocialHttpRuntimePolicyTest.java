package com.cadence.infrastructure.ingest.social;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SocialHttpRuntimePolicyTest {
  private static final Map<String, String> PROPERTIES =
      Map.of(
          "jdk.httpclient.disableRetryConnect",
          "true",
          "jdk.httpclient.redirects.retrylimit",
          "1",
          "jdk.httpclient.enableAllMethodRetry",
          "false");
  private static final List<String> ARGUMENTS =
      PROPERTIES.entrySet().stream()
          .map(entry -> "-D" + entry.getKey() + "=" + entry.getValue())
          .toList();

  @Test
  void exactStartupFlagsAndEffectivePropertiesPermitBoundedProviderRequests() {
    assertThatCode(() -> SocialHttpRuntimePolicy.validate(true, ARGUMENTS, PROPERTIES))
        .doesNotThrowAnyException();
    assertThatCode(() -> SocialHttpRuntimePolicy.validate(false, List.of(), Map.of()))
        .doesNotThrowAnyException();
    assertThatCode(() -> SocialHttpRuntimePolicy.require(true)).doesNotThrowAnyException();
  }

  @Test
  void missingStartupFlagsRejectLateSystemPropertyChangesAndPartialConfiguration() {
    for (String argument : ARGUMENTS) {
      List<String> partial = new ArrayList<>(ARGUMENTS);
      partial.remove(argument);
      assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, partial, PROPERTIES))
          .hasMessage("Invalid social HTTP runtime policy")
          .hasNoCause();
    }
    // Correct properties alone cannot prove they were set before JDK HTTP static initialization.
    assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, List.of(), PROPERTIES))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changedEffectivePropertiesAndRetryEnablingValuesFailClosedWithoutDisclosure() {
    for (String key : PROPERTIES.keySet()) {
      Map<String, String> changed = new HashMap<>(PROPERTIES);
      changed.put(key, "unexpected-sensitive-value");
      assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, ARGUMENTS, changed))
          .hasMessage("Invalid social HTTP runtime policy")
          .hasNoCause()
          .hasMessageNotContaining("unexpected-sensitive-value");
    }
  }

  @Test
  void diagnosticLoggingCannotExposeBearerHeadersEvenWhenClearedAfterStartup() {
    for (String property :
        List.of(
            "jdk.httpclient.HttpClient.log",
            "jdk.internal.httpclient.debug",
            "jdk.internal.httpclient.hpack.debug")) {
      for (String value : List.of("headers", "content", "all", "true", "err,out", "none")) {
        Map<String, String> changed = new HashMap<>(PROPERTIES);
        changed.put(property, value);
        assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, ARGUMENTS, changed))
            .hasMessage("Invalid social HTTP runtime policy")
            .hasNoCause();
        List<String> startup = new ArrayList<>(ARGUMENTS);
        startup.add("-D" + property + "=" + value);
        assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, startup, PROPERTIES))
            .hasMessage("Invalid social HTTP runtime policy")
            .hasNoCause();
      }
      Map<String, String> empty = new HashMap<>(PROPERTIES);
      empty.put(property, "");
      List<String> startup = new ArrayList<>(ARGUMENTS);
      startup.add("-D" + property + "=");
      assertThatCode(() -> SocialHttpRuntimePolicy.validate(true, startup, empty))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void disablingTlsHostnameVerificationIsRejectedIncludingEmptyAndLateChangedProperty() {
    String property = "jdk.internal.httpclient.disableHostnameVerification";
    for (String value : List.of("", "true", "TRUE", "unexpected-sensitive-value")) {
      Map<String, String> changed = new HashMap<>(PROPERTIES);
      changed.put(property, value);
      assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, ARGUMENTS, changed))
          .hasMessage("Invalid social HTTP runtime policy")
          .hasNoCause();
      List<String> startup = new ArrayList<>(ARGUMENTS);
      startup.add("-D" + property + "=" + value);
      assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, startup, PROPERTIES))
          .hasMessage("Invalid social HTTP runtime policy")
          .hasNoCause();
    }
    List<String> implicitEmpty = new ArrayList<>(ARGUMENTS);
    implicitEmpty.add("-D" + property);
    assertThatThrownBy(() -> SocialHttpRuntimePolicy.validate(true, implicitEmpty, PROPERTIES))
        .isInstanceOf(IllegalArgumentException.class);
    Map<String, String> verified = new HashMap<>(PROPERTIES);
    verified.put(property, "false");
    List<String> startup = new ArrayList<>(ARGUMENTS);
    startup.add("-D" + property + "=false");
    assertThatCode(() -> SocialHttpRuntimePolicy.validate(true, startup, verified))
        .doesNotThrowAnyException();
  }
}
