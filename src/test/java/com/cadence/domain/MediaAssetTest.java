package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MediaAssetTest {
  private final UUID actor = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-10-16T12:00:00Z");

  private MediaAsset asset() {
    return new MediaAsset(
        new Brand("Demo", new Workspace("Studio", "Creator")),
        DemoMediaFixture.IMAGE_V1,
        actor,
        now);
  }

  @Test
  void scanAndHumanReviewAreIndependentAndArchiveIsIrreversible() {
    var a = asset();
    assertThatThrownBy(a::requireUsable).isInstanceOf(IllegalStateException.class);
    a.complete(
        ScanVerdict.CLEAN,
        new ScanProvenance("engine", "v1"),
        now.plusSeconds(1),
        now.plusSeconds(2));
    a.requirePreview();
    assertThatThrownBy(a::requireUsable).isInstanceOf(IllegalStateException.class);
    a.review(true, actor, now.plusSeconds(3));
    a.requireUsable();
    assertThatThrownBy(() -> a.review(false, actor, now.plusSeconds(4)))
        .isInstanceOf(IllegalStateException.class);
    a.archive(actor, now.plusSeconds(4));
    assertThatThrownBy(a::requireUsable).isInstanceOf(IllegalStateException.class);
    assertThat(a.getReviewedBy()).isEqualTo(actor);
    assertThat(a.getScanResult()).isEqualTo(ScanVerdict.CLEAN);
    assertThatThrownBy(() -> a.archive(actor, now.plusSeconds(5)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void equalityAtDeadlineNeverAdmitsLateBytesAndUnknownScanIsNotClean() {
    var late = asset();
    late.complete(
        ScanVerdict.CLEAN,
        new ScanProvenance("engine", "v1"),
        now.plusSeconds(119),
        now.plusSeconds(120));
    assertThat(late.getStatus()).isEqualTo(MediaAsset.State.REJECTED);
    assertThat(late.getVerificationFailure()).isEqualTo("DEADLINE_EXCEEDED");
    var unknown = asset();
    unknown.complete(null, null, null, now.plusSeconds(1));
    assertThat(unknown.getScanResult()).isNull();
    assertThat(unknown.getVerificationFailure()).isEqualTo("PHYSICAL_UNAVAILABLE");
    var early = asset();
    early.complete(
        ScanVerdict.CLEAN,
        new ScanProvenance("engine", "v1"),
        now.plusSeconds(118),
        now.plusSeconds(119));
    early.requirePreview();
  }

  @Test
  void mediaKeysNeverAcceptClientPathsAndEnforceBytesBound() {
    var key =
        new MediaObjectKey(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MediaObjectKey.hash(new byte[] {1}),
            MediaFormat.PNG);
    key.verify(new byte[] {1});
    assertThat(key.key()).startsWith("media/").doesNotContain("quarantine", ":");
    assertThatThrownBy(() -> key.verify(new byte[] {2}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> key.verify(new byte[MediaObjectKey.MAX_BYTES + 1]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MediaObjectKey(
                    UUID.randomUUID(), UUID.randomUUID(), "../../secret", MediaFormat.PNG))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
