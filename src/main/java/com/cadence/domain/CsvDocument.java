package com.cadence.domain;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned deterministic manual-use CSV. Contains no URLs or executable instructions. */
public final class CsvDocument {
  public static final int MAX_BYTES = 2 * 1024 * 1024;
  public static final List<String> COLUMNS =
      List.of(
          "contract",
          "source_policy",
          "export_id",
          "exported_by",
          "captured_at",
          "post_id",
          "revision_id",
          "revision_number",
          "revision_version",
          "content_sha256",
          "platform",
          "title",
          "caption",
          "hashtags",
          "format",
          "media_prompt",
          "video_script",
          "strategy_id",
          "strategy_revision",
          "strategy_version",
          "strategy_sha256",
          "schedule_id",
          "schedule_version",
          "scheduled_utc",
          "schedule_timezone",
          "schedule_local",
          "schedule_offset",
          "schedule_rules_version",
          "view_timezone",
          "view_local",
          "view_offset",
          "authored_by",
          "authored_at",
          "edited_by",
          "edited_at",
          "approved_by",
          "approved_at",
          "media_state",
          "media_references",
          "generation_batch_id",
          "generation_slot_id");

  private CsvDocument() {}

  public static String checksum(List<List<String>> rows) {
    var b = new StringBuilder("csv-export-snapshot-v1:");
    for (var row : rows) {
      if (row.size() != COLUMNS.size()) throw new IllegalArgumentException("Invalid CSV row");
      row.forEach(s -> part(b, s));
    }
    return BrandAnalysisAttempt.checksum(b.toString());
  }

  public static byte[] render(List<List<String>> rows) {
    if (rows == null || rows.isEmpty() || rows.size() > 84)
      throw new IllegalArgumentException("Invalid CSV batch");
    var b = new StringBuilder("\uFEFF");
    append(b, COLUMNS);
    for (var row : rows) {
      if (row.size() != COLUMNS.size()) throw new IllegalArgumentException("Invalid CSV row");
      append(b, row);
      if (b.length() > MAX_BYTES) throw new IllegalStateException("CSV output capacity exceeded");
    }
    byte[] bytes = b.toString().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_BYTES) throw new IllegalStateException("CSV output capacity exceeded");
    return bytes;
  }

  private static void append(StringBuilder b, List<String> row) {
    for (int i = 0; i < row.size(); i++) {
      if (i > 0) b.append(',');
      b.append('"').append(safeCell(row.get(i)).replace("\"", "\"\"")).append('"');
    }
    b.append("\r\n");
  }

  public static String safeCell(String value) {
    if (value == null) return "";
    boolean prefix =
        !value.isEmpty()
            && (value.charAt(0) == '\t' || value.charAt(0) == '\r' || value.charAt(0) == '\n');
    for (int i = 0; i < value.length(); ) {
      int cp = value.codePointAt(i);
      i += Character.charCount(cp);
      if (Character.isWhitespace(cp)
          || Character.isSpaceChar(cp)
          || Character.isISOControl(cp)
          || Character.getType(cp) == Character.FORMAT) continue;
      prefix |= cp == '=' || cp == '+' || cp == '-' || cp == '@';
      break;
    }
    return prefix ? "'" + value : value;
  }

  public static void part(StringBuilder b, String value) {
    b.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
  }
}
