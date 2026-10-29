package com.cadence.application.ports;

import com.cadence.domain.EvidenceAdmission;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Immutable admitted data, never instructions or a private storage URL. */
public record AnalysisTextSource(
    UUID admissionId,
    UUID evidenceId,
    long sourceVersion,
    String contentChecksum,
    String text,
    @com.fasterxml.jackson.annotation.JsonIgnore java.util.Map<String, String> fields) {
  public AnalysisTextSource(
      UUID admissionId, UUID evidenceId, long sourceVersion, String contentChecksum, String text) {
    this(admissionId, evidenceId, sourceVersion, contentChecksum, text, java.util.Map.of());
  }

  public static AnalysisTextSource from(com.cadence.domain.EvidenceAdmission a) {
    if (com.cadence.domain.SocialTextProjection.POLICY.equals(a.getPolicyVersion()))
      return new AnalysisTextSource(
          a.getId(),
          a.getEvidenceId(),
          a.getSourceVersion(),
          a.getContentChecksum(),
          a.getReviewedText(),
          com.cadence.domain.SocialTextProjection.fields(a.getReviewedText()));
    if (!com.cadence.domain.UrlReviewContent.POLICY_VERSION.equals(a.getPolicyVersion()))
      return new AnalysisTextSource(
          a.getId(),
          a.getEvidenceId(),
          a.getSourceVersion(),
          a.getContentChecksum(),
          a.getReviewedText());
    try {
      var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(a.getReviewedText());
      var fields = new java.util.LinkedHashMap<String, String>();
      for (var key : java.util.List.of("title", "description", "text")) {
        if (!node.path(key).isTextual())
          throw new IllegalArgumentException("Invalid URL projection");
        fields.put(key, node.get(key).textValue());
      }
      return new AnalysisTextSource(
          a.getId(),
          a.getEvidenceId(),
          a.getSourceVersion(),
          a.getContentChecksum(),
          a.getReviewedText(),
          fields);
    } catch (java.io.IOException invalid) {
      throw new IllegalArgumentException("Invalid URL projection");
    }
  }

  @com.fasterxml.jackson.annotation.JsonProperty("representation")
  public String representation() {
    return social() ? "social-text-v1" : structured() ? "url-text-v1" : "plain-text-v1";
  }

  public boolean social() {
    return fields.containsKey("accountName");
  }

  public boolean structured() {
    return !fields.isEmpty();
  }

  public String citationText(String path) {
    if (fields.isEmpty()) {
      if (path != null) throw new IllegalArgumentException("Invalid citation path");
      return text;
    }
    if (path == null || !fields.containsKey(path))
      throw new IllegalArgumentException("Invalid citation path");
    return fields.get(path);
  }

  public AnalysisTextSource {
    fields = java.util.Map.copyOf(fields);
    if (fields.containsKey("accountName")) {
      if (!fields.equals(com.cadence.domain.SocialTextProjection.fields(text)))
        throw new IllegalArgumentException("Invalid social projection fields");
    } else if (!fields.isEmpty()) {
      if (!fields.keySet().equals(java.util.Set.of("title", "description", "text")))
        throw new IllegalArgumentException("Invalid URL projection fields");
      var metadata =
          new com.cadence.domain.WebsiteMetadata(
              fields.get("title"), fields.get("description"), fields.get("text"), "", "website-v2");
      if (!com.cadence.domain.UrlTextSnapshot.project(metadata).equals(text))
        throw new IllegalArgumentException("Invalid URL projection");
    }
    if (admissionId == null
        || evidenceId == null
        || sourceVersion < 0
        || text == null
        || text.isBlank()
        || text.length() > 20000
        || contentChecksum == null
        || !EvidenceAdmission.checksum(text).equals(contentChecksum))
      throw new IllegalArgumentException("Invalid analysis text source");
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
          throw new IllegalArgumentException("Invalid analysis text source");
      } else if (Character.isLowSurrogate(c)
          || (Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t'))
        throw new IllegalArgumentException("Invalid analysis text source");
    }
  }

  public int utf8Bytes() {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }
}
