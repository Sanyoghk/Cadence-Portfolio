package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.CreatorReportUseCase;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

public abstract class CreatorReportApiContract extends RecommendationApiContract {
  @Autowired protected CreatorReportUseCase reportApi;
  @Autowired protected CreatorReportService reportWorker;
  @Autowired protected CreatorReportRepository reportRows;
  @Autowired protected DurableJobRepository reportJobs;
  @MockitoSpyBean protected CreatorReportRenderer reportRenderer;
  @MockitoSpyBean protected Clock reportClock;

  protected CreatorReportRequest reportRequest(List<UUID> ids) {
    var q = recommendationRequest();
    return new CreatorReportRequest(
        source.getId(),
        source.getLockVersion(),
        q.fromDate(),
        q.untilDate(),
        "UTC",
        PlaybookContent.Channel.INSTAGRAM,
        PerformanceQuery.Bucket.PERIOD,
        ids);
  }

  protected CreatorReportDetails submitReport(List<UUID> ids, String key) {
    return reportApi.submit(brand.getId(), scope(), key, reportRequest(ids));
  }

  protected void finishReport(CreatorReportDetails r) {
    reportWorker.execute(reportWorker.claim(r.id()));
  }

  @Test
  void reportCapturesMetricsAndOriginalAdviceAndPublishesPrivateHtmlAndJson() throws Exception {
    var evidence = seedRecommendation();
    var advice = generate("report-advice");
    var r = submitReport(List.of(advice.id()), "report-flow");
    assertThat(r.status()).isEqualTo(JobStatus.PENDING);
    assertThatThrownBy(() -> reportApi.result(r.id(), scope()))
        .isInstanceOf(IllegalStateException.class);
    finishReport(r);
    var result = reportApi.result(r.id(), scope());
    assertThat(result.selectedRows()).isEqualTo(10);
    assertThat(result.recommendations()).hasSize(1);
    assertThat(
            result.recommendations().getFirst().supportingObservations().stream()
                .map(CreatorReportSnapshot.Observation::id))
        .containsExactlyInAnyOrderElementsOf(evidence);
    assertThat(result.recommendations().getFirst().confidence()).isEqualTo("UNCALIBRATED");
    assertThat(result.recommendations().getFirst().relationToCapturedMetrics())
        .isEqualTo("MATCHES_CAPTURED_METRICS");
    var html =
        new String(
            reportApi.download(r.id(), scope()).bytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(html)
        .contains(
            "<!doctype html>",
            "Historical recommendations",
            advice.snapshotChecksum(),
            "HYPOTHESIS",
            "UNCALIBRATED");
    assertThat(json(result))
        .doesNotContain(
            "ingestedBy",
            "attributedBy",
            "identitySource",
            "rawChecksum",
            "providerPayload",
            "storageKey");
    assertThat(reportApi.status(r.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
    mvc.perform(get("/api/v1/jobs/{id}", r.jobId()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("CREATOR_REPORT"));
    mvc.perform(get("/api/v1/reports/{id}/download", r.id()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
        .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
  }

  @Test
  void staleAndMissingValuesAreDisclosedInHistoricalReport() {
    var p = create();
    var q = reportRequest(List.of());
    var start = q.fromDate().minusDays(10);
    var end = start.plusDays(1);
    var request =
        new CreatorReportRequest(
            source.getId(),
            source.getLockVersion(),
            start,
            end,
            "UTC",
            q.platform(),
            PerformanceQuery.Bucket.DAY,
            List.of());
    observation(
        new MetricRow(
            p.id(),
            p.platform(),
            request.query(0).from(),
            request.query(0).until(),
            request.query(0).until(),
            100L,
            20L,
            0L,
            null,
            null,
            null));
    var r = reportApi.submit(brand.getId(), scope(), "stale-report", request);
    finishReport(r);
    var g = reportApi.result(r.id(), scope()).groups().getFirst();
    assertThat(g.staleRows()).isEqualTo(1);
    assertThat(g.rates().getFirst().percent()).isEqualByComparingTo("20.0000");
    assertThat(g.rates().getFirst().issues()).contains(PerformanceMetrics.Issue.STALE);
    assertThat(g.rates().getLast().percent()).isNull();
    assertThat(g.rates().getLast().missingPairs()).isEqualTo(1);
    assertThat(reportApi.result(r.id(), scope()).recommendations()).isEmpty();
  }

  @Test
  void emptyAndSparseDataRemainExplicitAndNeverInventAdvice() {
    var empty = submitReport(List.of(), "empty-report");
    finishReport(empty);
    var r = reportApi.result(empty.id(), scope());
    assertThat(r.selectedRows()).isZero();
    assertThat(r.caveats()).contains("NO_DATA");
    assertThat(r.recommendations()).isEmpty();
    var p = approved();
    observation(p, 0, 0, 0, 0, 0);
    var sparse = submitReport(List.of(), "sparse-report");
    finishReport(sparse);
    assertThat(
            reportApi.result(sparse.id(), scope()).groups().getFirst().rates().getFirst().percent())
        .isNull();
    assertThat(
            reportApi
                .result(sparse.id(), scope())
                .groups()
                .getFirst()
                .rates()
                .getFirst()
                .comparisonEligible())
        .isFalse();
  }

  @Test
  void correctionsAfterCaptureDoNotRewriteArtifactOrOriginalAdvice() {
    seedRecommendation();
    var advice = generate("historical-advice");
    var r = submitReport(List.of(advice.id()), "before-correction");
    var old = advice.capture().sources().getFirst();
    var request = reportRequest(List.of(advice.id()));
    observation(
        new MetricRow(
            old.postId(),
            request.platform(),
            request.query(0).from(),
            request.query(0).until(),
            old.observedAt(),
            100L,
            77L,
            1L,
            1L,
            5L,
            old.observationId()));
    finishReport(r);
    var original = reportApi.result(r.id(), scope());
    assertThat(
            original.recommendations().getFirst().supportingObservations().stream()
                .map(CreatorReportSnapshot.Observation::id))
        .contains(old.observationId());
    var before = reportApi.download(r.id(), scope()).checksum();
    var next = submitReport(List.of(advice.id()), "after-correction");
    finishReport(next);
    assertThat(
            reportApi
                .result(next.id(), scope())
                .recommendations()
                .getFirst()
                .relationToCapturedMetrics())
        .isEqualTo("HISTORICAL_DIFFERS_FROM_CAPTURED_METRICS");
    assertThat(reportApi.download(r.id(), scope()).checksum()).isEqualTo(before);
    assertThat(reportRows.findById(r.id()).orElseThrow().getSnapshotChecksum())
        .isEqualTo(r.snapshotChecksum());
  }

  @Test
  void identicalReplayAndPayloadConflictPreserveOneOperation() {
    var r = submitReport(List.of(), "replay-report");
    assertThat(submitReport(List.of(), "replay-report").id()).isEqualTo(r.id());
    var q = reportRequest(List.of());
    assertThatThrownBy(
            () ->
                reportApi.submit(
                    brand.getId(),
                    scope(),
                    "replay-report",
                    new CreatorReportRequest(
                        q.strategyId(),
                        q.expectedStrategyVersion(),
                        q.fromDate().minusDays(1),
                        q.untilDate(),
                        q.timezone(),
                        q.platform(),
                        q.bucket(),
                        List.of())))
        .isInstanceOf(IllegalStateException.class);
    finishReport(r);
    reportWorker.execute(new CreatorReportService.Ticket(r.id(), "stale-worker", 1));
    assertThat(reportApi.status(r.id(), scope(), false).attempts()).isEqualTo(1);
  }

  @Test
  void forgedForeignRecommendationAndMismatchedWindowAreDenied() {
    seedRecommendation();
    var advice = generate("foreign-report-advice");
    var q = reportRequest(List.of(advice.id()));
    assertThatThrownBy(
            () ->
                reportApi.submit(
                    brand.getId(),
                    scope(),
                    "wrong-window-report",
                    new CreatorReportRequest(
                        q.strategyId(),
                        q.expectedStrategyVersion(),
                        q.fromDate().minusDays(1),
                        q.untilDate(),
                        q.timezone(),
                        q.platform(),
                        q.bucket(),
                        q.recommendationIds())))
        .isInstanceOf(IllegalStateException.class);
    setupPost();
    assertThatThrownBy(() -> submitReport(List.of(advice.id()), "foreign-report-selection"))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
  }

  @Test
  void archivedParentDeniesDownloadAndSnapshotIsRetained() {
    var r = submitReport(List.of(), "archive-report");
    finishReport(r);
    var b = brands.findById(brand.getId()).orElseThrow();
    b.archive();
    brands.saveAndFlush(b);
    assertThatThrownBy(() -> reportApi.download(r.id(), scope()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(reportRows.findById(r.id()).orElseThrow().getArtifactChecksum()).isNotNull();
  }

  @Test
  void invalidAndForeignSelectionsAreDenied() {
    assertThatThrownBy(() -> submitReport(List.of(UUID.randomUUID()), "unknown-advice"))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    var q = reportRequest(List.of());
    assertThatThrownBy(
            () ->
                new CreatorReportRequest(
                    q.strategyId(),
                    0,
                    q.fromDate().minusDays(29),
                    q.untilDate(),
                    "UTC",
                    q.platform(),
                    q.bucket(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CreatorReportRequest(
                    q.strategyId(),
                    0,
                    q.fromDate(),
                    q.untilDate(),
                    "UTC",
                    q.platform(),
                    q.bucket(),
                    Collections.nCopies(11, UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                reportApi.submit(
                    brand.getId(),
                    scope(),
                    "wrong-version",
                    new CreatorReportRequest(
                        q.strategyId(),
                        999,
                        q.fromDate(),
                        q.untilDate(),
                        q.timezone(),
                        q.platform(),
                        q.bucket(),
                        List.of())))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> submitReport(List.of(), "../bad-key"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourceChangeDuringRenderDeniesPublicationAndReturnsControlledTerminalCode() {
    var r = submitReport(List.of(), "source-race");
    doAnswer(
            i -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              var changed = strategies.findById(source.getId()).orElseThrow();
              changed.lock();
              strategies.saveAndFlush(changed);
              return i.callRealMethod();
            })
        .when(reportRenderer)
        .render(any(), any());
    finishReport(r);
    assertThat(reportApi.status(r.id(), scope(), false).status()).isEqualTo(JobStatus.DEAD);
    assertThat(reportApi.status(r.id(), scope(), false).errorCode())
        .isEqualTo("REPORT_SOURCE_UNAVAILABLE");
    assertThat(reportRows.findById(r.id()).orElseThrow().getArtifactChecksum()).isNull();
  }

  @Test
  void rendererFailureIsRetryableWithoutPartialResultOrSecretLeak() {
    var r = submitReport(List.of(), "renderer-failure");
    doThrow(new IllegalStateException("private path secret token"))
        .when(reportRenderer)
        .render(any(), any());
    finishReport(r);
    var state = reportApi.status(r.id(), scope(), false);
    assertThat(state.status()).isEqualTo(JobStatus.FAILED);
    assertThat(state.errorCode()).isEqualTo("REPORT_RENDER_FAILED");
    assertThat(reportRows.findById(r.id()).orElseThrow().getArtifactChecksum()).isNull();
  }

  @Test
  void combinedSnapshotAndArtifactCannotExceedReservedTwoMiB() {
    var r = submitReport(List.of(), "oversized-report");
    doReturn(new byte[CreatorReport.MAX_BYTES]).when(reportRenderer).render(any(), any());
    finishReport(r);
    assertThat(reportApi.status(r.id(), scope(), false).status()).isEqualTo(JobStatus.DEAD);
    assertThat(reportApi.status(r.id(), scope(), false).errorCode())
        .isEqualTo("REPORT_OUTPUT_INVALID");
    assertThat(reportRows.findById(r.id()).orElseThrow().getArtifactChecksum()).isNull();
  }

  @Test
  void exclusiveExpiryDoesNotResurrectResultOnReplay() {
    var originalRequest = reportRequest(List.of());
    var r = submitReport(List.of(), "expiry-report");
    finishReport(r);
    doReturn(r.downloadExpiresAt()).when(reportClock).instant();
    try {
      assertThatThrownBy(() -> reportApi.download(r.id(), scope()))
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> reportApi.result(r.id(), scope()))
          .isInstanceOf(IllegalStateException.class);
      var replay = reportApi.submit(brand.getId(), scope(), "expiry-report", originalRequest);
      assertThat(replay.id()).isEqualTo(r.id());
      assertThat(replay.resultState()).isEqualTo("EXPIRED");
      assertThat(replay.downloadPath()).isNull();
    } finally {
      reset(reportClock);
    }
  }

  @Test
  void httpAuthorizationUnknownFieldsNotFoundAndRangeAreControlled() throws Exception {
    var r = submitReport(List.of(), "http-report");
    finishReport(r);
    mvc.perform(get("/api/v1/reports/{id}", r.id()).with(anonymous()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/reports/{id}/download", r.id())
                .with(authentication(auth(actorId, UserRole.VIEWER, scope()))))
        .andExpect(status().isForbidden());
    var outsider = auth(UUID.randomUUID(), UserRole.OWNER, Set.of(UUID.randomUUID()));
    mvc.perform(get("/api/v1/reports/{id}/download", r.id()).with(authentication(outsider)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/jobs/{id}", r.jobId()).with(authentication(outsider)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/reports/{id}/result", UUID.randomUUID()).with(authentication(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/reports/{id}/download", r.id())
                .with(authentication(owner))
                .header("Range", "bytes=0-1"))
        .andExpect(status().isBadRequest());
    var body =
        mapper.convertValue(
            reportRequest(List.of()),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    body.put("actorId", UUID.randomUUID());
    mvc.perform(
            post("/api/v1/brands/{id}/reports", brand.getId())
                .with(authentication(owner))
                .header("Idempotency-Key", "http-mass-assignment")
                .contentType("application/json")
                .content(json(body)))
        .andExpect(status().isBadRequest());
  }
}
