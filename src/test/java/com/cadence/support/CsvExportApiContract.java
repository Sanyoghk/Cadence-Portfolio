package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.results.*;
import com.cadence.application.services.CsvExportService;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

public abstract class CsvExportApiContract extends PostApiContract {
  @Autowired protected CsvExportUseCase exports;
  @Autowired protected CsvExportService worker;
  @Autowired protected PostCalendarUseCase calendar;

  protected record Scheduled(
      PostDetails post, ScheduleDetails schedule, CsvExportRequest request) {}

  protected Scheduled scheduled() {
    return scheduled(create(), 1);
  }

  protected Scheduled scheduled(PostDetails draft, int day) {
    var p = posts.approve(draft.id(), draft.revisionId(), scope(), draft.lockVersion());
    var instant =
        clock
            .instant()
            .plusSeconds(day * 86400L)
            .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
    var time =
        new CalendarTime("UTC", LocalDateTime.ofInstant(instant, ZoneOffset.UTC), ZoneOffset.UTC);
    var e =
        calendar
            .change(
                brand.getId(),
                scope(),
                List.of(
                    new PostCalendarUseCase.Change(
                        p.id(),
                        p.revisionId(),
                        p.lockVersion(),
                        0,
                        PostScheduleEvent.Action.SCHEDULE,
                        time)))
            .getFirst();
    return new Scheduled(
        p,
        e,
        new CsvExportRequest(
            instant.minusSeconds(3600),
            instant.plusSeconds(27 * 86400),
            "Europe/London",
            List.of(new CsvExportRequest.Selection(p.id(), p.revisionId(), p.lockVersion(), 1))));
  }

  protected CsvExportDetails submit(Scheduled s) {
    return exports.submit(brand.getId(), scope(), "export-key-" + UUID.randomUUID(), s.request());
  }

  @Test
  void mixedPlatformsFourWeekRowsAreOrderedAndUnicodeFormulaTextIsPreservedSafely()
      throws Exception {
    var a = scheduled();
    var c = scheduled(create(), 15);
    var d = scheduled(create(), 22);
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
    var text =
        new PostContent(
            "TikTok export",
            "\u200b \t=HYPERLINK(\"https://never-fetched.example\")\r\nПривет,🌍",
            List.of("#тест"),
            PostFormat.VIDEO,
            "Demo media prompt",
            null);
    var b = scheduled(posts.create(brand.getId(), scope(), strategy.getId(), 1, channel, text), 8);
    var selections =
        List.of(
            d.request().selections().getFirst(),
            b.request().selections().getFirst(),
            c.request().selections().getFirst(),
            a.request().selections().getFirst());
    var request =
        new CsvExportRequest(a.request().from(), a.request().until(), "Europe/London", selections);
    var e =
        exports.submit(brand.getId(), scope(), "mixed-export-key-" + UUID.randomUUID(), request);
    worker.execute(worker.claim(e.id()));
    var bytes = exports.download(e.id(), scope()).bytes();
    var csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(csv)
        .contains(
            "INSTAGRAM",
            "TIKTOK",
            "\"'\u200b \t=HYPERLINK(\"\"https://never-fetched.example\"\")\r\nПривет,🌍\"",
            "#тест");
    assertThat(csv.indexOf(a.post().id().toString()))
        .isLessThan(csv.indexOf(b.post().id().toString()));
    assertThat(csv.indexOf(b.post().id().toString()))
        .isLessThan(csv.indexOf(c.post().id().toString()));
    assertThat(csv.indexOf(c.post().id().toString()))
        .isLessThan(csv.indexOf(d.post().id().toString()));
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("var/prompt18-verification"));
    java.nio.file.Files.write(
        java.nio.file.Path.of("var/prompt18-verification/fixture.csv"), bytes);
  }

  @Test
  void approvedFourWeekSnapshotJobPrivateDownloadAndCoherentReplay() throws Exception {
    var s = scheduled();
    var key = "export-key-" + UUID.randomUUID();
    var result =
        mvc.perform(
                post("/api/v1/brands/" + brand.getId() + "/exports")
                    .with(authentication(owner))
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(s.request())))
            .andExpect(status().isAccepted())
            .andExpect(header().exists("Location"))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andReturn();
    var e = mapper.readValue(result.getResponse().getContentAsString(), CsvExportDetails.class);
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(owner);
    assertThat(e.status()).isEqualTo(JobStatus.PENDING);
    assertThat(exports.submit(brand.getId(), scope(), key, s.request()).jobId())
        .isEqualTo(e.jobId());
    assertThatThrownBy(() -> exports.download(e.id(), scope()))
        .isInstanceOf(IllegalStateException.class);
    var ticket = worker.claim(e.id());
    assertThat(ticket).isNotNull();
    assertThat(worker.claim(e.id())).isNull();
    worker.execute(ticket);
    worker.execute(ticket);
    var d = exports.download(e.id(), scope());
    String csv = new String(d.bytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(csv)
        .contains(
            "CADENCE_CSV_V1",
            "APPROVED_CREATIVE_V1",
            s.post().revisionId().toString(),
            s.post().source().checksum(),
            "TEXT_ONLY",
            e.id().toString());
    assertThat(exports.download(e.id(), scope()).bytes()).isEqualTo(d.bytes());
    assertThat(d.checksum()).isEqualTo(MediaObjectKey.hash(d.bytes()));
    mvc.perform(get("/api/v1/jobs/" + e.jobId()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    mvc.perform(get("/api/v1/exports/" + e.id() + "/download").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Accept-Ranges", "none"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"cadence-export-" + e.id() + ".csv\""));
    mvc.perform(
            get("/api/v1/exports/" + e.id() + "/download")
                .with(authentication(owner))
                .header("Range", "bytes=0-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void oneUnknownSelectedSourceRejectsEntireBatchAndForeignBrandCannotAdoptIt() throws Exception {
    var s = scheduled();
    var selections = new ArrayList<>(s.request().selections());
    selections.add(new CsvExportRequest.Selection(UUID.randomUUID(), UUID.randomUUID(), 1, 1));
    var bad =
        new CsvExportRequest(
            s.request().from(), s.request().until(), s.request().timezone(), selections);
    assertThatThrownBy(() -> exports.submit(brand.getId(), scope(), "mixed-invalid-key-001", bad))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    var other = brands.saveAndFlush(new Brand("Other Brand", workspace));
    assertThatThrownBy(
            () -> exports.submit(other.getId(), scope(), "foreign-source-key-01", s.request()))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    mvc.perform(
            post("/api/v1/brands/" + brand.getId() + "/exports")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "export-key-123456")
                .content(json(s.request()))
                .with(authentication(auth(actorId, UserRole.EDITOR, scope()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsDraftStaleSchedulePayloadCollisionAndDoesNotFallBackToPriorApproval() {
    var s = scheduled();
    var key = "export-key-" + UUID.randomUUID();
    var e = exports.submit(brand.getId(), scope(), key, s.request());
    var other =
        new CsvExportRequest(
            s.request().from(), s.request().until(), "UTC", s.request().selections());
    assertThatThrownBy(() -> exports.submit(brand.getId(), scope(), key, other))
        .isInstanceOf(IllegalStateException.class);
    var branch =
        posts
            .edit(
                s.post().id(),
                s.post().revisionId(),
                scope(),
                s.post().lockVersion(),
                content("New draft"))
            .details();
    assertThat(branch.status()).isEqualTo(PostRevision.State.DRAFT);
    assertThatThrownBy(() -> submit(s)).isInstanceOf(IllegalStateException.class);
    worker.execute(worker.claim(e.id()));
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.DEAD);
    assertThatThrownBy(() -> exports.download(e.id(), scope()))
        .isInstanceOf(IllegalStateException.class);
    // Replay retains original operation even when captured content later became stale.
    assertThat(exports.submit(brand.getId(), scope(), key, s.request()).id()).isEqualTo(e.id());
  }

  @Test
  void downloadRechecksSourceScheduleAndDoesNotRewriteSucceededHistory() {
    var s = scheduled();
    var e = submit(s);
    worker.execute(worker.claim(e.id()));
    calendar.change(
        brand.getId(),
        scope(),
        List.of(
            new PostCalendarUseCase.Change(
                s.post().id(),
                s.post().revisionId(),
                s.post().lockVersion(),
                1,
                PostScheduleEvent.Action.CANCEL,
                null)));
    assertThatThrownBy(() -> exports.download(e.id(), scope()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
  }

  @Test
  void scopedAuthorizationStrictInputAndBoundedSelection() throws Exception {
    var s = scheduled();
    var e = submit(s);
    var foreign = auth(UUID.randomUUID(), UserRole.OWNER, Set.of(UUID.randomUUID()));
    for (var path :
        List.of(
            "/api/v1/exports/" + e.id(),
            "/api/v1/exports/" + e.id() + "/download",
            "/api/v1/jobs/" + e.jobId())) {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).with(authentication(foreign))).andExpect(status().isNotFound());
      mvc.perform(get(path).with(authentication(auth(actorId, UserRole.VIEWER, scope()))))
          .andExpect(status().isForbidden());
    }
    var body = mapper.readTree(json(s.request()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("approvedBy", actorId.toString());
    mvc.perform(
            post("/api/v1/brands/" + brand.getId() + "/exports")
                .with(authentication(owner))
                .header("Idempotency-Key", "export-key-123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/brands/" + brand.getId() + "/exports")
                .with(authentication(owner))
                .header("Idempotency-Key", "../../secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(s.request())))
        .andExpect(status().isBadRequest());
    var copy = mapper.readTree(json(s.request()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) copy.path("selections").get(0))
        .remove("revisionVersion");
    mvc.perform(
            post("/api/v1/brands/" + brand.getId() + "/exports")
                .with(authentication(owner))
                .header("Idempotency-Key", "export-key-123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(copy.toString()))
        .andExpect(status().isBadRequest());
  }
}
