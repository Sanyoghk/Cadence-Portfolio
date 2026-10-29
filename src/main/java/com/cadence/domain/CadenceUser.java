package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "cadence_users")
public class CadenceUser extends BaseEntity {

  @Column(name = "external_subject", nullable = false, unique = true, length = 255)
  private String externalSubject;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private UserRole role;

  @Column(nullable = false)
  private boolean active = true;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "workspace_memberships",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "workspace_id"))
  private Set<Workspace> workspaces = new LinkedHashSet<>();

  protected CadenceUser() {}

  public CadenceUser(String externalSubject, UserRole role, Collection<Workspace> workspaces) {
    if (externalSubject == null || externalSubject.isBlank()) {
      throw new IllegalArgumentException("External subject must not be blank");
    }
    if (role == null) {
      throw new IllegalArgumentException("User role must not be null");
    }
    this.externalSubject = externalSubject.trim();
    this.role = role;
    this.workspaces.addAll(workspaces == null ? Set.of() : workspaces);
  }

  public String getExternalSubject() {
    return externalSubject;
  }

  public UserRole getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public Set<Workspace> getWorkspaces() {
    return Set.copyOf(workspaces);
  }

  public void grantAccess(Workspace workspace) {
    if (workspace == null) {
      throw new IllegalArgumentException("Workspace must not be null");
    }
    workspaces.add(workspace);
  }
}
