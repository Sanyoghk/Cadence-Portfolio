package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.results.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class PerformanceApiContract extends PostApiContract {
  @Test
  void exactFourWeekPeriodIsSupportedWithoutSummingDailyOrWeeklySnapshots() {
    var p = approved();
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    var start = q.untilDate().minusDays(28);
    var period =
        new PerformanceQuery(
            start,
            q.untilDate(),
            "UTC",
            q.dimension(),
            q.platform(),
            0,
            50,
            PerformanceQuery.Bucket.PERIOD);
    observation(
        new MetricRow(
            p.id(),
            p.platform(),
            period.from(),
            period.until(),
            clock.instant(),
            100L,
            25L,
            0L,
            null,
            null,
            null));
    var r = performance.read(brand.getId(), scope(), period);
    assertThat(r.selectedRows()).isEqualTo(1);
    assertThat(r.skippedLocalDays()).isZero();
    assertThat(r.items().getFirst().windowSeconds()).isEqualTo(28 * 86400L);
    assertThat(r.items().getFirst().rates().getFirst().percent()).isEqualByComparingTo("25.0000");
    assertThat(report(PerformanceQuery.Dimension.PLATFORM).incompatibleWindows()).isEqualTo(1);
    assertThat(report(PerformanceQuery.Dimension.PLATFORM).selectedRows()).isZero();
  }

  @Autowired protected PerformanceService performanceService;

  @Test
  void queryCapacityFailsClosedWithoutPartialStatistics() throws Exception {
    var slots =
        (java.util.concurrent.Semaphore)
            org.springframework.test.util.ReflectionTestUtils.getField(performanceService, "slots");
    assertThat(slots.tryAcquire(2)).isTrue();
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    try {
      mvc.perform(
              get("/api/v1/brands/{id}/performance", brand.getId())
                  .with(authentication(owner))
                  .param("fromDate", q.fromDate().toString())
                  .param("untilDate", q.untilDate().toString())
                  .param("timezone", "UTC"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("performance_unavailable"));
    } finally {
      slots.release(2);
    }
  }

  @Test
  void attributionCorrectionPreservesValuesSourceTimeAndHistory() {
    var p = approved();
    var o = observation(p, 100, 20, 1, 1, 2);
    var first = performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education");
    var before = report(PerformanceQuery.Dimension.PILLAR).items().getFirst().sources().getFirst();
    var next =
        performance.reviseAttribution(o, scope(), p.revisionId(), p.lockVersion(), "community", 1);
    assertThat(next.attributionNumber()).isEqualTo(2);
    assertThat(next.previousId()).isEqualTo(first.id());
    assertThat(performance.attributionVersion(o, scope(), 1).pillarId()).isEqualTo("education");
    assertThat(
            performance
                .reviseAttribution(o, scope(), p.revisionId(), p.lockVersion(), "community", 1)
                .id())
        .isEqualTo(next.id());
    assertThatThrownBy(
            () ->
                performance.reviseAttribution(
                    o, scope(), p.revisionId(), p.lockVersion(), "education", 0))
        .isInstanceOf(IllegalStateException.class);
    var after = report(PerformanceQuery.Dimension.PILLAR).items().getFirst();
    assertThat(after.pillarId()).isEqualTo("community");
    assertThat(after.sources()).hasSize(1);
    assertThat(after.sources().getFirst().observedAt()).isEqualTo(before.observedAt());
    assertThat(after.sources().getFirst().valueChecksum()).isEqualTo(before.valueChecksum());
    assertThat(after.sources().getFirst().attributionNumber()).isEqualTo(2);
  }

  @Test
  void timezonesRangesPaginationAndQueryBounds() throws Exception {
    var p = approved();
    observation(p, 100, 20, 1, 1, 2);
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    var shifted =
        performance.read(
            brand.getId(),
            scope(),
            new PerformanceQuery(
                q.fromDate(),
                q.untilDate(),
                "America/New_York",
                q.dimension(),
                q.platform(),
                0,
                50));
    assertThat(shifted.selectedRows()).isZero();
    assertThat(shifted.incompatibleWindows()).isEqualTo(1);
    var empty =
        performance.read(
            brand.getId(),
            scope(),
            new PerformanceQuery(
                q.fromDate().minusDays(30),
                q.fromDate().minusDays(2),
                "UTC",
                q.dimension(),
                null,
                1,
                50));
    assertThat(empty.totalGroups()).isEqualTo(56);
    assertThat(empty.items()).hasSize(6);
    for (var changed :
        List.of(
            Map.of("size", "51"),
            Map.of("page", "-1"),
            Map.of("dimension", "INVALID"),
            Map.of("timezone", "../UTC"),
            Map.of("untilDate", q.fromDate().plusDays(29).toString()))) {
      var request =
          get("/api/v1/brands/{id}/performance", brand.getId()).with(authentication(owner));
      var params =
          new HashMap<>(
              Map.of(
                  "fromDate",
                  q.fromDate().toString(),
                  "untilDate",
                  q.untilDate().toString(),
                  "timezone",
                  "UTC"));
      params.putAll(changed);
      params.forEach((k, v) -> request.param(k, v));
      mvc.perform(request).andExpect(status().isBadRequest());
    }
  }

  @Test
  void platformAndFormatGroupsDoNotCombineInstagramAndTiktok() {
    var ig = approved();
    observation(ig, 100, 10, 1, 0, 1);
    var original = StrategyFixtures.playbook();
    var channel = PlaybookContent.Channel.TIKTOK;
    var book =
        new PlaybookContent(
            2,
            original.pillarIds(),
            original.audienceAssumptions(),
            List.of(
                new PlaybookContent.ChannelPlan(
                    channel, 100, original.channels().getFirst().goals())),
            original.toneSpectrum(),
            original.competitiveWhitespace(),
            original.fourWeekOutline().stream()
                .map(
                    w ->
                        new PlaybookContent.Week(
                            w.week(),
                            w.slots().stream()
                                .map(
                                    v ->
                                        new PlaybookContent.Slot(
                                            v.slotId(),
                                            channel,
                                            v.pillarId(),
                                            v.audienceId(),
                                            v.topic(),
                                            v.intent(),
                                            v.dayOffset()))
                                .toList()))
                .toList());
    var strat =
        new Strategy(
            brand,
            2,
            new PublishingCadence(3, "UTC", List.of(LocalTime.NOON)),
            List.of(
                new ContentPillar("Education", 60, "Explain value"),
                new ContentPillar("Community", 40, "Invite feedback")));
    strat.replacePlaybook(StrategyContent.from(strat), book);
    strat.recordHumanEdit(actorId, clock.instant());
    strat = strategies.saveAndFlush(strat);
    strategyLifecycle.approve(strat.getId(), scope(), 0, actorId);
    var tt =
        posts.create(
            brand.getId(),
            scope(),
            strat.getId(),
            1,
            channel,
            new PostContent("TikTok", "Video", List.of(), PostFormat.VIDEO, "Idea", "Script"));
    tt = posts.approve(tt.id(), tt.revisionId(), scope(), tt.lockVersion());
    var o = observation(tt, 200, 60, 2, 1, 4);
    performance.attribute(o, scope(), tt.revisionId(), tt.lockVersion(), "education");
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    var all =
        performance.read(
            brand.getId(),
            scope(),
            new PerformanceQuery(q.fromDate(), q.untilDate(), "UTC", q.dimension(), null, 0, 50));
    assertThat(all.selectedRows()).isEqualTo(2);
    assertThat(all.items()).hasSize(2);
    assertThat(all.items().get(0).rates().getFirst().percent()).isEqualByComparingTo("10.0000");
    assertThat(all.items().get(1).rates().getFirst().percent()).isEqualByComparingTo("30.0000");
    assertThat(all.items().get(0).comparisonKey()).isNotEqualTo(all.items().get(1).comparisonKey());
    assertThat(report(PerformanceQuery.Dimension.PLATFORM).selectedRows()).isEqualTo(1);
  }

  @Autowired protected PerformanceUseCase performance;
  @Autowired protected ImportMetricsUseCase metricImports;
  @Autowired protected MetricImportService metricWorker;

  protected PerformanceQuery query(PerformanceQuery.Dimension d) {
    var today = LocalDate.now(clock);
    return new PerformanceQuery(
        today.minusDays(1), today, "UTC", d, PlaybookContent.Channel.INSTAGRAM, 0, 50);
  }

  protected PostDetails approved() {
    var p = create();
    return posts.approve(p.id(), p.revisionId(), scope(), p.lockVersion());
  }

  protected UUID observation(
      PostDetails p, long reach, long engagement, long saves, long signups, long clicks) {
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    return observation(
        new MetricRow(
            p.id(),
            p.platform(),
            q.from(),
            q.until(),
            clock.instant(),
            reach,
            engagement,
            saves,
            signups,
            clicks,
            null));
  }

  protected UUID observation(MetricRow row) {
    try {
      var e =
          metricImports.submit(
              brand.getId(),
              scope(),
              "performance-key-" + UUID.randomUUID(),
              "MANUAL_JSON",
              mapper.writeValueAsBytes(new MetricImportParser.Manual(List.of(row))));
      metricWorker.execute(metricWorker.claim(e.id()));
      var done = metricImports.status(e.id(), scope(), false);
      assertThat(done.status()).isEqualTo(JobStatus.SUCCEEDED);
      return done.observationIds().getFirst();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  protected PerformanceReport report(PerformanceQuery.Dimension d) {
    return performance.read(brand.getId(), scope(), query(d));
  }

  @Test
  void fivePostManualArithmeticPillarFormatAndFullProvenance() {
    long[] n = {1, 2, 9, 8, 5};
    for (int i = 0; i < 5; i++) {
      var p = approved();
      var o = observation(p, (i + 1) * 10, n[i], i, i < 3 ? i == 0 ? 0 : 1 : 2, (i + 1) * 2);
      performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education");
    }
    for (var d : PerformanceQuery.Dimension.values()) {
      var r = report(d);
      assertThat(r.selectedRows()).isEqualTo(5);
      var g = r.items().getFirst();
      assertThat(g.posts()).isEqualTo(5);
      var rates = g.rates();
      assertThat(rates.get(0).numeratorTotal()).isEqualTo(25);
      assertThat(rates.get(0).denominatorTotal()).isEqualTo(150);
      assertThat(rates.get(0).percent()).isEqualByComparingTo("16.6667");
      assertThat(rates.get(1).percent()).isEqualByComparingTo("6.6667");
      assertThat(rates.get(2).percent()).isEqualByComparingTo("20.0000");
      assertThat(rates.get(3).percent()).isEqualByComparingTo("20.0000");
      assertThat(rates).allMatch(PerformanceMetrics.PerformanceRate::comparisonEligible);
      assertThat(g.sources())
          .allSatisfy(
              s -> {
                assertThat(s.valueChecksum()).hasSize(64);
                assertThat(s.strategyId()).isEqualTo(source.getId());
                assertThat(s.strategyChecksum()).hasSize(64);
                assertThat(s.attributedBy()).isEqualTo(actorId);
                assertThat(s.attributionId()).isNotNull();
              });
      if (d == PerformanceQuery.Dimension.PILLAR) assertThat(g.pillarId()).isEqualTo("education");
      if (d == PerformanceQuery.Dimension.FORMAT) assertThat(g.format()).isEqualTo(PostFormat.REEL);
    }
  }

  @Test
  void unknownContextIsNotCurrentFormatAndAnnotationReplayIsImmutable() {
    var p = approved();
    var o = observation(p, 10, 1, 0, 0, 1);
    assertThat(report(PerformanceQuery.Dimension.FORMAT).items().getFirst().format()).isNull();
    var a = performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education");
    assertThat(performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education").id())
        .isEqualTo(a.id());
    assertThatThrownBy(
            () -> performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "community"))
        .isInstanceOf(IllegalStateException.class);
    posts.edit(
        p.id(),
        p.revisionId(),
        scope(),
        p.lockVersion(),
        new PostContent("Image", "New", List.of(), PostFormat.IMAGE, "Image idea", null));
    assertThat(report(PerformanceQuery.Dimension.FORMAT).items().getFirst().format())
        .isEqualTo(PostFormat.REEL);
    var s = strategies.findById(source.getId()).orElseThrow();
    s.lock();
    strategies.saveAndFlush(s);
    assertThat(report(PerformanceQuery.Dimension.PILLAR).items().getFirst().pillarId())
        .isEqualTo("education");
  }

  @Test
  void missingKnownZeroSparseAndInconsistentPairs() {
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    for (int i = 0; i < 3; i++) {
      var p = approved();
      observation(
          new MetricRow(
              p.id(),
              p.platform(),
              q.from(),
              q.until(),
              clock.instant(),
              i == 0 ? null : 0L,
              i == 2 ? 1L : 0L,
              0L,
              null,
              null,
              null));
    }
    var r = report(PerformanceQuery.Dimension.PLATFORM).items().getFirst().rates().getFirst();
    assertThat(r.percent()).isNull();
    assertThat(r.missingPairs()).isEqualTo(1);
    assertThat(r.zeroDenominatorPairs()).isEqualTo(1);
    assertThat(r.inconsistentPairs()).isEqualTo(1);
    assertThat(r.comparisonEligible()).isFalse();
  }

  @Test
  void latestSnapshotNotImportCountAndMovedCorrectionRemovesOldWindow() {
    var p = approved();
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    var first =
        new MetricRow(
            p.id(),
            p.platform(),
            q.from(),
            q.until(),
            clock.instant(),
            10L,
            1L,
            null,
            null,
            null,
            null);
    observation(first);
    var later =
        new MetricRow(
            p.id(),
            p.platform(),
            q.from(),
            q.until(),
            clock.instant(),
            20L,
            2L,
            null,
            null,
            null,
            null);
    var o = observation(later);
    var r = report(PerformanceQuery.Dimension.PLATFORM);
    assertThat(r.selectedRows()).isEqualTo(1);
    assertThat(r.duplicateSnapshots()).isEqualTo(1);
    assertThat(r.items().getFirst().rates().getFirst().denominatorTotal()).isEqualTo(20);
    observation(
        new MetricRow(
            p.id(),
            p.platform(),
            q.from().minusSeconds(86400),
            q.from(),
            later.observedAt(),
            30L,
            3L,
            null,
            null,
            null,
            o));
    var corrected = report(PerformanceQuery.Dimension.PLATFORM);
    assertThat(corrected.selectedRows()).isEqualTo(1);
    assertThat(corrected.items().getFirst().rates().getFirst().denominatorTotal()).isEqualTo(10);
    assertThat(
            performance
                .read(
                    brand.getId(),
                    scope(),
                    new PerformanceQuery(
                        q.fromDate().minusDays(1),
                        q.fromDate(),
                        "UTC",
                        q.dimension(),
                        q.platform(),
                        0,
                        50))
                .items()
                .getFirst()
                .rates()
                .getFirst()
                .denominatorTotal())
        .isEqualTo(30);
  }

  @Test
  void correctionDoesNotInheritClassificationAndStaleOldTargetDenied() {
    var p = approved();
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    var row =
        new MetricRow(
            p.id(),
            p.platform(),
            q.from(),
            q.until(),
            clock.instant(),
            10L,
            1L,
            null,
            null,
            null,
            null);
    var old = observation(row);
    performance.attribute(old, scope(), p.revisionId(), p.lockVersion(), "education");
    var corrected =
        observation(
            new MetricRow(
                p.id(),
                p.platform(),
                q.from(),
                q.until(),
                row.observedAt(),
                11L,
                1L,
                null,
                null,
                null,
                old));
    assertThat(report(PerformanceQuery.Dimension.FORMAT).items().getFirst().format()).isNull();
    assertThat(performance.attribution(old, scope()).observationId()).isEqualTo(old);
    performance.attribute(corrected, scope(), p.revisionId(), p.lockVersion(), "community");
    assertThat(report(PerformanceQuery.Dimension.PILLAR).items().getFirst().pillarId())
        .isEqualTo("community");
  }

  @Test
  void historicalStaleAndIncompatibleWindowsStayExplicit() {
    var p = create();
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    Instant oldFrom = q.from().minusSeconds(10 * 86400), oldUntil = oldFrom.plusSeconds(86400);
    observation(
        new MetricRow(
            p.id(), p.platform(), oldFrom, oldUntil, oldUntil, 100L, 20L, 0L, null, null, null));
    var oldQ =
        new PerformanceQuery(
            q.fromDate().minusDays(10),
            q.fromDate().minusDays(9),
            "UTC",
            q.dimension(),
            q.platform(),
            0,
            50);
    var rate = performance.read(brand.getId(), scope(), oldQ).items().getFirst().rates().getFirst();
    assertThat(rate.percent()).isEqualByComparingTo("20.0000");
    assertThat(rate.issues()).contains(PerformanceMetrics.Issue.STALE);
    observation(
        new MetricRow(
            p.id(),
            p.platform(),
            q.from(),
            q.until().minusSeconds(1),
            clock.instant(),
            100L,
            20L,
            null,
            null,
            null,
            null));
    assertThat(report(PerformanceQuery.Dimension.PLATFORM).incompatibleWindows()).isEqualTo(1);
    assertThat(report(PerformanceQuery.Dimension.PLATFORM).selectedRows()).isZero();
  }

  @Test
  void dstTimezoneAndEmptyDataAreNotZeroRates() {
    var r =
        performance.read(
            brand.getId(),
            scope(),
            new PerformanceQuery(
                LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 10),
                "America/New_York",
                PerformanceQuery.Dimension.PLATFORM,
                PlaybookContent.Channel.INSTAGRAM,
                0,
                50));
    assertThat(r.items().stream().map(PerformanceReport.PerformanceGroup::windowSeconds))
        .containsExactly(82800L, 86400L);
    assertThat(r.items().get(0).comparisonKey()).isNotEqualTo(r.items().get(1).comparisonKey());
    assertThat(r.items())
        .allSatisfy(g -> assertThat(g.rates()).allSatisfy(v -> assertThat(v.percent()).isNull()));
  }

  @Test
  void httpContractAuthorizationAndMassAssignment() throws Exception {
    var p = approved();
    var o = observation(p, 100, 20, 1, 1, 2);
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    String path = "/api/v1/brands/" + brand.getId() + "/performance";
    mvc.perform(
            get(path)
                .with(authentication(owner))
                .param("fromDate", q.fromDate().toString())
                .param("untilDate", q.untilDate().toString())
                .param("timezone", "UTC"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    var payload =
        new HashMap<String, Object>(
            Map.of(
                "postRevisionId",
                p.revisionId(),
                "expectedVersion",
                p.lockVersion(),
                "expectedAttributionVersion",
                0,
                "pillarId",
                "education"));
    mvc.perform(
            put("/api/v1/metric-observations/{id}/attribution", o)
                .with(authentication(owner))
                .contentType("application/json")
                .content(json(payload)))
        .andExpect(status().isOk())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    payload.put("actorId", actorId);
    mvc.perform(
            put("/api/v1/metric-observations/{id}/attribution", o)
                .with(authentication(owner))
                .contentType("application/json")
                .content(json(payload)))
        .andExpect(status().isBadRequest());
    SecurityContextHolder.clearContext();
    mvc.perform(
            get(path)
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.anonymous())
                .param("fromDate", q.fromDate().toString())
                .param("untilDate", q.untilDate().toString())
                .param("timezone", "UTC"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get(path)
                .with(authentication(auth(actorId, UserRole.VIEWER, scope())))
                .param("fromDate", q.fromDate().toString())
                .param("untilDate", q.untilDate().toString())
                .param("timezone", "UTC"))
        .andExpect(status().isForbidden());
    var foreignWorkspace = workspaces.saveAndFlush(new Workspace("Other tenant", "Creator"));
    var outsider =
        users.saveAndFlush(
            new CadenceUser(
                "analytics-foreign-" + UUID.randomUUID(),
                UserRole.OWNER,
                Set.of(foreignWorkspace)));
    mvc.perform(
            get(path)
                .with(
                    authentication(
                        auth(outsider.getId(), UserRole.OWNER, Set.of(foreignWorkspace.getId()))))
                .param("fromDate", q.fromDate().toString())
                .param("untilDate", q.untilDate().toString())
                .param("timezone", "UTC"))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/metric-observations/{id}/attribution", o)
                .with(
                    authentication(
                        auth(outsider.getId(), UserRole.OWNER, Set.of(foreignWorkspace.getId())))))
        .andExpect(status().isNotFound());
  }

  @Test
  void unknownPillarDraftForeignAndStaleVersionDenied() {
    var p = approved();
    var o = observation(p, 1, 1, 0, 0, 0);
    var other = approved();
    assertThatThrownBy(
            () ->
                performance.attribute(
                    o, scope(), other.revisionId(), other.lockVersion(), "education"))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                performance.attribute(o, scope(), p.revisionId(), p.lockVersion() + 1, "education"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "foreign"))
        .isInstanceOf(IllegalArgumentException.class);
    var draft = create();
    var d = observation(draft, 1, 1, 0, 0, 0);
    assertThatThrownBy(
            () -> performance.attribute(d, scope(), draft.revisionId(), draft.lockVersion(), null))
        .isInstanceOf(IllegalStateException.class);
  }
}
