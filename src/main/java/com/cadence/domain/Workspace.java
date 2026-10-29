package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "workspaces")
public class Workspace extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String industry;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkspaceStatus status;

  protected Workspace() {}

  public Workspace(String name, String industry) {
    rename(name);
    changeIndustry(industry);
    status = WorkspaceStatus.ACTIVE;
  }

  public void archive() {
    if (status == WorkspaceStatus.ARCHIVED) {
      throw new IllegalStateException("Workspace is already archived");
    }
    status = WorkspaceStatus.ARCHIVED;
  }

  public void rename(String name) {
    this.name = DomainText.required(name, "Workspace name", 255);
  }

  public void changeIndustry(String industry) {
    this.industry = DomainText.required(industry, "Workspace industry", 255);
  }

  public String getName() {
    return name;
  }

  public String getIndustry() {
    return industry;
  }

  public WorkspaceStatus getStatus() {
    return status;
  }
}
