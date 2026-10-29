package com.cadence.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MvcResult;

/** Checks the schema features used by this reviewed contract, not a general JSON Schema engine. */
public final class OpenApiContractAssertions {
  public static final JsonNode DOCUMENT;

  static {
    try (var input = new ClassPathResource("openapi/cadence.json").getInputStream()) {
      DOCUMENT = new ObjectMapper().readTree(input);
    } catch (Exception error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  private OpenApiContractAssertions() {}

  public static void verify(MvcResult result) throws Exception {
    var response = result.getResponse();
    if (response.getStatus() >= 400) {
      assertThat(response.getContentType()).startsWith("application/problem+json");
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
      var body = new ObjectMapper().readTree(response.getContentAsString());
      validate(body, DOCUMENT.at("/components/schemas/Problem"));
      assertThat(body.path("status").asInt()).isEqualTo(response.getStatus());
      assertThat(body.path("instance").asText()).startsWith("urn:uuid:");
      UUID.fromString(body.path("instance").asText().substring(9));
      return;
    }
    var paths = DOCUMENT.path("paths").fields();
    while (paths.hasNext()) {
      var path = paths.next();
      String pattern = "^" + path.getKey().replaceAll("\\{[^}]+}", "[^/]+") + "$";
      if (!result.getRequest().getRequestURI().matches(pattern)) continue;
      var operation =
          path.getValue().path(result.getRequest().getMethod().toLowerCase(Locale.ROOT));
      assertThat(operation.isMissingNode()).as("documented operation").isFalse();
      var declared = operation.path("responses").path(Integer.toString(response.getStatus()));
      if (declared.has("$ref")) declared = DOCUMENT.at(declared.path("$ref").asText().substring(1));
      assertThat(declared.isMissingNode()).as("documented success status").isFalse();
      var media = declared.path("content").fields().next();
      assertThat(response.getContentType()).startsWith(media.getKey());
      JsonNode body =
          media.getKey().equals("text/plain")
              ? new ObjectMapper().valueToTree(response.getContentAsString())
              : new ObjectMapper().readTree(response.getContentAsString());
      validate(body, media.getValue().path("schema"));
      assertPrivateFileReference(body);
      return;
    }
    throw new AssertionError("Undocumented endpoint: " + result.getRequest().getRequestURI());
  }

  private static void assertPrivateFileReference(JsonNode node) {
    if (node.isObject()
        && node.path("type").asText().equals("FILE")
        && node.has("sourceReference")) {
      assertThat(node.path("sourceReference").isNull()).as("private FILE reference").isTrue();
    }
    node.elements().forEachRemaining(OpenApiContractAssertions::assertPrivateFileReference);
  }

  static void validate(JsonNode value, JsonNode schema) {
    if (schema.has("$ref")) {
      validate(value, DOCUMENT.at(schema.path("$ref").asText().substring(1)));
      return;
    }
    if (schema.has("oneOf")) {
      int matches = 0;
      for (var alternative : schema.path("oneOf")) {
        try {
          validate(value, alternative);
          matches++;
        } catch (AssertionError ignored) {
          /* Validate all alternatives, require exactly one. */
        }
      }
      assertThat(matches).as("exactly one declared schema").isEqualTo(1);
      return;
    }
    if (schema.has("anyOf")) {
      for (var alternative : schema.path("anyOf")) {
        try {
          validate(value, alternative);
          return;
        } catch (AssertionError ignored) {
          /* Try next declared alternative. */
        }
      }
      throw new AssertionError("No declared alternative accepts " + value.getNodeType());
    }
    switch (schema.path("type").asText()) {
      case "null" -> assertThat(value.isNull()).isTrue();
      case "object" -> {
        assertThat(value.isObject()).isTrue();
        for (var field : schema.path("required"))
          assertThat(value.has(field.asText())).as(field.asText()).isTrue();
        var names = new HashSet<String>();
        schema.path("properties").fieldNames().forEachRemaining(names::add);
        if (schema.has("additionalProperties")
            && !schema.path("additionalProperties").asBoolean()) {
          var actual = new HashSet<String>();
          value.fieldNames().forEachRemaining(actual::add);
          assertThat(names).containsAll(actual);
        }
        value
            .fields()
            .forEachRemaining(
                field -> {
                  if (schema.path("properties").has(field.getKey()))
                    validate(field.getValue(), schema.path("properties").path(field.getKey()));
                });
      }
      case "array" -> {
        assertThat(value.isArray()).isTrue();
        if (schema.has("maxItems"))
          assertThat(value.size()).isLessThanOrEqualTo(schema.path("maxItems").asInt());
        value.forEach(item -> validate(item, schema.path("items")));
      }
      case "string" -> {
        assertThat(value.isTextual()).isTrue();
        if (schema.path("format").asText().equals("uuid")) UUID.fromString(value.asText());
        if (schema.path("format").asText().equals("date-time")) {
          Instant.parse(value.asText());
          assertThat(value.asText()).endsWith("Z");
        }
        if (schema.has("pattern"))
          assertThat(
                  java.util.regex.Pattern.compile(schema.path("pattern").asText())
                      .matcher(value.asText())
                      .find())
              .isTrue();
        if (schema.has("maxLength"))
          assertThat(value.asText().length()).isLessThanOrEqualTo(schema.path("maxLength").asInt());
      }
      case "number" -> {
        assertThat(value.isNumber()).isTrue();
      }
      case "integer" -> {
        assertThat(value.isIntegralNumber()).isTrue();
        if (schema.has("minimum"))
          assertThat(value.asLong()).isGreaterThanOrEqualTo(schema.path("minimum").asLong());
        if (schema.has("maximum"))
          assertThat(value.asLong()).isLessThanOrEqualTo(schema.path("maximum").asLong());
      }
      case "boolean" -> assertThat(value.isBoolean()).isTrue();
      default -> throw new AssertionError("Unsupported schema type");
    }
    if (schema.has("const")) assertThat(value).isEqualTo(schema.path("const"));
    if (schema.has("enum")) {
      boolean found = false;
      for (var allowed : schema.path("enum")) found |= allowed.equals(value);
      assertThat(found).isTrue();
    }
  }
}
