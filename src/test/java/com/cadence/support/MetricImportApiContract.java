package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.ImportMetricsUseCase;
import com.cadence.domain.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class MetricImportApiContract extends PostApiContract {
  @Test
  void maximumCountRoundTripsWithoutIntegerOverflow() {
    var p = create();
    var r =
        new MetricRow(
            p.id(),
            PlaybookContent.Channel.INSTAGRAM,
            FROM,
            UNTIL,
            AT,
            MetricRow.MAX_COUNT,
            MetricRow.MAX_COUNT,
            MetricRow.MAX_COUNT,
            MetricRow.MAX_COUNT,
            MetricRow.MAX_COUNT,
            null);
    assertThat(finish(submit(raw(List.of(r)))).status()).isEqualTo(JobStatus.SUCCEEDED);
    var actual = read(p.id(), false).items().getFirst();
    assertThat(actual.reach()).isEqualTo(MetricRow.MAX_COUNT);
    assertThat(actual.profileToSiteClicks()).isEqualTo(MetricRow.MAX_COUNT);
  }

  @Test
  void malformedCsvWireInputsAreRejected() throws Exception {
    var p = create();
    var good = csv(p.id(), "1");
    for (var raw :
        List.of(
            good.replace("reach", "Reach").getBytes(StandardCharsets.UTF_8),
            good.replace(",1,", ",-1,").getBytes(StandardCharsets.UTF_8),
            good.replace(",1,", ",=SUM(1),").getBytes(StandardCharsets.UTF_8),
            good.replace(AT.toString(), "invalid-time").getBytes(StandardCharsets.UTF_8),
            new byte[] {(byte) 0xc3, 0x28}))
      mvc.perform(
              post("/api/v1/brands/{id}/metric-imports", brand.getId())
                  .with(authentication(owner))
                  .contentType("text/csv")
                  .header("Idempotency-Key", "malformed-csv-key-001")
                  .content(raw))
          .andExpect(status().isBadRequest());
  }

  @Test
  void csvHttpAndCrossBrandPostMapping() throws Exception {
    var p = create();
    var bytes = csv(p.id(), "0").getBytes(StandardCharsets.UTF_8);
    mvc.perform(
            post("/api/v1/brands/{id}/metric-imports", brand.getId())
                .with(authentication(owner))
                .contentType("text/csv;charset=UTF-8")
                .header("Idempotency-Key", "csv-http-metrics-key-001")
                .content(bytes))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.origin").value("CSV"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    var other = brands.saveAndFlush(new Brand("Other brand", workspace));
    mvc.perform(
            post("/api/v1/brands/{id}/metric-imports", other.getId())
                .with(authentication(owner))
                .contentType("text/csv")
                .header("Idempotency-Key", "foreign-post-metrics-key-001")
                .content(bytes))
        .andExpect(status().isNotFound());
  }

  @Autowired protected ImportMetricsUseCase metrics;
  @Autowired protected MetricImportService worker;
  @Autowired protected MetricImportRepository imports;
  @Autowired protected MetricObservationRepository observations;
  protected static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z"),
      UNTIL = FROM.plusSeconds(86400),
      AT = UNTIL.plusSeconds(3600);

  protected MetricRow row(UUID post, Long reach, UUID correction) {
    return new MetricRow(
        post,
        PlaybookContent.Channel.INSTAGRAM,
        FROM,
        UNTIL,
        AT,
        reach,
        null,
        0L,
        null,
        null,
        correction);
  }

  protected byte[] raw(List<MetricRow> rows) {
    try {
      return mapper.writeValueAsBytes(new MetricImportParser.Manual(rows));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  protected MetricImportDetails submit(byte[] raw) {
    return metrics.submit(
        brand.getId(), scope(), "metrics-key-" + UUID.randomUUID(), "MANUAL_JSON", raw);
  }

  protected MetricImportDetails finish(MetricImportDetails e) {
    worker.execute(worker.claim(e.id()));
    return metrics.status(e.id(), scope(), false);
  }

  protected MetricImportService.ObservationPage read(UUID post, boolean history) {
    return metrics.read(post, scope(), FROM, AT.plusSeconds(1), history, 0, 100);
  }

  protected String csv(UUID post, String reach) {
    return MetricImportParser.HEADER
        + "\r\n"
        + post
        + ",INSTAGRAM,"
        + FROM
        + ","
        + UNTIL
        + ","
        + AT
        + ","
        + reach
        + ",,0,,,\r\n";
  }

  @Test
  void manualJobToNullableObservationAndScopedStatus() throws Exception {
    var p = create();
    var payload = raw(List.of(row(p.id(), 12L, null)));
    var response =
        mvc.perform(
                post("/api/v1/brands/{id}/metric-imports", brand.getId())
                    .with(authentication(owner))
                    .contentType("application/json")
                    .header("Idempotency-Key", "manual-metrics-key-0001")
                    .content(payload))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();
    var node = mapper.readTree(response.getResponse().getContentAsString());
    var id = UUID.fromString(node.path("id").asText());
    var job = UUID.fromString(node.path("jobId").asText());
    assertThat(response.getResponse().getHeader("Location")).isEqualTo("/api/v1/jobs/" + job);
    worker.execute(worker.claim(id));
    mvc.perform(get("/api/v1/jobs/{id}", job).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.type").value("METRICS_IMPORT"))
        .andExpect(jsonPath("$.rawInput").doesNotExist())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/posts/{id}/metrics", p.id())
                .with(authentication(owner))
                .param("from", FROM.toString())
                .param("until", AT.plusSeconds(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].reach").value(12))
        .andExpect(jsonPath("$.items[0].saves").value(0))
        .andExpect(jsonPath("$.items[0].engagement").isEmpty())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    SecurityContextHolder.getContext().setAuthentication(owner);
    var o = read(p.id(), false).items().getFirst();
    assertThat(o.origin()).isEqualTo("MANUAL_JSON");
    assertThat(o.ingestedBy()).isEqualTo(actorId);
    assertThat(o.ingestedAt()).isAfter(o.observedAt());
    assertThat(metrics.status(id, scope(), false).rawChecksum()).isEqualTo(MetricRow.sha(payload));
  }

  @Test
  void csvAndManualDedupHaveSeparateRawReceipts() {
    var p = create();
    var first = finish(submit(raw(List.of(row(p.id(), 12L, null)))));
    var csv =
        metrics.submit(
            brand.getId(),
            scope(),
            "csv-metrics-key-00001",
            "CSV",
            csv(p.id(), "12").getBytes(StandardCharsets.UTF_8));
    var done = finish(csv);
    assertThat(done.observationIds()).isEqualTo(first.observationIds());
    assertThat(done.origin()).isEqualTo("CSV");
    assertThat(done.rawChecksum()).isNotEqualTo(first.rawChecksum());
    assertThat(read(p.id(), true).items()).hasSize(1);
  }

  @Test
  void correctionAppendsAndStaleCorrectionsFailAtomically() {
    var p = create();
    var first = finish(submit(raw(List.of(row(p.id(), 12L, null)))));
    var id = first.observationIds().getFirst();
    var correction = finish(submit(raw(List.of(row(p.id(), 13L, id)))));
    assertThat(correction.status()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(read(p.id(), false).items().getFirst().revisionNumber()).isEqualTo(2);
    assertThat(read(p.id(), true).items()).hasSize(2);
    var stale = finish(submit(raw(List.of(row(p.id(), 14L, id)))));
    assertThat(stale.status()).isEqualTo(JobStatus.DEAD);
    assertThat(stale.observationIds()).isEmpty();
    assertThat(read(p.id(), true).items()).hasSize(2);
  }

  @Test
  void conflictWithoutCorrectionRollsBackEarlierRows() {
    var a = create();
    var b = create();
    finish(submit(raw(List.of(row(b.id(), 1L, null)))));
    var result = finish(submit(raw(List.of(row(a.id(), 2L, null), row(b.id(), 3L, null)))));
    assertThat(result.status()).isEqualTo(JobStatus.DEAD);
    assertThat(read(a.id(), true).items()).isEmpty();
    assertThat(read(b.id(), true).items()).hasSize(1);
  }

  @Test
  void sameKeyReplayAndPayloadCollision() {
    var p = create();
    var key = "replay-metrics-key-001";
    var payload = raw(List.of(row(p.id(), 5L, null)));
    var e = metrics.submit(brand.getId(), scope(), key, "MANUAL_JSON", payload);
    assertThat(metrics.submit(brand.getId(), scope(), key, "MANUAL_JSON", payload).id())
        .isEqualTo(e.id());
    finish(e);
    assertThat(metrics.submit(brand.getId(), scope(), key, "MANUAL_JSON", payload).status())
        .isEqualTo(JobStatus.SUCCEEDED);
    assertThatThrownBy(
            () ->
                metrics.submit(
                    brand.getId(),
                    scope(),
                    key,
                    "MANUAL_JSON",
                    raw(List.of(row(p.id(), 6L, null)))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invalidUnknownAuditForeignPlatformAndFutureRejected() throws Exception {
    var p = create();
    String valid = new String(raw(List.of(row(p.id(), 1L, null))), StandardCharsets.UTF_8);
    for (var content :
        List.of(
            valid.replace("\"observations\":", "\"actorId\":\"" + actorId + "\",\"observations\":"),
            valid.replace("\"reach\":1", "\"reach\":-1"),
            valid.replace("INSTAGRAM", "TIKTOK"),
            valid.replace(p.id().toString(), UUID.randomUUID().toString()))) {
      mvc.perform(
              post("/api/v1/brands/{id}/metric-imports", brand.getId())
                  .with(authentication(owner))
                  .contentType("application/json")
                  .header("Idempotency-Key", "invalid-metrics-key-001")
                  .content(content))
          .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 404));
    }
    var future =
        clock.instant().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var r =
        new MetricRow(
            p.id(),
            PlaybookContent.Channel.INSTAGRAM,
            FROM,
            UNTIL,
            future,
            1L,
            null,
            null,
            null,
            null,
            null);
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(() -> submit(raw(List.of(r)))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void authenticationRoleForeignJobAndQueryBounds() throws Exception {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    SecurityContextHolder.clearContext();
    mvc.perform(get("/api/v1/metric-imports/{id}", e.id())).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/metric-imports/{id}", e.id())
                .with(authentication(auth(actorId, UserRole.VIEWER, scope()))))
        .andExpect(status().isForbidden());
    var outsider =
        users.saveAndFlush(
            new CadenceUser("metrics-foreign-" + UUID.randomUUID(), UserRole.OWNER, Set.of()));
    var foreign = auth(outsider.getId(), UserRole.OWNER, Set.of());
    for (var path : List.of("/api/v1/jobs/" + e.jobId(), "/api/v1/metric-imports/" + e.id()))
      mvc.perform(get(path).with(authentication(foreign))).andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/posts/{id}/metrics", p.id())
                .with(authentication(owner))
                .param("from", FROM.toString())
                .param("until", AT.toString())
                .param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bodyCapAndWrongEncodingRejected() throws Exception {
    create();
    mvc.perform(
            post("/api/v1/brands/{id}/metric-imports", brand.getId())
                .with(authentication(owner))
                .contentType("text/csv")
                .header("Idempotency-Key", "oversize-metrics-key-001")
                .content(new byte[131073]))
        .andExpect(status().isPayloadTooLarge());
    mvc.perform(
            post("/api/v1/brands/{id}/metric-imports", brand.getId())
                .with(authentication(owner))
                .contentType("text/csv;charset=ISO-8859-1")
                .header("Idempotency-Key", "encoding-metrics-key-001")
                .content("bad"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void historicalSourceSurvivesCreativeRevisionChanges() {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    var sourceEntity = strategies.findById(source.getId()).orElseThrow();
    sourceEntity.lock();
    strategies.saveAndFlush(sourceEntity);
    assertThat(finish(e).status()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(read(p.id(), false).items()).hasSize(1);
  }

  @Test
  void archivedParentDeniesWorkerPublication() {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    var t = worker.claim(e.id());
    var b = brands.findById(brand.getId()).orElseThrow();
    b.archive();
    brands.saveAndFlush(b);
    worker.execute(t);
    assertThat(observations.findTopByPostIdAndObservedAtOrderByRevisionNumberDesc(p.id(), AT))
        .isEmpty();
    assertThat(imports.findById(e.id()).orElseThrow().getJob().getStatus())
        .isEqualTo(JobStatus.DEAD);
  }

  @Test
  void outOfOrderHistoryAndForeignCorrectionAreExplicit() {
    var p = create();
    var a = finish(submit(raw(List.of(row(p.id(), 1L, null)))));
    var older =
        new MetricRow(
            p.id(),
            PlaybookContent.Channel.INSTAGRAM,
            FROM,
            UNTIL,
            AT.minusSeconds(1),
            2L,
            null,
            null,
            null,
            null,
            null);
    assertThat(finish(submit(raw(List.of(older)))).status()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(read(p.id(), false).items()).hasSize(2);
    var foreign = finish(submit(raw(List.of(row(p.id(), 1L, UUID.randomUUID())))));
    assertThat(foreign.status()).isEqualTo(JobStatus.DEAD);
    assertThat(metrics.status(a.id(), scope(), false).observationIds()).hasSize(1);
  }
}
