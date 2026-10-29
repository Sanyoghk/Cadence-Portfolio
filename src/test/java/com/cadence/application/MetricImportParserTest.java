package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.services.MetricImportParser;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class MetricImportParserTest {
  @Test
  void parserCapacityFailsClosedAndIsReusable() {
    var slots =
        (java.util.concurrent.Semaphore)
            org.springframework.test.util.ReflectionTestUtils.getField(parser, "slots");
    assertThat(slots.tryAcquire(2)).isTrue();
    try {
      assertThatThrownBy(() -> parser.parse("CSV", bytes(MetricImportParser.HEADER + "\n" + row())))
          .isInstanceOf(com.cadence.application.exceptions.MetricImportUnavailableException.class);
    } finally {
      slots.release(2);
    }
    assertThat(parser.parse("CSV", bytes(MetricImportParser.HEADER + "\n" + row()))).hasSize(1);
  }

  final MetricImportParser parser =
      new MetricImportParser(JsonMapper.builder().addModule(new JavaTimeModule()).build());
  final String id = "00000000-0000-0000-0000-000000000001";

  String row() {
    return id + ",INSTAGRAM,2026-01-01T00:00:00Z,2026-01-02T00:00:00Z,2026-01-02T00:00:00Z,1,,,,,";
  }

  byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void strictCsvQuoteLfCrLfAndMissing() {
    for (var sep : List.of("\n", "\r\n")) {
      var result =
          parser.parse(
              "CSV",
              bytes(MetricImportParser.HEADER + sep + row().replace(id, "\"" + id + "\"") + sep));
      assertThat(result.getFirst().reach()).isEqualTo(1);
      assertThat(result.getFirst().engagement()).isNull();
    }
  }

  @Test
  void maliciousAndMalformedCsv() {
    var header = MetricImportParser.HEADER + "\n";
    for (var r :
        List.of(
            row().replace(",1,", ",-1,"),
            row().replace(",1,", ",1.0,"),
            row().replace(",1,", ",+1,"),
            row().replace(",1,", ", 1,"),
            row().replace(",1,", ",1000000000001,"),
            row().replace(",1,", ",9223372036854775808,"),
            row().replace(",1,", ",=SUM(1),"),
            row().replace(",1,", ",https://localhost/,"),
            row().replace(",1,", ",1e3,"),
            row().replace("2026-01-02T00:00:00Z", "bad"),
            row().replace("INSTAGRAM", "YOUTUBE"),
            "\"" + row(),
            row() + ",",
            row().replace(id, "\"" + id + "\"x"),
            row().replace(id, "x".repeat(129)),
            row().replace(id, "\t" + id),
            row() + "\n\n"))
      assertThatThrownBy(() -> parser.parse("CSV", bytes(header + r)))
          .as(r)
          .isInstanceOf(IllegalArgumentException.class);
    for (var h :
        List.of(
            "",
            MetricImportParser.HEADER.replace("reach", "Reach"),
            MetricImportParser.HEADER + ",role",
            MetricImportParser.HEADER.replace("reach", "saves")))
      assertThatThrownBy(() -> parser.parse("CSV", bytes(h + "\n" + row())))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidEncodingBomAndBounds() {
    for (var raw :
        List.of(
            new byte[] {(byte) 0xc3, 0x28},
            new byte[131073],
            bytes("\uFEFF" + MetricImportParser.HEADER + "\n" + row()),
            bytes(MetricImportParser.HEADER + "\r" + row()),
            bytes(MetricImportParser.HEADER + "\n" + row() + "\n" + row())))
      assertThatThrownBy(() -> parser.parse("CSV", raw))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rowCapIsExactlyTwoHundred() {
    var rows = new ArrayList<String>();
    for (int i = 0; i < 201; i++) rows.add(row().replace(id, new UUID(0, i + 1).toString()));
    assertThat(
            parser.parse(
                "CSV",
                bytes(MetricImportParser.HEADER + "\n" + String.join("\n", rows.subList(0, 200)))))
        .hasSize(200);
    assertThatThrownBy(
            () ->
                parser.parse(
                    "CSV", bytes(MetricImportParser.HEADER + "\n" + String.join("\n", rows))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void strictJsonNoCoercionAuditDuplicatesOrTrailing() {
    String row =
        "{\"postId\":\""
            + id
            + "\",\"platform\":\"INSTAGRAM\",\"windowFrom\":\"2026-01-01T00:00:00Z\",\"windowUntil\":\"2026-01-02T00:00:00Z\",\"observedAt\":\"2026-01-02T00:00:00Z\",\"reach\":1}";
    String valid = "{\"observations\":[" + row + "]}";
    assertThat(parser.parse("MANUAL_JSON", bytes(valid))).hasSize(1);
    for (var r :
        List.of(
            valid + "{}",
            valid.replace("\"reach\":1", "\"reach\":1.2"),
            valid.replace("\"reach\":1", "\"reach\":\"1\""),
            valid.replace("\"reach\":1", "\"reach\":1,\"reach\":2"),
            valid.replace("\"reach\":1", "\"reach\":1,\"tenantId\":\"forged\""),
            "{\"observations\":[null]}",
            "{\"observations\":[]}"))
      assertThatThrownBy(() -> parser.parse("MANUAL_JSON", bytes(r)))
          .isInstanceOf(IllegalArgumentException.class);
  }
}
