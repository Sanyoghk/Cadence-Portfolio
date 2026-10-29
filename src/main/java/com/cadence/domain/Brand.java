package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @Column(length = 2048)
  private String websiteUrl;

  private String instagramHandle;

  private String tiktokHandle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BrandStatus status = BrandStatus.ACTIVE;

  @Embedded private BrandSignature signature;

  @ManyToOne
  @JoinColumn(name = "workspace_id", nullable = false)
  private Workspace workspace;

  public Brand() {}

  public Brand(String name, Workspace workspace) {
    rename(name);
    assignTo(workspace);
    status = BrandStatus.ACTIVE;
  }

  public void updateSignature(BrandSignature signature) {
    this.signature = signature;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    rename(name);
  }

  public void rename(String name) {
    this.name = DomainText.required(name, "Brand name", 255);
  }

  public String getWebsiteUrl() {
    return websiteUrl;
  }

  public void setWebsiteUrl(String websiteUrl) {
    this.websiteUrl = DomainText.optional(websiteUrl, "Brand website URL", 2048);
  }

  public String getInstagramHandle() {
    return instagramHandle;
  }

  public void setInstagramHandle(String instagramHandle) {
    this.instagramHandle = DomainText.optional(instagramHandle, "Instagram handle", 255);
  }

  public String getTiktokHandle() {
    return tiktokHandle;
  }

  public void setTiktokHandle(String tiktokHandle) {
    this.tiktokHandle = DomainText.optional(tiktokHandle, "TikTok handle", 255);
  }

  public BrandSignature getSignature() {
    return signature;
  }

  public BrandStatus getStatus() {
    return status;
  }

  public void archive() {
    if (status == BrandStatus.ARCHIVED) {
      throw new IllegalStateException("Brand is already archived");
    }
    status = BrandStatus.ARCHIVED;
  }

  public Workspace getWorkspace() {
    return workspace;
  }

  public void setWorkspace(Workspace workspace) {
    assignTo(workspace);
  }

  private void assignTo(Workspace workspace) {
    if (workspace == null) {
      throw new IllegalArgumentException("Brand workspace must not be null");
    }
    if (this.workspace != null && this.workspace != workspace) {
      throw new IllegalStateException("A brand cannot be moved to another workspace");
    }
    this.workspace = workspace;
  }
}
