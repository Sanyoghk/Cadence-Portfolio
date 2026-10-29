package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.repositories.StrategyRepository;
import com.cadence.application.results.StrategyDetails;
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
public class ReadStrategyUseCase {
  private final StrategyRepository strategies;
  private final BrandRepository brands;

  public ReadStrategyUseCase(StrategyRepository strategies, BrandRepository brands) {
    this.strategies = strategies;
    this.brands = brands;
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public StrategyDetails findById(UUID strategyId, Set<UUID> scope) {
    return StrategyDetails.from(
        strategies
            .findByIdAndBrandWorkspaceIdIn(strategyId, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Strategy not found")));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public Page<StrategyDetails> findAll(UUID brandId, Set<UUID> scope, Pageable pageable) {
    if (pageable == null
        || pageable.isUnpaged()
        || pageable.getPageSize() > 100
        || pageable.getPageNumber() > 10_000
        || pageable.getSort().stream()
            .anyMatch(
                order ->
                    !Set.of("version", "createdAt", "updatedAt", "id")
                        .contains(order.getProperty())))
      throw new IllegalArgumentException("Invalid strategy pagination");
    brands
        .findByIdAndWorkspaceIdIn(brandId, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    return strategies.findAllByBrandId(brandId, pageable).map(StrategyDetails::from);
  }
}
