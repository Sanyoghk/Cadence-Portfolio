package com.cadence.presentation.dto;

import com.cadence.domain.Brand;
import com.cadence.domain.BrandStatus;
import java.time.Instant;
import java.util.UUID;

public record BrandResponse(
    UUID id,
    UUID workspaceId,
    String name,
    String websiteUrl,
    String instagramHandle,
    String tiktokHandle,
    BrandStatus status,
    BrandSignatureResponse signature,
    long lockVersion,
    Instant createdAt,
    Instant updatedAt) {
  public static BrandResponse from(Brand brand) {
    return new BrandResponse(
        brand.getId(),
        brand.getWorkspace().getId(),
        brand.getName(),
        brand.getWebsiteUrl(),
        brand.getInstagramHandle(),
        brand.getTiktokHandle(),
        brand.getStatus(),
        BrandSignatureResponse.from(brand.getSignature()),
        brand.getLockVersion(),
        brand.getCreatedAt(),
        brand.getUpdatedAt());
  }
}
