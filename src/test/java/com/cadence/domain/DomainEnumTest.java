package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class DomainEnumTest {

  @Test
  void shouldExposeAllApprovalStatuses() {
    assertArrayEquals(
        new ApprovalStatus[] {ApprovalStatus.DRAFT, ApprovalStatus.APPROVED, ApprovalStatus.LOCKED},
        ApprovalStatus.values());
  }

  @Test
  void shouldExposeAllPostStatuses() {
    assertArrayEquals(
        new PostStatus[] {
          PostStatus.DRAFT,
          PostStatus.APPROVED,
          PostStatus.SCHEDULED,
          PostStatus.PUBLISHED,
          PostStatus.FAILED
        },
        PostStatus.values());
  }

  @Test
  void shouldExposeAllPlatforms() {
    assertArrayEquals(new Platform[] {Platform.INSTAGRAM, Platform.TIKTOK}, Platform.values());
  }

  @Test
  void shouldExposeAllPostFormats() {
    assertArrayEquals(
        new PostFormat[] {
          PostFormat.IMAGE,
          PostFormat.CAROUSEL,
          PostFormat.REEL,
          PostFormat.VIDEO,
          PostFormat.STORY,
          PostFormat.TEXT
        },
        PostFormat.values());
  }

  @Test
  void shouldExposeAllWorkspaceStatuses() {
    assertArrayEquals(
        new WorkspaceStatus[] {WorkspaceStatus.ACTIVE, WorkspaceStatus.ARCHIVED},
        WorkspaceStatus.values());
  }
}
