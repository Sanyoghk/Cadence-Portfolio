package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.PostScheduleRepository;
import com.cadence.application.results.*;
import com.cadence.application.usecases.PostCalendarUseCase;
import com.cadence.domain.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class CalendarApiContract extends PostApiContract {
  @Autowired protected PostCalendarUseCase calendar;
  @Autowired protected PostScheduleRepository schedules;

  @Test
  void differentPlatformsMayShareOneBrandInstant() {
    var original = StrategyFixtures.playbook();
    var channel = PlaybookContent.Channel.TIKTOK;
    var playbook =
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
                                    s ->
                                        new PlaybookContent.Slot(
                                            s.slotId(),
                                            channel,
                                            s.pillarId(),
                                            s.audienceId(),
                                            s.topic(),
                                            s.intent(),
                                            s.dayOffset()))
                                .toList()))
                .toList());
    var strategy =
        new Strategy(
            brand,
            2,
            new PublishingCadence(3, "UTC", List.of(LocalTime.NOON)),
            List.of(
                new ContentPillar("Education", 60, "Explain value"),
                new ContentPillar("Community", 40, "Invite feedback")));
    strategy.replacePlaybook(StrategyContent.from(strategy), playbook);
    strategy.recordHumanEdit(actorId, clock.instant());
    strategy = strategies.saveAndFlush(strategy);
    strategyLifecycle.approve(strategy.getId(), scope(), 0, actorId);
    var draft =
        posts.create(
            brand.getId(),
            scope(),
            strategy.getId(),
            1,
            channel,
            new PostContent("TikTok", "Caption", List.of(), PostFormat.VIDEO, "Idea", null));
    var tiktok = posts.approve(draft.id(), draft.revisionId(), scope(), 0);
    var instagram = approved();
    var t = time();
    assertThat(
            calendar.change(
                brand.getId(), scope(), List.of(change(tiktok, 0, t), change(instagram, 0, t))))
        .hasSize(2);
    assertThat(window(t)).hasSize(2);
  }

  @Test
  void fourWeeksAreScheduledAtomicallyAndVisibleAsOneBoundedCalendar() {
    var t = time();
    var changes = new ArrayList<PostCalendarUseCase.Change>();
    for (int week = 0; week < 4; week++)
      changes.add(
          change(
              approved(),
              0,
              new CalendarTime("UTC", t.localTime().plusWeeks(week), ZoneOffset.UTC)));
    assertThat(calendar.change(brand.getId(), scope(), changes)).hasSize(4);
    assertThat(window(t)).hasSize(4);
  }

  @Test
  void actualFutureDstFoldBothOffsetsPersistAndGapIsRejected() throws Exception {
    var rules = ZoneId.of("Europe/London").getRules();
    var fold = rules.nextTransition(clock.instant());
    while (fold.isGap()) fold = rules.nextTransition(fold.getInstant().plusSeconds(1));
    var local = fold.getDateTimeAfter().plusMinutes(30);
    var first = new CalendarTime("Europe/London", local, fold.getOffsetBefore());
    var second = new CalendarTime("Europe/London", local, fold.getOffsetAfter());
    var a = approved();
    var b = approved();
    var results =
        calendar.change(brand.getId(), scope(), List.of(change(a, 0, first), change(b, 0, second)));
    assertThat(results)
        .extracting(ScheduleDetails::scheduledAt)
        .containsExactly(first.instant(), second.instant());
    var gap = rules.nextTransition(clock.instant());
    while (!gap.isGap()) gap = rules.nextTransition(gap.getInstant().plusSeconds(1));
    var item = body(a, 1, first);
    item.put("localTime", gap.getDateTimeBefore().plusMinutes(30).toString());
    item.put("offset", gap.getOffsetBefore().toString());
    mvc.perform(
            post(endpoint())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("changes", List.of(item)))))
        .andExpect(status().isBadRequest());
  }

  protected PostDetails approved() {
    var p = create();
    return posts.approve(p.id(), p.revisionId(), scope(), p.lockVersion());
  }

  protected CalendarTime time() {
    return new CalendarTime(
        "UTC",
        LocalDateTime.ofInstant(clock.instant().plus(5, ChronoUnit.DAYS), ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MINUTES),
        ZoneOffset.UTC);
  }

  protected PostCalendarUseCase.Change change(PostDetails p, int version, CalendarTime time) {
    return new PostCalendarUseCase.Change(
        p.id(),
        p.revisionId(),
        p.lockVersion(),
        version,
        time == null ? PostScheduleEvent.Action.CANCEL : PostScheduleEvent.Action.SCHEDULE,
        time);
  }

  protected ScheduleDetails schedule(PostDetails p, int version, CalendarTime time) {
    return calendar.change(brand.getId(), scope(), List.of(change(p, version, time))).getFirst();
  }

  protected Page<ScheduleDetails> window(CalendarTime t) {
    return calendar.calendar(
        brand.getId(),
        scope(),
        t.instant().minusSeconds(3600),
        t.instant().plus(27, ChronoUnit.DAYS),
        "Europe/London",
        PageRequest.of(0, 100, Sort.by("scheduledAt", "id")));
  }

  protected Map<String, Object> body(PostDetails p, int version, CalendarTime t) {
    var item =
        new LinkedHashMap<String, Object>(
            Map.of(
                "postId",
                p.id(),
                "revisionId",
                p.revisionId(),
                "expectedRevisionVersion",
                p.lockVersion(),
                "expectedScheduleVersion",
                version,
                "action",
                t == null ? "CANCEL" : "SCHEDULE"));
    if (t != null) {
      item.put("timezone", t.timezone());
      item.put("localTime", t.localTime().toString());
      item.put("offset", t.offset().toString());
    }
    return item;
  }

  protected String endpoint() {
    return "/api/v1/brands/" + brand.getId() + "/calendar/schedules";
  }

  @Test
  void scheduleRescheduleCancelHistoryAndRoundTrip() throws Exception {
    var p = approved();
    var t = time();
    var first =
        mvc.perform(
                post(endpoint())
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("changes", List.of(body(p, 0, t))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].scheduleVersion").value(1))
            .andExpect(jsonPath("$[0].eligibility").value("READY"))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andReturn();
    assertThat(
            mapper
                .readTree(first.getResponse().getContentAsString())
                .get(0)
                .get("scheduledAt")
                .asText())
        .isEqualTo(t.instant().toString());
    SecurityContextHolder.getContext().setAuthentication(owner);
    var next =
        new CalendarTime(
            "Europe/London",
            t.instant().plusSeconds(3600).atZone(ZoneId.of("Europe/London")).toLocalDateTime(),
            t.instant().plusSeconds(3600).atZone(ZoneId.of("Europe/London")).getOffset());
    var moved = schedule(p, 1, next);
    assertThat(moved.scheduleVersion()).isEqualTo(2);
    assertThat(window(t).getContent())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.localTime()).isEqualTo(next.localTime());
              assertThat(e.displayLocalTime()).isEqualTo(next.localTime());
            });
    assertThat(schedule(p, 2, null).eligibility()).isEqualTo(ScheduleDetails.Eligibility.CANCELLED);
    assertThat(window(t)).isEmpty();
    var history =
        calendar.history(
            p.id(), scope(), PageRequest.of(0, 10, Sort.by("eventNumber").descending()));
    assertThat(history).hasSize(3);
    assertThat(history.getContent().getLast().eligibility())
        .isEqualTo(ScheduleDetails.Eligibility.HISTORICAL);
    assertThat(posts.read(p.id(), scope()).lockVersion()).isEqualTo(p.lockVersion());
    mvc.perform(get("/api/v1/posts/" + p.id() + "/schedule-history").with(authentication(owner)))
        .andExpect(status().isOk())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }

  @Test
  void atomicBatchCanSwapSlotsAndFailureLeavesNoPartialEvents() {
    var a = approved();
    var b = approved();
    var t = time();
    var later = new CalendarTime("UTC", t.localTime().plusHours(1), ZoneOffset.UTC);
    schedule(a, 0, t);
    schedule(b, 0, later);
    calendar.change(brand.getId(), scope(), List.of(change(a, 1, later), change(b, 1, t)));
    assertThatThrownBy(
            () ->
                calendar.change(
                    brand.getId(), scope(), List.of(change(a, 2, t), change(b, 1, later))))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            schedules.findTopByPostIdOrderByEventNumberDesc(a.id()).orElseThrow().getEventNumber())
        .isEqualTo(2);
    assertThatThrownBy(
            () ->
                calendar.change(brand.getId(), scope(), List.of(change(a, 2, t), change(b, 2, t))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void draftStaleReplayAndContentBranchInvalidateSchedule() {
    var draft = create();
    var t = time();
    assertThatThrownBy(() -> schedule(draft, 0, t)).isInstanceOf(IllegalStateException.class);
    var p = posts.approve(draft.id(), draft.revisionId(), scope(), 0);
    schedule(p, 0, t);
    assertThatThrownBy(() -> schedule(p, 0, t)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> schedule(draft, 1, t)).isInstanceOf(IllegalStateException.class);
    var branch =
        posts.edit(p.id(), p.revisionId(), scope(), p.lockVersion(), content("Branch")).details();
    assertThat(window(t).getContent().getFirst().eligibility())
        .isEqualTo(ScheduleDetails.Eligibility.CONTENT_CHANGED);
    // A stale historical assignment no longer occupies its slot; new approval does not revive it.
    var other = approved();
    schedule(other, 0, t);
    var approvedBranch = posts.approve(branch.id(), branch.revisionId(), scope(), 0);
    assertThatThrownBy(() -> schedule(approvedBranch, 1, t))
        .isInstanceOf(IllegalStateException.class);
    assertThat(schedule(approvedBranch, 1, null).action())
        .isEqualTo(PostScheduleEvent.Action.CANCEL);
  }

  @Test
  void parentAndSourceLifecycleRecheckedWithoutDestroyingHistory() {
    var p = approved();
    var t = time();
    schedule(p, 0, t);
    var s = strategies.findById(source.getId()).orElseThrow();
    s.lock();
    strategies.saveAndFlush(s);
    assertThat(window(t).getContent().getFirst().eligibility())
        .isEqualTo(ScheduleDetails.Eligibility.SOURCE_UNAVAILABLE);
    assertThatThrownBy(() -> schedule(p, 1, t)).isInstanceOf(IllegalStateException.class);
    assertThat(schedule(p, 1, null).action()).isEqualTo(PostScheduleEvent.Action.CANCEL);
    var w = workspaces.findById(workspace.getId()).orElseThrow();
    w.archive();
    workspaces.saveAndFlush(w);
    assertThatThrownBy(() -> schedule(p, 2, t)).isInstanceOf(IllegalStateException.class);
    assertThat(calendar.history(p.id(), scope(), PageRequest.of(0, 10))).hasSize(2);
  }

  @Test
  void foreignIdsAndActualMembershipDenied() throws Exception {
    var p = approved();
    var t = time();
    var foreign = auth(UUID.randomUUID(), UserRole.OWNER, Set.of(UUID.randomUUID()));
    mvc.perform(
            post(endpoint())
                .with(authentication(foreign))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("changes", List.of(body(p, 0, t))))))
        .andExpect(status().isNotFound());
    SecurityContextHolder.getContext().setAuthentication(owner);
    var missing =
        new PostCalendarUseCase.Change(
            UUID.randomUUID(), p.revisionId(), 1, 0, PostScheduleEvent.Action.SCHEDULE, t);
    assertThatThrownBy(() -> calendar.change(brand.getId(), scope(), List.of(missing)))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    var outsider =
        users.saveAndFlush(
            new CadenceUser("calendar-" + UUID.randomUUID(), UserRole.OWNER, Set.of()));
    SecurityContextHolder.getContext()
        .setAuthentication(auth(outsider.getId(), UserRole.OWNER, scope()));
    assertThatThrownBy(() -> schedule(p, 0, t))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"EDITOR", "VIEWER", "ANONYMOUS"})
  void endpointRolesDenied(String role) throws Exception {
    var p = approved();
    var request =
        post(endpoint())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("changes", List.of(body(p, 0, time())))));
    if (!role.equals("ANONYMOUS"))
      request.with(authentication(auth(actorId, UserRole.valueOf(role), scope())));
    if (role.equals("ANONYMOUS")) SecurityContextHolder.clearContext();
    mvc.perform(request).andExpect(status().is(role.equals("ANONYMOUS") ? 401 : 403));
  }

  @Test
  void strictFieldsInvalidOffsetsAndBoundsRejected() throws Exception {
    var p = approved();
    var t = time();
    for (String key : List.of("actorId", "approvedBy", "provider", "sourceOwner", "scheduledAt")) {
      var item = body(p, 0, t);
      item.put(key, "forged");
      mvc.perform(
              post(endpoint())
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("changes", List.of(item)))))
          .andExpect(status().isBadRequest());
    }
    var invalid = body(p, 0, t);
    invalid.put("offset", "+02:00");
    mvc.perform(
            post(endpoint())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("changes", List.of(invalid)))))
        .andExpect(status().isBadRequest());
    for (var items :
        List.of(
            List.of(),
            List.of(body(p, 0, t), body(p, 0, t)),
            Collections.nCopies(85, body(p, 0, t))))
      mvc.perform(
              post(endpoint())
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("changes", items))))
          .andExpect(status().isBadRequest());
    for (var invalidQuery :
        List.of(
            Map.of("size", "101"),
            Map.of("page", "-1"),
            Map.of("timezone", "No/Zone"),
            Map.of("until", t.instant().plus(29, ChronoUnit.DAYS).toString()),
            Map.of("until", t.instant().toString()),
            Map.of("from", "1800-01-01T00:00:00Z"))) {
      var params =
          new HashMap<String, String>(
              Map.of(
                  "from",
                  t.instant().toString(),
                  "until",
                  t.instant().plus(28, ChronoUnit.DAYS).toString(),
                  "timezone",
                  "UTC"));
      params.putAll(invalidQuery);
      var request =
          get("/api/v1/brands/" + brand.getId() + "/calendar").with(authentication(owner));
      params.forEach(request::param);
      mvc.perform(request).andExpect(status().isBadRequest());
    }
  }

  @Test
  void boundedFourWeekCalendarIsHalfOpenAndPaged() throws Exception {
    var p = approved();
    var t = time();
    schedule(p, 0, t);
    assertThat(
            calendar.calendar(
                brand.getId(),
                scope(),
                t.instant().minus(28, ChronoUnit.DAYS),
                t.instant(),
                "UTC",
                PageRequest.of(0, 1)))
        .isEmpty();
    mvc.perform(
            get("/api/v1/brands/" + brand.getId() + "/calendar")
                .param("from", t.instant().toString())
                .param("until", t.instant().plus(28, ChronoUnit.DAYS).toString())
                .param("timezone", "UTC")
                .param("size", "1")
                .with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }
}
