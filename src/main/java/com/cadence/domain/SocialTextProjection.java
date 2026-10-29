package com.cadence.domain;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Deterministic data-only projection. Provider identifiers never enter model input. */
public final class SocialTextProjection {
  public static final String FORMAT = "social-text-v1";
  public static final String POLICY = "social-text-review-v1";
  private static final ObjectMapper JSON =
      new ObjectMapper(
              com.fasterxml.jackson.core.JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      com.fasterxml.jackson.core.StreamReadConstraints.builder()
                          .maxNestingDepth(8)
                          .build())
                  .build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private SocialTextProjection() {}

  public record Projection(String payload, String postIds) {}

  public static Projection project(BrandEvidence e, EvidenceSocialOrigin origin) {
    String raw = EvidenceAdmission.reviewableText(e);
    if (origin == null
        || !e.getId().equals(origin.getEvidenceId())
        || !e.getChecksum().equals(origin.getChecksum())) throw invalid();
    var root = parse(raw);
    shape(
        root,
        Set.of(
            "format", "provider", "accountId", "accountName", "biography", "apiVersion", "posts"));
    if (!value(root, "format", 64).equals("approved-social-v1")
        || !value(root, "provider", 16).equals(origin.getProvider().name())
        || !value(root, "accountId", 128).equals(origin.getAccountId())
        || !value(root, "apiVersion", 16).equals(origin.getApiVersion())) throw invalid();
    boolean tikTok = origin.getProvider() == SocialEvidenceProvider.TIKTOK;
    var result = JSON.createObjectNode();
    result.put("format", FORMAT);
    result.put("accountName", value(root, "accountName", 128));
    result.put("biography", value(root, "biography", 300));
    if (!tikTok && !origin.getAccountId().matches("[0-9]{1,32}")) throw invalid();
    if (tikTok
        && (!root.path("biography").textValue().isEmpty() || !origin.getApiVersion().equals("v2")))
      throw invalid();
    if (!tikTok && !origin.getApiVersion().matches("v[1-9][0-9]*\\.0")) throw invalid();
    var posts = root.get("posts");
    if (!posts.isArray() || posts.size() > 20) throw invalid();
    var target = result.putArray("posts");
    var ids = JSON.createArrayNode();
    String previous = null;
    for (var post : posts) {
      shape(
          post,
          tikTok
              ? Set.of("id", "publishedAt", "text", "title")
              : Set.of("id", "publishedAt", "text"));
      String id = value(post, "id", 32);
      if (!id.matches("[0-9]{1,32}") || (previous != null && previous.compareTo(id) >= 0))
        throw invalid();
      previous = id;
      ids.add(id);
      String published = value(post, "publishedAt", 40);
      Instant time;
      try {
        time = Instant.parse(published);
      } catch (RuntimeException ex) {
        throw invalid();
      }
      if (time.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
          || time.isAfter(origin.getCapturedAt().plusSeconds(86400))
          || !time.toString().equals(published)) throw invalid();
      var p = target.addObject();
      p.put("publishedAt", published);
      p.put("text", value(post, "text", 300));
      if (tikTok) p.put("title", value(post, "title", 100));
    }
    String payload = result.toString();
    fields(payload); // canonical shape, meaningful content and UTF-8 budget
    return new Projection(payload, ids.toString());
  }

  /** Decoded field paths. Timestamps remain context, not standalone substantive citations. */
  public static Map<String, String> fields(String payload) {
    if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > 20000) throw invalid();
    var root = parse(payload);
    shape(root, Set.of("format", "accountName", "biography", "posts"));
    if (!FORMAT.equals(value(root, "format", 32))) throw invalid();
    var canonical = JSON.createObjectNode();
    canonical.put("format", FORMAT);
    var fields = new LinkedHashMap<String, String>();
    for (var key : List.of("accountName", "biography")) {
      String v = value(root, key, key.equals("accountName") ? 128 : 300);
      canonical.put(key, v);
      fields.put(key, v);
    }
    var posts = root.get("posts");
    if (!posts.isArray() || posts.size() > 20) throw invalid();
    var target = canonical.putArray("posts");
    Boolean titles = null;
    for (int i = 0; i < posts.size(); i++) {
      var post = posts.get(i);
      boolean title = post.has("title");
      if (titles != null && titles != title) throw invalid();
      titles = title;
      shape(post, title ? Set.of("publishedAt", "text", "title") : Set.of("publishedAt", "text"));
      var p = target.addObject();
      String at = value(post, "publishedAt", 40);
      try {
        if (!Instant.parse(at).toString().equals(at)) throw invalid();
      } catch (RuntimeException ex) {
        throw invalid();
      }
      p.put("publishedAt", at);
      p.put("text", value(post, "text", 300));
      fields.put("posts[" + i + "].text", p.get("text").textValue());
      if (title) {
        p.put("title", value(post, "title", 100));
        fields.put("posts[" + i + "].title", p.get("title").textValue());
      }
    }
    if (!canonical.toString().equals(payload) || fields.values().stream().allMatch(String::isBlank))
      throw invalid();
    return Collections.unmodifiableMap(fields);
  }

  public static boolean citationPath(String path) {
    return path != null
        && (Set.of("title", "description", "text", "accountName", "biography").contains(path)
            || path.matches("posts\\[([0-9]|1[0-9])\\]\\.(text|title)"));
  }

  private static JsonNode parse(String text) {
    try {
      if (text == null || text.length() > 20000) throw invalid();
      return JSON.readTree(text);
    } catch (java.io.IOException ex) {
      throw invalid();
    }
  }

  private static void shape(JsonNode n, Set<String> expected) {
    if (n == null || !n.isObject()) throw invalid();
    var keys = new HashSet<String>();
    n.fieldNames().forEachRemaining(keys::add);
    if (!keys.equals(expected)) throw invalid();
  }

  private static String value(JsonNode n, String key, int max) {
    var v = n.get(key);
    if (v == null || !v.isTextual()) throw invalid();
    String s = v.textValue();
    if ((key.equals("accountName") && s.length() > 128)
        || s.codePointCount(0, s.length()) > max
        || !StandardCharsets.UTF_8.newEncoder().canEncode(s)
        || s.codePoints()
            .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
      throw invalid();
    return s;
  }

  private static IllegalStateException invalid() {
    return new IllegalStateException("Social text snapshot is unavailable or invalid");
  }
}
