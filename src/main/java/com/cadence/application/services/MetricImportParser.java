package com.cadence.application.services;

import com.cadence.domain.MetricRow;
import com.cadence.domain.PlaybookContent;
import com.fasterxml.jackson.databind.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Bounded data parser; never evaluates formulas, opens URLs or performs provider I/O. */
@Component
public class MetricImportParser {
  public static final String HEADER =
      "postId,platform,windowFrom,windowUntil,observedAt,reach,engagement,saves,signups,profileToSiteClicks,correctionOf";

  public record Manual(List<MetricRow> observations) {}

  private final ObjectMapper mapper;
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

  public MetricImportParser(ObjectMapper mapper) {
    this.mapper = mapper.copy();
    this.mapper
        .getFactory()
        .setStreamReadConstraints(
            com.fasterxml.jackson.core.StreamReadConstraints.builder()
                .maxNestingDepth(4)
                .maxStringLength(128)
                .maxNumberLength(16)
                .build());
  }

  public List<MetricRow> parse(String origin, byte[] raw) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Metric parsing outside transaction required");
    if (!slots.tryAcquire())
      throw new com.cadence.application.exceptions.MetricImportUnavailableException();
    try {
      return parseInput(origin, raw);
    } finally {
      slots.release();
    }
  }

  private List<MetricRow> parseInput(String origin, byte[] raw) {
    if (raw == null || raw.length < 1 || raw.length > 131072) throw invalid();
    try {
      String text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(raw))
              .toString();
      if (text.startsWith("\uFEFF")) throw invalid();
      List<MetricRow> rows;
      if ("MANUAL_JSON".equals(origin)) {
        // Strict integer and duplicate-key policy, independent of permissive framework defaults.
        var reader =
            mapper
                .readerFor(Manual.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        JsonNode tree = mapper.readTree(text);
        if (!tree.isObject()
            || !tree.path("observations").isArray()
            || tree.path("observations").size() > 200) throw invalid();
        for (var r : tree.path("observations")) {
          if (!r.isObject()) throw invalid();
          for (var name : List.of("postId", "platform", "windowFrom", "windowUntil", "observedAt"))
            if (!r.path(name).isTextual()) throw invalid();
          uuid(r.path("postId").asText());
          var correction = r.get("correctionOf");
          if (correction != null && !correction.isNull()) {
            if (!correction.isTextual()) throw invalid();
            uuid(correction.asText());
          }
          var fields = r.elements();
          while (fields.hasNext()) {
            var value = fields.next();
            if (value.isContainerNode() || value.asText().length() > 128) throw invalid();
          }
        }
        for (var r : tree.path("observations"))
          for (var name :
              List.of("reach", "engagement", "saves", "signups", "profileToSiteClicks")) {
            var v = r.get(name);
            if (v != null && !v.isNull() && (!v.isIntegralNumber() || !v.canConvertToLong()))
              throw invalid();
          }
        rows = reader.<Manual>readValue(text).observations();
      } else if ("CSV".equals(origin)) {
        var cells = csv(text);
        if (cells.size() < 2 || !cells.getFirst().equals(List.of(HEADER.split(",", -1))))
          throw invalid();
        rows = new ArrayList<>();
        for (int i = 1; i < cells.size(); i++) {
          var c = cells.get(i);
          if (c.size() != 11) throw invalid();
          rows.add(
              new MetricRow(
                  uuid(c.get(0)),
                  PlaybookContent.Channel.valueOf(c.get(1)),
                  Instant.parse(c.get(2)),
                  Instant.parse(c.get(3)),
                  Instant.parse(c.get(4)),
                  number(c.get(5)),
                  number(c.get(6)),
                  number(c.get(7)),
                  number(c.get(8)),
                  number(c.get(9)),
                  c.get(10).isEmpty() ? null : uuid(c.get(10))));
        }
      } else throw invalid();
      if (rows == null || rows.isEmpty() || rows.size() > 200 || rows.contains(null))
        throw invalid();
      var keys = new HashSet<String>();
      for (var row : rows) if (!keys.add(row.naturalKey())) throw invalid();
      return List.copyOf(rows);
    } catch (Exception e) {
      throw invalid();
    }
  }

  private static UUID uuid(String s) {
    var id = UUID.fromString(s);
    if (!id.toString().equals(s)) throw invalid();
    return id;
  }

  private static Long number(String s) {
    if (s.isEmpty()) return null;
    if (!s.matches("0|[1-9][0-9]{0,12}")) throw invalid();
    return Long.valueOf(s);
  }

  static List<List<String>> csv(String text) {
    var result = new ArrayList<List<String>>();
    var row = new ArrayList<String>();
    var cell = new StringBuilder();
    boolean quoted = false, closed = false, start = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\u0000' || (c < 32 && c != '\r' && c != '\n')) throw invalid();
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
            closed = true;
          }
        } else cell.append(c);
      } else if (c == '"') {
        if (!start || closed) throw invalid();
        quoted = true;
        start = false;
      } else if (c == ',' || c == '\r' || c == '\n') {
        row.add(cell.toString());
        cell.setLength(0);
        closed = false;
        start = true;
        if (row.size() > 11) throw invalid();
        if (c != ',') {
          if (c == '\r') {
            if (i + 1 >= text.length() || text.charAt(i + 1) != '\n') throw invalid();
            i++;
          }
          result.add(List.copyOf(row));
          row.clear();
          if (result.size() > 201) throw invalid();
        }
      } else {
        if (closed) throw invalid();
        cell.append(c);
        start = false;
      }
      if (cell.length() > 128) throw invalid();
    }
    if (quoted) throw invalid();
    if (!row.isEmpty() || !cell.isEmpty() || closed) {
      row.add(cell.toString());
      result.add(List.copyOf(row));
    }
    if (result.size() > 201) throw invalid();
    return result;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid or oversized metrics input");
  }
}
