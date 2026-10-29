package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvDocumentTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "=1+1",
        " +cmd",
        "\t@SUM(1)",
        "\r-1",
        "\ntext",
        "\u00a0=HYPERLINK(\"x\")",
        "\u200b\uFEFF+1",
        " \t\u001b@cmd",
        "\u2003-1"
      })
  void neutralizesLeadingWhitespaceControlsAndUnicodeFormats(String input) {
    assertThat(CsvDocument.safeCell(input)).isEqualTo("'" + input);
  }

  @Test
  void quotesUnicodeEmbeddedNewlinesAndQuotesDeterministically() {
    var row = new ArrayList<>(Collections.nCopies(CsvDocument.COLUMNS.size(), ""));
    row.set(11, "Привет, \"🌍\"\r\nnext");
    row.set(12, "\t=1");
    var bytes = CsvDocument.render(List.of(row));
    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .startsWith("\uFEFF\"contract\",\"source_policy\"")
        .contains("\"Привет, \"\"🌍\"\"\r\nnext\"", "\"'\t=1\"")
        .endsWith("\r\n");
    assertThat(CsvDocument.render(List.of(row))).isEqualTo(bytes);
    assertThat(CsvDocument.safeCell("ordinary - text")).isEqualTo("ordinary - text");
  }

  @Test
  void rejectsCapsAndDigestBindsEverySelectionAndView() {
    assertThatThrownBy(() -> CsvDocument.render(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    var row = new ArrayList<>(Collections.nCopies(CsvDocument.COLUMNS.size(), ""));
    row.set(0, "界".repeat(800000));
    assertThatThrownBy(() -> CsvDocument.render(List.of(row)))
        .isInstanceOf(IllegalStateException.class);
    var s = new CsvExportRequest.Selection(UUID.randomUUID(), UUID.randomUUID(), 1, 1);
    var from = Instant.parse("2026-10-01T00:00:00Z");
    var a = new CsvExportRequest(from, from.plusSeconds(3600), "UTC", List.of(s));
    assertThat(new CsvExportRequest(a.from(), a.until(), "Europe/London", a.selections()).digest())
        .isNotEqualTo(a.digest());
    assertThatThrownBy(() -> new CsvExportRequest(a.from(), a.until(), "UTC", List.of(s, s)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CsvExportRequest(a.from(), a.from().plusSeconds(29 * 86400), "UTC", List.of(s)))
        .isInstanceOf(IllegalArgumentException.class);
    var t = new CsvExportRequest.Selection(UUID.randomUUID(), UUID.randomUUID(), 1, 1);
    assertThat(new CsvExportRequest(a.from(), a.until(), "UTC", List.of(s, t)).digest())
        .isEqualTo(new CsvExportRequest(a.from(), a.until(), "UTC", List.of(t, s)).digest());
  }
}
