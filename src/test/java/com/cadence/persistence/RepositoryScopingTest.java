package com.cadence.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.repositories.PostMetricRepository;
import com.cadence.application.repositories.RecommendationRepository;
import com.cadence.application.repositories.SocialPostRepository;
import com.cadence.application.repositories.StrategyRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.Brand;
import com.cadence.domain.ContentPillar;
import com.cadence.domain.PostFormat;
import com.cadence.domain.PostMetric;
import com.cadence.domain.PublishingCadence;
import com.cadence.domain.Recommendation;
import com.cadence.domain.SocialPost;
import com.cadence.domain.Strategy;
import com.cadence.domain.Workspace;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryScopingTest {

  @Autowired private WorkspaceRepository workspaces;
  @Autowired private BrandRepository brands;
  @Autowired private StrategyRepository strategies;
  @Autowired private SocialPostRepository posts;
  @Autowired private PostMetricRepository metrics;
  @Autowired private RecommendationRepository recommendations;

  private Aggregate first;
  private Aggregate second;

  @BeforeEach
  void setUp() {
    first = persistAggregate("Alpha Studio", "Alpha Brand");
    second = persistAggregate("Beta Studio", "Beta Brand");
  }

  @Test
  void shouldScopeEveryAggregateToItsOwningWorkspace() {
    PageRequest page = PageRequest.of(0, 10, Sort.by("createdAt").ascending());

    assertEquals(
        List.of(first.brand()),
        brands.findAllByWorkspaceId(first.workspace().getId(), page).getContent());
    assertTrue(
        brands
            .findByIdAndWorkspaceId(first.brand().getId(), first.workspace().getId())
            .isPresent());
    assertFalse(
        brands
            .findByIdAndWorkspaceId(first.brand().getId(), second.workspace().getId())
            .isPresent());
    assertEquals(
        1,
        strategies.findAllByBrandWorkspaceId(first.workspace().getId(), page).getTotalElements());
    assertFalse(
        strategies
            .findByIdAndBrandWorkspaceId(first.strategy().getId(), second.workspace().getId())
            .isPresent());
    assertEquals(
        1,
        posts
            .findAllByStrategyBrandWorkspaceId(first.workspace().getId(), page)
            .getTotalElements());
    assertEquals(
        1,
        metrics
            .findAllByPostStrategyBrandWorkspaceId(first.workspace().getId(), page)
            .getTotalElements());
    assertEquals(
        1,
        recommendations
            .findAllByStrategyBrandWorkspaceId(first.workspace().getId(), page)
            .getTotalElements());
  }

  @Test
  void shouldPageAndSortBrandsWithinAWorkspace() {
    brands.save(new Brand("Zulu Brand", first.workspace()));
    var result =
        brands.findAllByWorkspaceId(
            first.workspace().getId(), PageRequest.of(0, 1, Sort.by("name").ascending()));

    assertEquals(2, result.getTotalElements());
    assertEquals("Alpha Brand", result.getContent().getFirst().getName());
  }

  private Aggregate persistAggregate(String workspaceName, String brandName) {
    Workspace workspace = workspaces.save(new Workspace(workspaceName, "Creator"));
    Brand brand = brands.save(new Brand(brandName, workspace));
    Strategy strategy =
        strategies.save(
            new Strategy(
                brand,
                1,
                new PublishingCadence(3, "UTC", List.of(LocalTime.NOON)),
                List.of(new ContentPillar("Education", 100, "Explain the product"))));
    SocialPost post =
        posts.save(
            new SocialPost(
                strategy, "Launch", "Launch caption", List.of("cadence"), PostFormat.IMAGE));
    PostMetric metric = metrics.save(new PostMetric(post, 100, 10, 2, 1, 3));
    Recommendation recommendation =
        recommendations.save(new Recommendation(strategy, "Publish weekly", 0.8, "+10% reach"));
    return new Aggregate(workspace, brand, strategy, post, metric, recommendation);
  }

  private record Aggregate(
      Workspace workspace,
      Brand brand,
      Strategy strategy,
      SocialPost post,
      PostMetric metric,
      Recommendation recommendation) {}
}
