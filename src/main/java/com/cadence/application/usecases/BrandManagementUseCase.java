package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.Brand;
import com.cadence.domain.BrandStatus;
import com.cadence.domain.Workspace;
import com.cadence.domain.WorkspaceStatus;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("denyAll()")
@Transactional(readOnly = true)
public class BrandManagementUseCase {

  private final BrandRepository brandRepository;
  private final WorkspaceRepository workspaceRepository;

  public BrandManagementUseCase(
      BrandRepository brandRepository, WorkspaceRepository workspaceRepository) {
    this.brandRepository = brandRepository;
    this.workspaceRepository = workspaceRepository;
  }

  @Transactional
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Brand create(
      UUID workspaceId,
      Set<UUID> permittedWorkspaceIds,
      String name,
      String websiteUrl,
      String instagramHandle,
      String tiktokHandle) {
    Workspace workspace =
        workspaceRepository
            .findLockedByIdAndIdIn(workspaceId, permittedWorkspaceIds)
            .orElseThrow(
                () -> new ResourceNotFoundException("Workspace not found: " + workspaceId));
    if (workspace.getStatus() == WorkspaceStatus.ARCHIVED) {
      throw new IllegalStateException("Cannot create a brand in an archived workspace");
    }

    Brand brand = new Brand(name.trim(), workspace);
    brand.setWebsiteUrl(normalize(websiteUrl));
    brand.setInstagramHandle(normalize(instagramHandle));
    brand.setTiktokHandle(normalize(tiktokHandle));
    return brandRepository.save(brand);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Brand findById(UUID brandId, Set<UUID> permittedWorkspaceIds) {
    return brandRepository
        .findByIdAndWorkspaceIdIn(brandId, permittedWorkspaceIds)
        .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + brandId));
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Page<Brand> findAll(UUID workspaceId, Set<UUID> permittedWorkspaceIds, Pageable pageable) {
    workspaceRepository
        .findByIdAndIdIn(workspaceId, permittedWorkspaceIds)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + workspaceId));
    return brandRepository.findAllByWorkspaceId(workspaceId, pageable);
  }

  @Transactional
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Brand update(
      UUID brandId,
      Set<UUID> permittedWorkspaceIds,
      long expectedVersion,
      String name,
      String websiteUrl,
      String instagramHandle,
      String tiktokHandle) {
    Brand brand = findLocked(brandId, permittedWorkspaceIds);
    requireVersion(brand, expectedVersion);
    if (brand.getStatus() == BrandStatus.ARCHIVED
        || brand.getWorkspace().getStatus() == WorkspaceStatus.ARCHIVED) {
      throw new IllegalStateException(
          "Cannot update a brand in an archived brand or workspace state");
    }
    brand.rename(name);
    brand.setWebsiteUrl(normalize(websiteUrl));
    brand.setInstagramHandle(normalize(instagramHandle));
    brand.setTiktokHandle(normalize(tiktokHandle));
    return brandRepository.saveAndFlush(brand);
  }

  @Transactional
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Brand archive(UUID brandId, Set<UUID> permittedWorkspaceIds, long expectedVersion) {
    Brand brand = findLocked(brandId, permittedWorkspaceIds);
    requireVersion(brand, expectedVersion);
    brand.archive();
    return brandRepository.saveAndFlush(brand);
  }

  private Brand findLocked(UUID brandId, Set<UUID> scope) {
    UUID workspaceId =
        brandRepository
            .findWorkspaceIdIn(brandId, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    workspaceRepository
        .findLockedByIdAndIdIn(workspaceId, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    return brandRepository
        .findLockedWithWorkspaceIn(brandId, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
  }

  private void requireVersion(Brand brand, long expectedVersion) {
    if (brand.getLockVersion() != expectedVersion) {
      throw new IllegalStateException(
          "Brand was modified; expected version "
              + expectedVersion
              + " but was "
              + brand.getLockVersion());
    }
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
