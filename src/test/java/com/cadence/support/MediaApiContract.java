package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.MediaAssetRepository;
import com.cadence.application.results.*;
import com.cadence.application.usecases.MediaAssetUseCase;
import com.cadence.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class MediaApiContract extends PostApiContract {
  @Autowired protected MediaAssetUseCase media;
  @Autowired protected MediaAssetRepository assets;

  @Test
  void tiktokVideoUsesSameVerifiedPrivateAssetContract() {
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
            new PublishingCadence(3, "UTC", List.of(java.time.LocalTime.NOON)),
            List.of(
                new ContentPillar("Education", 60, "Explain value"),
                new ContentPillar("Community", 40, "Invite feedback")));
    strategy.replacePlaybook(StrategyContent.from(strategy), playbook);
    strategy.recordHumanEdit(actorId, clock.instant());
    strategy = strategies.saveAndFlush(strategy);
    strategyLifecycle.approve(strategy.getId(), scope(), 0, actorId);
    var p =
        posts.create(
            brand.getId(),
            scope(),
            strategy.getId(),
            1,
            channel,
            new PostContent("TikTok", "Caption", List.of(), PostFormat.VIDEO, "Idea", null));
    var a = reviewed(DemoMediaFixture.VIDEO_V1);
    var attached =
        posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    assertThat(
            posts.approve(p.id(), p.revisionId(), scope(), attached.lockVersion()).mediaAssetIds())
        .containsExactly(a.id());
  }

  protected MediaDetails fresh(DemoMediaFixture fixture) {
    return media.create(brand.getId(), scope(), fixture);
  }

  protected MediaDetails reviewed(DemoMediaFixture fixture) {
    var a = fresh(fixture);
    return media.review(a.id(), scope(), a.lockVersion(), true);
  }

  @Test
  void verifiedPreviewHumanReviewAttachApproveAndPrivateDelivery() throws Exception {
    var post = create();
    var asset = fresh(DemoMediaFixture.VIDEO_V1);
    assertThat(asset.status()).isEqualTo(MediaAsset.State.AVAILABLE);
    assertThat(asset.moderation()).isEqualTo(MediaAsset.Moderation.PENDING);
    assertThat(asset.warnings())
        .contains("NOT_AI_GENERATED", "DEMO_SCAN_NOT_SECURITY_QUALIFICATION");
    mvc.perform(get("/api/v1/media-assets/" + asset.id() + "/bytes").with(authentication(owner)))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/media-assets/" + asset.id() + "/preview").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentType("video/mp4"))
        .andExpect(header().string("X-Cadence-Media-Origin", "DEMO_FIXTURE"))
        .andExpect(header().string("X-Cadence-Media-Moderation", "PENDING"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Accept-Ranges", "none"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    mvc.perform(
            post("/api/v1/media-assets/" + asset.id() + "/review")
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("expectedVersion", asset.lockVersion(), "approved", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moderation").value("APPROVED_DEMO"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    mvc.perform(
            put("/api/v1/posts/" + post.id() + "/revisions/" + post.revisionId() + "/media")
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("expectedVersion", 0, "assetIds", List.of(asset.id())))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockVersion").value(1))
        .andExpect(jsonPath("$.mediaAssetIds[0]").value(asset.id().toString()))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    SecurityContextHolder.getContext().setAuthentication(owner);
    var approved = posts.approve(post.id(), post.revisionId(), scope(), 1);
    assertThat(approved.mediaAssetIds()).containsExactly(asset.id());
    var delivered =
        mvc.perform(
                get("/api/v1/media-assets/" + asset.id() + "/bytes").with(authentication(owner)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(MediaObjectKey.hash(delivered.getResponse().getContentAsByteArray()))
        .isEqualTo(asset.checksum());
    assertThat(delivered.getResponse().getHeader("Content-Disposition"))
        .contains("demo-" + asset.id())
        .doesNotContain("quarantine", "media/");
  }

  @Test
  void approvedReplacementAndManualBranchKeepImmutableHistory() {
    var p = create();
    var a = reviewed(DemoMediaFixture.VIDEO_V1);
    var b = reviewed(DemoMediaFixture.VIDEO_V1);
    var attached =
        posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    var approved = posts.approve(p.id(), p.revisionId(), scope(), attached.lockVersion());
    var replacement =
        posts.replaceMedia(
            p.id(), p.revisionId(), scope(), approved.lockVersion(), List.of(b.id()));
    assertThat(replacement.created()).isTrue();
    assertThat(replacement.details().status()).isEqualTo(PostRevision.State.DRAFT);
    assertThat(replacement.details().revisedFromId()).isEqualTo(p.revisionId());
    assertThat(posts.revision(p.id(), p.revisionId(), scope()).mediaAssetIds())
        .containsExactly(a.id());
    var latest = posts.approve(p.id(), replacement.details().revisionId(), scope(), 0);
    var manual =
        posts
            .edit(
                p.id(), latest.revisionId(), scope(), latest.lockVersion(), content("Changed text"))
            .details();
    assertThat(manual.mediaAssetIds()).containsExactly(b.id());
    assertThatThrownBy(
            () ->
                posts.replaceMedia(
                    p.id(), p.revisionId(), scope(), approved.lockVersion(), List.of(a.id())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archiveRetainsReferencesAndQuotaButDeniesDeliveryAndApproval() {
    var p = create();
    var a = reviewed(DemoMediaFixture.VIDEO_V1);
    var attached =
        posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    var approved = posts.approve(p.id(), p.revisionId(), scope(), attached.lockVersion());
    var archived = media.archive(a.id(), scope(), a.lockVersion());
    assertThat(archived.checksum()).isEqualTo(a.checksum());
    assertThat(archived.reviewedBy()).isEqualTo(actorId);
    assertThat(assets.reservedBytes(brand.getId())).isEqualTo(a.byteSize());
    assertThatThrownBy(() -> media.delivery(a.id(), scope(), false))
        .isInstanceOf(IllegalStateException.class);
    assertThat(posts.revision(p.id(), p.revisionId(), scope()).mediaAssetIds())
        .containsExactly(a.id());
    assertThatThrownBy(
            () ->
                posts.edit(
                    p.id(), p.revisionId(), scope(), approved.lockVersion(), content("Denied")))
        .isInstanceOf(IllegalStateException.class);
    var removed =
        posts
            .replaceMedia(p.id(), p.revisionId(), scope(), approved.lockVersion(), List.of())
            .details();
    assertThat(removed.mediaAssetIds()).isEmpty();
    assertThat(posts.revision(p.id(), p.revisionId(), scope()).mediaAssetIds())
        .containsExactly(a.id());
  }

  @Test
  void unverifiedWrongFormatDuplicateUnknownAndForeignReferencesDenied() {
    var p = create();
    var pending = fresh(DemoMediaFixture.VIDEO_V1);
    assertThatThrownBy(
            () -> posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(pending.id())))
        .isInstanceOf(IllegalStateException.class);
    var image = reviewed(DemoMediaFixture.IMAGE_V1);
    assertThatThrownBy(
            () -> posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(image.id())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                posts.replaceMedia(
                    p.id(), p.revisionId(), scope(), 0, List.of(image.id(), image.id())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(UUID.randomUUID())))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                posts.replaceMedia(
                    p.id(), p.revisionId(), scope(), 0, Collections.nCopies(11, image.id())))
        .isInstanceOf(IllegalArgumentException.class);
    var other = new Brand("Other", workspace);
    other = brands.saveAndFlush(other);
    var foreign = media.create(other.getId(), scope(), DemoMediaFixture.VIDEO_V1);
    foreign = media.review(foreign.id(), scope(), foreign.lockVersion(), true);
    var foreignId = foreign.id();
    assertThatThrownBy(
            () -> posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(foreignId)))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    assertThat(posts.read(p.id(), scope()).lockVersion()).isZero();
  }

  @Test
  void formatRulesImagesCarouselStoryAndVideoAreExplicit() {
    var a = reviewed(DemoMediaFixture.IMAGE_V1);
    var b = reviewed(DemoMediaFixture.IMAGE_V1);
    for (var format : List.of(PostFormat.IMAGE, PostFormat.CAROUSEL, PostFormat.STORY)) {
      var p =
          posts.create(
              brand.getId(),
              scope(),
              source.getId(),
              1,
              PlaybookContent.Channel.INSTAGRAM,
              new PostContent("Image", "Caption", List.of(), format, "Idea", null));
      var ids = format == PostFormat.CAROUSEL ? List.of(a.id(), b.id()) : List.of(a.id());
      var changed = posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, ids).details();
      if (format == PostFormat.CAROUSEL) {
        ids = List.of(b.id(), a.id());
        changed =
            posts
                .replaceMedia(p.id(), p.revisionId(), scope(), changed.lockVersion(), ids)
                .details();
      }
      assertThat(
              posts.approve(p.id(), p.revisionId(), scope(), changed.lockVersion()).mediaAssetIds())
          .isEqualTo(ids);
    }
  }

  @Test
  void humanRejectionReplayAndParentArchiveAreSafe() {
    var a = fresh(DemoMediaFixture.IMAGE_V1);
    var rejected = media.review(a.id(), scope(), a.lockVersion(), false);
    assertThat(rejected.status()).isEqualTo(MediaAsset.State.REJECTED);
    assertThatThrownBy(() -> media.review(a.id(), scope(), rejected.lockVersion(), true))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> media.delivery(a.id(), scope(), true))
        .isInstanceOf(IllegalStateException.class);
    var b = reviewed(DemoMediaFixture.VIDEO_V1);
    var w = workspaces.findById(workspace.getId()).orElseThrow();
    w.archive();
    workspaces.saveAndFlush(w);
    assertThatThrownBy(() -> media.delivery(b.id(), scope(), false))
        .isInstanceOf(IllegalStateException.class);
    assertThat(media.read(b.id(), scope()).checksum()).isEqualTo(b.checksum());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ANONYMOUS", "EDITOR", "VIEWER", "FOREIGN"})
  void scopedEndpointsDenyOtherCallers(String role) throws Exception {
    var a = reviewed(DemoMediaFixture.IMAGE_V1);
    for (String suffix : List.of("", "/preview", "/bytes")) {
      var req = get("/api/v1/media-assets/" + a.id() + suffix);
      if (role.equals("ANONYMOUS")) SecurityContextHolder.clearContext();
      else
        req.with(
            authentication(
                role.equals("FOREIGN")
                    ? auth(UUID.randomUUID(), UserRole.OWNER, Set.of(UUID.randomUUID()))
                    : auth(actorId, UserRole.valueOf(role), scope())));
      mvc.perform(req)
          .andExpect(
              status().is(role.equals("ANONYMOUS") ? 401 : role.equals("FOREIGN") ? 404 : 403));
    }
  }

  @Test
  void requestAllowlistRangePaginationAndMetadataExposure() throws Exception {
    for (String field :
        List.of(
            "filename",
            "url",
            "origin",
            "checksum",
            "actorId",
            "status",
            "provider",
            "scanResult")) {
      var body = new HashMap<String, Object>(Map.of("fixture", "IMAGE_V1"));
      body.put(field, "forged");
      mvc.perform(
              post("/api/v1/brands/" + brand.getId() + "/media-assets")
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(body)))
          .andExpect(status().isBadRequest());
    }
    SecurityContextHolder.getContext().setAuthentication(owner);
    var a = reviewed(DemoMediaFixture.IMAGE_V1);
    mvc.perform(
            get("/api/v1/media-assets/" + a.id() + "/bytes")
                .header("Range", "bytes=0-10")
                .with(authentication(owner)))
        .andExpect(status().isBadRequest());
    var read =
        mvc.perform(get("/api/v1/media-assets/" + a.id()).with(authentication(owner)))
            .andExpect(status().isOk())
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andReturn();
    assertThat(read.getResponse().getContentAsString())
        .doesNotContain("media/", "quarantine:", "objectKey", "storageRoot");
    mvc.perform(
            get("/api/v1/brands/" + brand.getId() + "/media-assets")
                .param("size", "101")
                .with(authentication(owner)))
        .andExpect(status().isBadRequest());
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThat(media.list(brand.getId(), scope(), PageRequest.of(0, 100))).hasSize(1);
  }
}
