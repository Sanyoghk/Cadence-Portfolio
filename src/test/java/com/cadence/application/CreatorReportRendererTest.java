package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.results.CreatorReportSnapshot;
import com.cadence.application.services.CreatorReportRenderer;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CreatorReportRendererTest {
  private CreatorReportSnapshot snapshot(String text) {
    var strategy = UUID.randomUUID();
    var request =
        new CreatorReportRequest(
            strategy,
            1,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 2),
            "UTC",
            PlaybookContent.Channel.INSTAGRAM,
            PerformanceQuery.Bucket.PERIOD,
            List.of());
    return new CreatorReportSnapshot(
        "creator-report-v1",
        UUID.randomUUID(),
        UUID.randomUUID(),
        new CreatorReportSnapshot.Source(strategy, 1, 1, "a".repeat(64)),
        request,
        Instant.parse("2026-10-03T00:00:00Z"),
        "2026a",
        "performance-v1",
        0,
        0,
        0,
        0,
        0,
        List.of(text),
        List.of(),
        List.of());
  }

  @Test
  void staticDocumentEscapesMarkupAndRetainsUnicodeWithoutResources() {
    var renderer = new CreatorReportRenderer();
    var s = snapshot("<script>alert('x')</script> & \"данные\"\nNo causal claim");
    var id = UUID.randomUUID();
    var a = renderer.render(id, s);
    var b = renderer.render(id, s);
    assertThat(a).isEqualTo(b);
    assertThat(new String(a, java.nio.charset.StandardCharsets.UTF_8))
        .contains(
            "&lt;script&gt;",
            "&amp;",
            "&quot;данные&quot;",
            "No causal claim",
            "No advice generated")
        .doesNotContain("<script>", "<iframe", "src=", "href=", "<style");
  }

  @Test
  void outputCannotExceedBound() {
    assertThatThrownBy(
            () ->
                new CreatorReportRenderer()
                    .render(UUID.randomUUID(), snapshot("x".repeat(CreatorReport.MAX_BYTES))))
        .isInstanceOf(CreatorReportOutputException.class);
  }

  @Test
  void requestBoundsAndDuplicateSelectionsAreRejected() {
    var id = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                new CreatorReportRequest(
                    id,
                    1,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 2),
                    "../../UTC",
                    PlaybookContent.Channel.INSTAGRAM,
                    PerformanceQuery.Bucket.PERIOD,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CreatorReportRequest(
                    id,
                    1,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 2),
                    "UTC",
                    PlaybookContent.Channel.INSTAGRAM,
                    PerformanceQuery.Bucket.PERIOD,
                    List.of(id, id)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
