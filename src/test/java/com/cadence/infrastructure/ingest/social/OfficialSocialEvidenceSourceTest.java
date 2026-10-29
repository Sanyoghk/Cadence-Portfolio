package com.cadence.infrastructure.ingest.social;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.exceptions.SocialEvidenceUnavailableException;
import com.cadence.domain.SocialEvidenceProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class OfficialSocialEvidenceSourceTest {
  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
  private static final UUID BRAND = UUID.randomUUID();
  private static final String TOKEN = "fixture-provider-token-not-a-real-secret";
  private final SocialProviderHttpTransport transport = mock(SocialProviderHttpTransport.class);
  private SocialEvidenceProperties properties;

  @BeforeEach
  void setup() throws Exception {
    properties = new SocialEvidenceProperties();
    properties.setEnabled(true);
    properties.setBindings(
        List.of(binding(SocialEvidenceProvider.INSTAGRAM), binding(SocialEvidenceProvider.TIKTOK)));
    when(transport.request(any(), eq(TOKEN), any(), any()))
        .thenAnswer(
            call -> {
              URI uri = call.getArgument(0);
              return fixture(
                  uri.getHost().equals("graph.facebook.com")
                      ? (uri.getPath().endsWith("/media") ? "instagram-media" : "instagram-profile")
                      : (uri.getPath().contains("/video/") ? "tiktok-videos" : "tiktok-profile"));
            });
  }

  @Test
  void sameAccountPolicyIsLocalAndRejectsRebindingExpiryScopesAndRevocation() {
    var adapter = source();
    var binding = properties.getBindings().get(0);
    assertThat(
            adapter.requireApprovedAccount(
                BRAND, SocialEvidenceProvider.INSTAGRAM, binding.getAccountId()))
        .isEqualTo(binding.getExpiresAt());
    unavailable(
        () ->
            adapter.requireApprovedAccount(
                BRAND, SocialEvidenceProvider.INSTAGRAM, "different-account"));
    String original = binding.getAccountId();
    binding.setAccountId("999");
    unavailable(
        () -> adapter.requireApprovedAccount(BRAND, SocialEvidenceProvider.INSTAGRAM, original));
    binding.setAccountId(original);
    binding.setExpiresAt(NOW.plusSeconds(30));
    unavailable(
        () -> adapter.requireApprovedAccount(BRAND, SocialEvidenceProvider.INSTAGRAM, original));
    binding.setExpiresAt(NOW.plusSeconds(3600));
    binding.setScopes(Set.of("instagram_basic"));
    unavailable(
        () -> adapter.requireApprovedAccount(BRAND, SocialEvidenceProvider.INSTAGRAM, original));
    binding.setScopes(Set.of("instagram_basic", "pages_read_engagement"));
    binding.setApproved(false);
    unavailable(
        () -> adapter.requireApprovedAccount(BRAND, SocialEvidenceProvider.INSTAGRAM, original));
    verifyNoInteractions(transport);
  }

  @Test
  void officialInstagramContractNormalizesAllowedFieldsAndNeverFollowsProviderUrls() {
    var snapshot = source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM);
    assertThat(snapshot.canonicalJson())
        .contains("approved-social-v1", "INSTAGRAM", "17841400000000001", "2026-09-28T10:00:00Z")
        .doesNotContain(
            TOKEN,
            "unselected-fixture",
            "access_token",
            "169.254",
            "127.0.0.1",
            "media_url",
            "paging");
    ArgumentCaptor<URI> requests = ArgumentCaptor.forClass(URI.class);
    verify(transport, times(2)).request(requests.capture(), eq(TOKEN), isNull(), any());
    assertThat(requests.getAllValues())
        .allSatisfy(
            uri -> {
              assertThat(uri.getHost()).isEqualTo("graph.facebook.com");
              assertThat(uri.getScheme()).isEqualTo("https");
              assertThat(uri.toString()).doesNotContain(TOKEN, "access_token");
            });
    assertThat(requests.getAllValues().get(1).getRawQuery()).contains("limit=20");
  }

  @Test
  void officialTikTokContractUsesBearerTokenSeparateFromUriAndLimitsOnePage() {
    var snapshot = source().fetch(BRAND, SocialEvidenceProvider.TIKTOK);
    assertThat(snapshot.canonicalJson())
        .contains("TIKTOK", "fixture-open-id", "Creative ideas")
        .doesNotContain("avatar_url", "share_url", "cursor", "has_more", TOKEN);
    verify(transport)
        .request(
            argThat(uri -> uri.getPath().equals("/v2/user/info/")), eq(TOKEN), isNull(), any());
    verify(transport)
        .request(
            argThat(uri -> uri.getPath().equals("/v2/video/list/")),
            eq(TOKEN),
            eq("{\"max_count\":20}"),
            any());
    verifyNoMoreInteractions(transport);
  }

  @Test
  void advancingRetrievalClockDoesNotChangeIdenticalProviderContentIdentity() {
    var time = new java.util.concurrent.atomic.AtomicReference<>(NOW);
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenAnswer(call -> time.get());
    var source = new OfficialSocialEvidenceSource(properties, transport, clock);
    for (var provider : SocialEvidenceProvider.values()) {
      time.set(NOW);
      var first = source.fetch(BRAND, provider);
      time.set(NOW.plusSeconds(15));
      var retry = source.fetch(BRAND, provider);
      assertThat(first.retrievedAt()).isEqualTo(NOW);
      assertThat(retry.retrievedAt()).isEqualTo(NOW.plusSeconds(15));
      assertThat(retry.canonicalJson())
          .isEqualTo(first.canonicalJson())
          .doesNotContain("retrievedAt", NOW.toString(), NOW.plusSeconds(15).toString());
    }
    verify(transport, times(8)).request(any(), eq(TOKEN), any(), any());
  }

  @Test
  void disabledUnknownBrandUnapprovedMissingScopeAndExpiredBindingMakeNoExternalCalls() {
    properties.setEnabled(false);
    unavailable(() -> source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
    properties.setEnabled(true);
    unavailable(() -> source().fetch(UUID.randomUUID(), SocialEvidenceProvider.INSTAGRAM));
    properties.getBindings().get(0).setExpiresAt(NOW.minusSeconds(1));
    unavailable(() -> source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
    properties.getBindings().get(0).setExpiresAt(NOW.plusSeconds(3600));
    properties.getBindings().get(0).setApproved(false);
    assertThatThrownBy(this::source).hasMessage("Invalid approved social evidence configuration");
    properties.getBindings().get(0).setApproved(true);
    properties.getBindings().get(0).setScopes(Set.of("instagram_basic"));
    assertThatThrownBy(this::source).hasMessage("Invalid approved social evidence configuration");
    verifyNoInteractions(transport);
  }

  @Test
  void approvalPreflightIsLocalAndRechecksMissingExpiredRevokedScopesAndRuntimePolicy() {
    var source = source();
    assertThatCode(() -> source.requireApprovedBinding(BRAND, SocialEvidenceProvider.INSTAGRAM))
        .doesNotThrowAnyException();
    unavailable(
        () -> source.requireApprovedBinding(UUID.randomUUID(), SocialEvidenceProvider.INSTAGRAM));
    var binding = properties.getBindings().get(0);
    binding.setExpiresAt(NOW.plusSeconds(5));
    unavailable(() -> source.requireApprovedBinding(BRAND, SocialEvidenceProvider.INSTAGRAM));
    binding.setExpiresAt(NOW.plusSeconds(3600));
    binding.setApproved(false);
    unavailable(() -> source.requireApprovedBinding(BRAND, SocialEvidenceProvider.INSTAGRAM));
    binding.setApproved(true);
    binding.setScopes(Set.of("instagram_basic"));
    unavailable(() -> source.requireApprovedBinding(BRAND, SocialEvidenceProvider.INSTAGRAM));
    binding.setScopes(SocialEvidenceProperties.requiredScopes(SocialEvidenceProvider.INSTAGRAM));
    properties.setEnabled(false);
    unavailable(() -> source.requireApprovedBinding(BRAND, SocialEvidenceProvider.INSTAGRAM));
    properties.setEnabled(true);
    String previous = System.getProperty("jdk.httpclient.HttpClient.log");
    try {
      System.setProperty("jdk.httpclient.HttpClient.log", "headers");
      unavailable(() -> source.requireApprovedBinding(BRAND, SocialEvidenceProvider.INSTAGRAM));
    } finally {
      if (previous == null) System.clearProperty("jdk.httpclient.HttpClient.log");
      else System.setProperty("jdk.httpclient.HttpClient.log", previous);
    }
    verifyNoInteractions(transport);
  }

  @Test
  void malformedOversizedResponseProviderErrorsAndWrongAccountAreRejectedWithoutLeak() {
    for (byte[] body :
        List.of(
            new byte[] {(byte) 0xc3, 0x28},
            new byte[65537],
            bytes("[]"),
            bytes(
                "{\"id\":\"other-account\",\"id\":\"17841400000000001\",\"username\":\"duplicate\"}"),
            bytes("{\"id\":\"17841400000000001\",\"username\":\"trailing\"} {}"),
            bytes("{\"nested\":" + "[".repeat(33) + "0" + "]".repeat(33) + "}"),
            bytes("{\"error\":{\"message\":\"" + TOKEN + "\"}}"),
            bytes("{\"id\":\"other-account\",\"username\":\"wrong\"}"))) {
      doReturn(body).when(transport).request(any(), anyString(), any(), any());
      unavailable(() -> source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
    }
  }

  @Test
  void actualTokenInIgnoredPagingUrlIsDiscardedAndNeverFollowedOrPublished() throws Exception {
    doAnswer(
            call -> {
              URI uri = call.getArgument(0);
              return uri.getPath().endsWith("/media")
                  ? new String(fixture("instagram-media"), StandardCharsets.UTF_8)
                      .replace("must-not-follow", TOKEN)
                      .getBytes(StandardCharsets.UTF_8)
                  : fixture("instagram-profile");
            })
        .when(transport)
        .request(any(), eq(TOKEN), any(), any());
    var snapshot = source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM);
    assertThat(snapshot.canonicalJson()).doesNotContain(TOKEN, "paging", "access_token", "169.254");
    verify(transport, times(2))
        .request(
            argThat(uri -> "graph.facebook.com".equals(uri.getHost())), eq(TOKEN), isNull(), any());
    verifyNoMoreInteractions(transport);
  }

  @Test
  void tokenInSelectedProviderTextIsRejectedAndGenericProviderExceptionsAreSanitized() {
    doAnswer(
            call ->
                ((URI) call.getArgument(0)).getPath().endsWith("/media")
                    ? fixture("instagram-media")
                    : bytes("{\"id\":\"17841400000000001\",\"username\":\"" + TOKEN + "\"}"))
        .when(transport)
        .request(any(), anyString(), any(), any());
    unavailable(() -> source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
    doThrow(new IllegalStateException("Authorization Bearer " + TOKEN))
        .when(transport)
        .request(any(), anyString(), any(), any());
    unavailable(() -> source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
  }

  @Test
  void rateLimitAndProvider429DoNotRetryOrBypassLimits() {
    properties.setMaxRequestsPerHour(2);
    var source = source();
    source.fetch(BRAND, SocialEvidenceProvider.INSTAGRAM);
    assertThatThrownBy(() -> source.fetch(BRAND, SocialEvidenceProvider.INSTAGRAM))
        .isInstanceOf(EvidenceQuotaExceededException.class);
    verify(transport, times(2)).request(any(), anyString(), any(), any());
    reset(transport);
    doThrow(new EvidenceQuotaExceededException())
        .when(transport)
        .request(any(), anyString(), any(), any());
    assertThatThrownBy(() -> source().fetch(BRAND, SocialEvidenceProvider.TIKTOK))
        .isInstanceOf(EvidenceQuotaExceededException.class);
    verify(transport, times(1)).request(any(), anyString(), any(), any());
  }

  @Test
  void invalidConfiguredAccountOrTokenCannotInjectPathsHeadersOrSecretsIntoErrors() {
    properties.getBindings().get(0).setAccountId("1/../../evil?access_token=secret");
    assertThatThrownBy(this::source)
        .hasMessage("Invalid approved social evidence configuration")
        .hasNoCause();
    properties.getBindings().get(0).setAccountId("17841400000000001");
    properties.getBindings().get(0).setToken("token\r\nInjected: header");
    assertThatThrownBy(this::source)
        .hasMessage("Invalid approved social evidence configuration")
        .hasNoCause();
    verifyNoInteractions(transport);
  }

  @Test
  void tokenTotalLengthIncludesPaddingAndExactBoundaryIsAccepted() {
    var binding = properties.getBindings().get(0);
    binding.setToken("a".repeat(4094) + "==");
    assertThatCode(properties::validate).doesNotThrowAnyException();
    for (String token : List.of("a".repeat(4095) + "==", "fixture-token" + "=".repeat(5000))) {
      binding.setToken(token);
      assertThatThrownBy(properties::validate)
          .hasMessage("Invalid approved social evidence configuration")
          .hasNoCause()
          .hasMessageNotContaining(token);
    }
    verifyNoInteractions(transport);
  }

  @Test
  void oversizedPaginationMalformedPostTimestampAndDuplicateIdsFailClosed() throws Exception {
    for (String posts :
        List.of(
            "{\"data\":[{\"id\":\"1\",\"timestamp\":\"invalid\"}]}",
            "{\"data\":[{\"id\":\"1\",\"timestamp\":\"2026-09-29T00:00:00Z\"},{\"id\":\"1\",\"timestamp\":\"2026-09-29T00:00:00Z\"}]}",
            "{\"data\":[" + String.join(",", Collections.nCopies(21, "{}")) + "]}")) {
      doAnswer(
              call ->
                  ((URI) call.getArgument(0)).getPath().endsWith("/media")
                      ? bytes(posts)
                      : fixture("instagram-profile"))
          .when(transport)
          .request(any(), anyString(), any(), any());
      unavailable(() -> source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
    }
  }

  @Test
  void overallConcurrencyCapCoversBothProvidersAndReleasesCapacityAfterCompletion()
      throws Exception {
    CountDownLatch started = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    var source = source();
    doAnswer(
            call -> {
              URI uri = call.getArgument(0);
              started.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("test timeout");
              return fixture(
                  uri.getHost().equals("graph.facebook.com")
                      ? (uri.getPath().endsWith("/media") ? "instagram-media" : "instagram-profile")
                      : (uri.getPath().contains("/video/") ? "tiktok-videos" : "tiktok-profile"));
            })
        .when(transport)
        .request(any(), eq(TOKEN), any(), any());
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var instagram = executor.submit(() -> source.fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
      var tiktok = executor.submit(() -> source.fetch(BRAND, SocialEvidenceProvider.TIKTOK));
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> source.fetch(BRAND, SocialEvidenceProvider.INSTAGRAM))
          .isInstanceOf(EvidenceQuotaExceededException.class);
      release.countDown();
      assertThat(instagram.get(5, TimeUnit.SECONDS)).isNotNull();
      assertThat(tiktok.get(5, TimeUnit.SECONDS)).isNotNull();
      assertThat(source.fetch(BRAND, SocialEvidenceProvider.INSTAGRAM)).isNotNull();
    } finally {
      release.countDown();
    }
  }

  @Test
  void expiredOperationCannotPublishSnapshotAndPermitsRecoverAfterFailure() {
    properties.setDeadline(Duration.ofSeconds(1));
    doAnswer(
            call -> {
              TimeUnit.MILLISECONDS.sleep(1100);
              return fixture("instagram-profile");
            })
        .when(transport)
        .request(any(), anyString(), any(), any());
    unavailable(() -> source().fetch(BRAND, SocialEvidenceProvider.INSTAGRAM));
  }

  private OfficialSocialEvidenceSource source() {
    return new OfficialSocialEvidenceSource(
        properties, transport, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static void unavailable(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(SocialEvidenceUnavailableException.class)
        .hasMessage("Approved social evidence source is unavailable")
        .hasNoCause();
  }

  static byte[] fixture(String name) throws Exception {
    try (var input =
        OfficialSocialEvidenceSourceTest.class.getResourceAsStream("/social/" + name + ".json")) {
      return input.readAllBytes();
    }
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static SocialEvidenceProperties.Binding binding(SocialEvidenceProvider provider) {
    var binding = new SocialEvidenceProperties.Binding();
    binding.setBrandId(BRAND);
    binding.setProvider(provider);
    binding.setAccountId(
        provider == SocialEvidenceProvider.INSTAGRAM ? "17841400000000001" : "fixture-open-id");
    binding.setToken(TOKEN);
    binding.setApiVersion("v25.0");
    binding.setExpiresAt(NOW.plusSeconds(3600));
    binding.setApproved(true);
    binding.setScopes(SocialEvidenceProperties.requiredScopes(provider));
    return binding;
  }
}
