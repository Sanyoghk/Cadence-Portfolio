package com.cadence.infrastructure.ingest.social;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.exceptions.SocialEvidenceUnavailableException;
import com.cadence.application.ports.ApprovedSocialEvidenceSource;
import com.cadence.domain.SocialEvidenceProvider;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;

public final class OfficialSocialEvidenceSource implements ApprovedSocialEvidenceSource {
  private static final ObjectMapper JSON =
      new ObjectMapper(
              JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder().maxNestingDepth(32).build())
                  .build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private final SocialEvidenceProperties properties;
  private final SocialProviderHttpTransport transport;
  private final Clock clock;
  private final Semaphore concurrency = new Semaphore(2);
  private final Map<SocialEvidenceProvider, RateWindow> rates =
      new EnumMap<>(SocialEvidenceProvider.class);

  public OfficialSocialEvidenceSource(
      SocialEvidenceProperties properties, SocialProviderHttpTransport transport, Clock clock) {
    properties.validate();
    this.properties = properties;
    this.transport = transport;
    this.clock = clock;
    for (SocialEvidenceProvider provider : SocialEvidenceProvider.values()) {
      rates.put(provider, new RateWindow());
    }
  }

  @Override
  public Instant requireApprovedAccount(
      UUID brandId, SocialEvidenceProvider provider, String accountId) {
    var binding = approvedBinding(brandId, provider);
    if (!binding.getAccountId().equals(accountId)) throw new SocialEvidenceUnavailableException();
    return binding.getExpiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  @Override
  public void requireApprovedBinding(UUID brandId, SocialEvidenceProvider provider) {
    approvedBinding(brandId, provider);
  }

  private SocialEvidenceProperties.Binding approvedBinding(
      UUID brandId, SocialEvidenceProvider provider) {
    if (brandId == null || provider == null || !properties.isEnabled()) throw unavailable();
    var binding =
        properties.getBindings().stream()
            .filter(item -> brandId.equals(item.getBrandId()) && provider == item.getProvider())
            .findFirst()
            .orElseThrow(OfficialSocialEvidenceSource::unavailable);
    Instant now = Instant.now(clock);
    if (!binding.isApproved()
        || !binding.getScopes().containsAll(SocialEvidenceProperties.requiredScopes(provider))
        || !binding.getExpiresAt().isAfter(now.plusSeconds(30))) throw unavailable();
    try {
      SocialHttpRuntimePolicy.require(true);
    } catch (IllegalArgumentException invalidRuntime) {
      throw unavailable();
    }
    return binding;
  }

  @Override
  public Snapshot fetch(UUID brandId, SocialEvidenceProvider provider) {
    var binding = approvedBinding(brandId, provider);
    Instant now = Instant.now(clock);
    Semaphore permits = concurrency;
    if (!permits.tryAcquire()) throw new EvidenceQuotaExceededException();
    long deadline = System.nanoTime() + properties.getDeadline().toNanos();
    try {
      JsonNode profile;
      JsonNode posts;
      String account;
      String name;
      String biography;
      if (provider == SocialEvidenceProvider.INSTAGRAM) {
        String base =
            "https://graph.facebook.com/" + binding.getApiVersion() + "/" + binding.getAccountId();
        profile =
            request(provider, binding, base + "?fields=id,username,biography", null, deadline);
        account = requiredText(profile, "id", 128);
        if (!binding.getAccountId().equals(account)) throw unavailable();
        name = requiredText(profile, "username", 128);
        biography = optionalText(profile, "biography", 300);
        posts =
            request(
                    provider,
                    binding,
                    base + "/media?fields=id,caption,timestamp&limit=20",
                    null,
                    deadline)
                .get("data");
      } else {
        profile =
            request(
                    provider,
                    binding,
                    "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name",
                    null,
                    deadline)
                .path("data")
                .path("user");
        account = requiredText(profile, "open_id", 128);
        if (!binding.getAccountId().equals(account)) throw unavailable();
        name = requiredText(profile, "display_name", 128);
        biography = "";
        posts =
            request(
                    provider,
                    binding,
                    "https://open.tiktokapis.com/v2/video/list/?fields=id,create_time,title,video_description",
                    "{\"max_count\":20}",
                    deadline)
                .path("data")
                .get("videos");
      }
      if (!binding.getAccountId().equals(account)
          || posts == null
          || !posts.isArray()
          || posts.size() > 20) throw unavailable();
      List<Map<String, Object>> normalized = new ArrayList<>();
      Set<String> postIds = new HashSet<>();
      for (JsonNode post : posts) {
        String id = requiredText(post, "id", 128);
        if (!id.matches("[0-9]{1,32}") || !postIds.add(id)) throw unavailable();
        Instant published;
        if (provider == SocialEvidenceProvider.INSTAGRAM)
          published = OffsetDateTime.parse(requiredText(post, "timestamp", 64)).toInstant();
        else {
          JsonNode time = post.get("create_time");
          if (time == null || !time.isIntegralNumber() || !time.canConvertToLong())
            throw unavailable();
          published = Instant.ofEpochSecond(time.longValue());
        }
        if (published.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
            || published.isAfter(now.plus(Duration.ofDays(1)))) throw unavailable();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("publishedAt", published.toString());
        row.put(
            "text",
            provider == SocialEvidenceProvider.INSTAGRAM
                ? optionalText(post, "caption", 300)
                : optionalText(post, "video_description", 300));
        if (provider == SocialEvidenceProvider.TIKTOK)
          row.put("title", optionalText(post, "title", 100));
        normalized.add(row);
      }
      normalized.sort(Comparator.comparing(row -> (String) row.get("id")));
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("format", "approved-social-v1");
      snapshot.put("provider", provider.name());
      snapshot.put("accountId", account);
      snapshot.put("accountName", name);
      snapshot.put("biography", biography);
      snapshot.put(
          "apiVersion",
          provider == SocialEvidenceProvider.INSTAGRAM ? binding.getApiVersion() : "v2");
      // Retrieval time belongs to provenance, not content identity: unchanged snapshots
      // deduplicate.
      snapshot.put("posts", normalized);
      String canonical = JSON.writeValueAsString(snapshot);
      if (canonical.contains(binding.getToken())
          || canonical.length() > 20_000
          || System.nanoTime() >= deadline) throw unavailable();
      return new Snapshot(
          provider,
          account,
          provider == SocialEvidenceProvider.INSTAGRAM ? binding.getApiVersion() : "v2",
          now,
          canonical);
    } catch (EvidenceQuotaExceededException limit) {
      throw limit;
    } catch (Exception malformedOrProviderFailure) {
      throw unavailable();
    } finally {
      permits.release();
    }
  }

  private JsonNode request(
      SocialEvidenceProvider provider,
      SocialEvidenceProperties.Binding binding,
      String uri,
      String post,
      long deadline)
      throws Exception {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) throw unavailable();
    rates.get(provider).reserve(Instant.now(clock), properties.getMaxRequestsPerHour());
    byte[] bytes =
        transport.request(URI.create(uri), binding.getToken(), post, Duration.ofNanos(remaining));
    if (bytes == null
        || bytes.length == 0
        || bytes.length > JdkSocialProviderHttpTransport.MAX_RESPONSE_BYTES) throw unavailable();
    String json =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    JsonNode result = JSON.readTree(json);
    if (result == null || !result.isObject()) throw unavailable();
    if (provider == SocialEvidenceProvider.TIKTOK) {
      if (!"ok".equals(result.path("error").path("code").asText())) throw unavailable();
    } else if (result.has("error")) throw unavailable();
    return result;
  }

  private static String requiredText(JsonNode object, String field, int max) {
    JsonNode value = object.get(field);
    if (value == null
        || !value.isTextual()
        || value.textValue().isBlank()
        || value.textValue().length() > max) throw unavailable();
    return value.textValue();
  }

  private static String optionalText(JsonNode object, String field, int maxCodepoints) {
    JsonNode value = object.get(field);
    if (value == null || value.isNull()) return "";
    if (!value.isTextual()) throw unavailable();
    String text = value.textValue();
    if (text.indexOf('\0') >= 0) throw unavailable();
    int end =
        text.offsetByCodePoints(0, Math.min(maxCodepoints, text.codePointCount(0, text.length())));
    return text.substring(0, end);
  }

  private static SocialEvidenceUnavailableException unavailable() {
    return new SocialEvidenceUnavailableException();
  }

  private static final class RateWindow {
    private long hour = Long.MIN_VALUE;
    private int calls;

    synchronized void reserve(Instant now, int limit) {
      long current = Math.floorDiv(now.getEpochSecond(), 3600);
      if (current > hour) {
        hour = current;
        calls = 0;
      }
      if (calls >= limit) throw new EvidenceQuotaExceededException();
      calls++;
    }
  }
}
