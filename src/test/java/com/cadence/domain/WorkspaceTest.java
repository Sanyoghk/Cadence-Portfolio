package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WorkspaceTest {

  @Test
  void shouldCreateActiveWorkspaceAndNormalizeFields() {
    Workspace workspace = new Workspace(" Creator Studio ", " Content creation ");

    assertEquals("Creator Studio", workspace.getName());
    assertEquals("Content creation", workspace.getIndustry());
    assertEquals(WorkspaceStatus.ACTIVE, workspace.getStatus());
  }

  @Test
  void shouldArchiveOnlyOnce() {
    Workspace workspace = new Workspace("Creator Studio", "Content creation");

    workspace.archive();

    assertEquals(WorkspaceStatus.ARCHIVED, workspace.getStatus());
    assertThrows(IllegalStateException.class, workspace::archive);
  }

  @Test
  void shouldRejectBlankFields() {
    assertThrows(IllegalArgumentException.class, () -> new Workspace(" ", "Content"));
    assertThrows(IllegalArgumentException.class, () -> new Workspace("Studio", " "));
  }
}
