package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import com.cadence.infrastructure.recommendations.RuleRecommendationEngine;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

public abstract class RecommendationApiContract extends PerformanceApiContract {
  @Autowired protected GenerateRecommendationsUseCase recommendations;
  @Autowired protected RecommendationKeyRepository recommendationKeys;
  @Autowired protected RecommendationSources recommendationSources;
  @Autowired protected BrandIdentityUseCase recommendationIdentities;
  @Autowired protected BuildStrategyUseCase recommendationStrategyBuild;
  @Autowired protected IdentityEvidenceUseCase recommendationIdentityCopies;
  @Autowired protected AnalyzeEvidenceUseCase recommendationAnalysis;
  @Autowired protected ReviewEvidenceUseCase recommendationReviews;
  @Autowired protected BrandEvidenceRepository recommendationEvidence;
  @MockitoSpyBean protected BrandAnalysisPort recommendationAnalysisPort;
  @Autowired protected ReadStrategyUseCase strategyReads;
  @Autowired protected RecommendationInsightRepository recommendationRows;
  @Autowired protected RecommendationResolutionRepository resolutionRows;
  @MockitoSpyBean protected RuleRecommendationEngine recommendationEngine;

  protected List<UUID> seedRecommendation() {
    return seedRecommendation(5);
  }

  protected List<UUID> seedRecommendation(int perPillar) {
    return seedRecommendation(perPillar, 100);
  }

  protected List<UUID> seedRecommendation(int perPillar, int reach) {
    var ids = new ArrayList<UUID>();
    for (int g = 0; g < 2; g++)
      for (int i = 0; i < perPillar; i++) {
        var p = approved();
        var o = observation(p, reach, g == 0 ? reach / 10 : reach * 3 / 10, 1, 1, 5);
        performance.attribute(
            o, scope(), p.revisionId(), p.lockVersion(), g == 0 ? "education" : "community");
        ids.add(o);
      }
    return ids;
  }

  protected RecommendationSources.Request recommendationRequest() {
    var q = query(PerformanceQuery.Dimension.PILLAR);
    return new RecommendationSources.Request(
        source.getId(),
        source.getLockVersion(),
        q.fromDate(),
        q.untilDate(),
        "UTC",
        PlaybookContent.Channel.INSTAGRAM);
  }

  protected RecommendationDetails generate(String key) {
    return recommendations.generate(brand.getId(), scope(), key, recommendationRequest()).details();
  }

  protected RecommendationDetails apply(UUID id, String key) {
    return recommendations
        .resolve(id, scope(), key, 0, RecommendationResolution.Action.APPLY)
        .details();
  }

  @Test
  void metricsToRecommendationToEditableDraftPreservesApprovedHistory() {
    var evidence = seedRecommendation();
    var r = generate("flow");
    assertThat(r.capture().sources()).hasSize(10);
    assertThat(r.capture().expected().citations()).containsExactlyInAnyOrderElementsOf(evidence);
    assertThat(r.attribution().origin()).isEqualTo("HEURISTIC");
    assertThat(r.attribution().modelVersion()).isNull();
    assertThat(r.calibratedProbability()).isNull();
    assertThat(r.expectedLift()).isNull();
    assertThat(r.confidence()).isEqualTo("UNCALIBRATED");
    assertThat(r.claimType()).isEqualTo("HYPOTHESIS");
    var result = apply(r.id(), "apply-flow");
    var draft = strategies.findById(result.resolution().draftId()).orElseThrow();
    var details =
        new com.cadence.application.services.RecommendationSources.Request(
            source.getId(),
            source.getLockVersion(),
            recommendationRequest().fromDate(),
            recommendationRequest().untilDate(),
            "UTC",
            PlaybookContent.Channel.INSTAGRAM);
    assertThat(draft.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
    assertThat(draft.getRevisedFromId()).isEqualTo(source.getId());
    assertThat(draft.getEditedBy()).isEqualTo(actorId);
    assertThat(draft.getGeneration()).isNull();
    var approved = strategyReads.findById(source.getId(), scope());
    assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
    assertThat(approved.contentPillars().stream().map(StrategyDetails.Pillar::weight))
        .containsExactly(60, 40);
    assertThat(
            strategyReads.findById(result.resolution().draftId(), scope()).contentPillars().stream()
                .map(StrategyDetails.Pillar::weight))
        .containsExactly(55, 45);
    mvcReadDraftAndEdit(result);
    assertThat(apply(r.id(), "apply-flow").resolution()).isEqualTo(result.resolution());
  }

  @Test
  void zeroAndDenominatorThresholdEnforcedBeforeInference() {
    seedRecommendation(5, 0);
    assertThatThrownBy(() -> generate("zero-denominator"))
        .isInstanceOf(IllegalStateException.class);
    verify(recommendationEngine, never()).recommend(any());
  }

  @Test
  void modelInputContainsNoTenantActorOrRawProvenance() {
    seedRecommendation(5, 20);
    doAnswer(
            i -> {
              RecommendationInput input = i.getArgument(0);
              String encoded = mapper.writeValueAsString(input);
              assertThat(encoded)
                  .doesNotContain(
                      actorId.toString(),
                      brand.getId().toString(),
                      workspace.getId().toString(),
                      "rawChecksum",
                      "ingestedBy",
                      "approvedBy",
                      "identitySource",
                      "reference",
                      "snapshot");
              assertThat(input.metrics()).hasSize(10);
              return input.expected();
            })
        .when(recommendationEngine)
        .recommend(any());
    assertThat(generate("minimal-input").capture().sources()).hasSize(10);
  }

  @Test
  void failedKeyCannotBeReboundAndSamePureRuleRequestCanRecover() {
    var q = recommendationRequest();
    assertThatThrownBy(() -> generate("failed-binding")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                recommendations.generate(
                    brand.getId(),
                    scope(),
                    "failed-binding",
                    new RecommendationSources.Request(
                        q.strategyId(),
                        q.expectedStrategyVersion(),
                        q.fromDate().minusDays(1),
                        q.untilDate(),
                        q.timezone(),
                        q.platform())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Idempotency");
    seedRecommendation();
    var recovered = generate("failed-binding");
    assertThat(generate("failed-binding").operationId()).isEqualTo(recovered.operationId());
    var another = brands.saveAndFlush(new Brand("Another", workspace));
    assertThatThrownBy(
            () -> recommendations.generate(another.getId(), scope(), "failed-binding", q))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Idempotency");
  }

  @Test
  void staleMetricsDenyGenerationAndApplyWithoutRewritingHistory() {
    seedRecommendation();
    var r = generate("stale-base");
    org.springframework.test.util.ReflectionTestUtils.setField(
        recommendationSources, "clock", Clock.offset(clock, Duration.ofDays(8)));
    try {
      assertThatThrownBy(() -> generate("stale-new")).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> apply(r.id(), "stale-apply"))
          .isInstanceOf(IllegalStateException.class);
      assertThat(recommendations.read(r.id(), scope()).snapshotChecksum())
          .isEqualTo(r.snapshotChecksum());
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          recommendationSources, "clock", clock);
    }
  }

  @Test
  void missingUnknownAttributionAndUnsupportedWindowsCannotCreateAdvice() {
    for (int i = 0; i < 10; i++) {
      var p = approved();
      observation(p, 100, 20, 1, 1, 5);
    }
    assertThatThrownBy(() -> generate("unknown-attribution"))
        .isInstanceOf(IllegalStateException.class);
    var q = recommendationRequest();
    assertThatThrownBy(
            () ->
                recommendations.generate(
                    brand.getId(),
                    scope(),
                    "window-mismatch",
                    new RecommendationSources.Request(
                        q.strategyId(),
                        q.expectedStrategyVersion(),
                        q.fromDate().minusDays(1),
                        q.untilDate(),
                        q.timezone(),
                        q.platform())))
        .isInstanceOf(IllegalStateException.class);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void generatedIdentitySourceHasPositiveApplyAndNewIdentityBlocksIt(boolean applyNow) {
    var identity =
        recommendationIdentities.create(brand.getId(), scope(), StrategyFixtures.identityContent());
    recommendationIdentities.approve(identity.id(), scope(), 0, actorId);
    var built = recommendationStrategyBuild.execute(brand.getId(), scope());
    strategyLifecycle.approve(built.id(), scope(), 0, actorId);
    source = strategies.findById(built.id()).orElseThrow();
    seedRecommendation();
    var r = generate("identity-source");
    assertThat(r.capture().identitySource().identityId()).isEqualTo(identity.id());
    if (applyNow) assertThat(apply(r.id(), "identity-apply").resolution().draftId()).isNotNull();
    else {
      recommendationIdentities.create(brand.getId(), scope(), StrategyFixtures.identityContent());
      assertThatThrownBy(() -> apply(r.id(), "identity-apply"))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void inheritedTextWithdrawalBlocksRecommendationGenerationAndApply() {
    String text = "Reviewed recommendation evidence";
    var evidence =
        recommendationEvidence.saveAndFlush(
            new BrandEvidence(
                brand,
                EvidenceType.TEXT,
                text,
                EvidenceAdmission.checksum("TEXT\n" + text),
                clock.instant()));
    var admission =
        recommendationReviews.admit(
            evidence.getId(), scope(), actorId, 0, 0, 0, EvidenceAdmission.checksum(text));
    doAnswer(
            i -> {
              BrandAnalysisCommand c = i.getArgument(0);
              return new BrandAnalysisResult(
                  new BrandAnalysisResult.Palette(
                      "#000000", "#111111", "#222222", "#333333", "#FFFFFF"),
                  List.of("Inter"),
                  List.of("Clear"),
                  List.of("Vague"),
                  "Demo",
                  List.of(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED),
                  "local-test",
                  "v1",
                  "text-v1",
                  List.of(new AnalysisCitation(c.sources().getFirst().admissionId(), "Reviewed")));
            })
        .when(recommendationAnalysisPort)
        .analyze(any());
    var receipt = recommendationAnalysis.execute(brand.getId(), scope(), actorId);
    var identity =
        recommendationIdentityCopies.createFromAnalysis(
            brand.getId(), scope(), receipt.analysisId(), 1, actorId);
    recommendationIdentities.approve(identity.id(), scope(), 0, actorId);
    var built = recommendationStrategyBuild.execute(brand.getId(), scope());
    strategyLifecycle.approve(built.id(), scope(), 0, actorId);
    source = strategies.findById(built.id()).orElseThrow();
    seedRecommendation();
    var r = generate("text-source");
    recommendationReviews.withdraw(evidence.getId(), admission.id(), scope(), actorId);
    assertThatThrownBy(() -> generate("text-withdrawn")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> apply(r.id(), "text-apply")).isInstanceOf(IllegalStateException.class);
    assertThat(recommendations.read(r.id(), scope()).snapshotChecksum())
        .isEqualTo(r.snapshotChecksum());
  }

  private void mvcReadDraftAndEdit(RecommendationDetails r) {
    var id = r.resolution().draftId();
    var draft = strategies.findById(id).orElseThrow();
    var p = r.capture().expected().apply(r.capture().content(), r.capture().playbook());
    strategyLifecycle.updatePlaybook(id, scope(), 0, p, r.capture().playbook());
    strategyLifecycle.approve(
        id, scope(), strategies.findById(id).orElseThrow().getLockVersion(), actorId);
  }

  @Test
  void replayAndPayloadCollisionAreCoherent() {
    seedRecommendation();
    var r = generate("same-key");
    assertThat(generate("same-key").id()).isEqualTo(r.id());
    var q = recommendationRequest();
    assertThatThrownBy(
            () ->
                recommendations.generate(
                    brand.getId(),
                    scope(),
                    "same-key",
                    new RecommendationSources.Request(
                        q.strategyId(),
                        q.expectedStrategyVersion(),
                        q.fromDate().minusDays(1),
                        q.untilDate(),
                        q.timezone(),
                        q.platform())))
        .isInstanceOf(IllegalStateException.class);
    var applied = apply(r.id(), "same-apply");
    assertThat(apply(r.id(), "same-apply").resolution()).isEqualTo(applied.resolution());
    assertThatThrownBy(() -> apply(r.id(), "another-key"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void insufficientAndZeroDataDenied() {
    assertThatThrownBy(() -> generate("empty")).isInstanceOf(IllegalStateException.class);
    seedRecommendation(4);
    assertThatThrownBy(() -> generate("sparse")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void wrongVersionForeignAndLockedStrategyDenied() {
    seedRecommendation();
    var q = recommendationRequest();
    assertThatThrownBy(
            () ->
                recommendations.generate(
                    brand.getId(),
                    scope(),
                    "version",
                    new RecommendationSources.Request(
                        source.getId(),
                        999,
                        q.fromDate(),
                        q.untilDate(),
                        q.timezone(),
                        q.platform())))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                recommendations.generate(
                    brand.getId(),
                    scope(),
                    "foreign",
                    new RecommendationSources.Request(
                        UUID.randomUUID(),
                        1,
                        q.fromDate(),
                        q.untilDate(),
                        q.timezone(),
                        q.platform())))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    var s = strategies.findById(source.getId()).orElseThrow();
    s.lock();
    strategies.saveAndFlush(s);
    assertThatThrownBy(() -> generate("locked")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void forgedCitationIdsRejectedWithoutPublishing() {
    seedRecommendation();
    long before = recommendationRows.count();
    doAnswer(
            inv -> {
              var c = (RecommendationInput) inv.getArgument(0);
              var ids = new ArrayList<>(c.expected().citations());
              ids.set(0, UUID.randomUUID());
              return new RecommendationProposal(
                  c.expected().fromPillar(), c.expected().toPillar(), 5, ids);
            })
        .when(recommendationEngine)
        .recommend(any());
    assertThatThrownBy(() -> generate("forged")).isInstanceOf(IllegalStateException.class);
    assertThat(recommendationRows.count()).isEqualTo(before);
  }

  @Test
  void fakeLiveAttributionCannotMasqueradeAsRule() {
    seedRecommendation();
    doReturn(new RecommendationEngine.Attribution("AI", "v1", "pretend-live", "model", "prompt"))
        .when(recommendationEngine)
        .attribution();
    assertThatThrownBy(() -> generate("fake-origin"))
        .isInstanceOf(com.cadence.application.exceptions.RecommendationUnavailableException.class);
  }

  @Test
  void sourceLockDuringRuleCallRejectsPublicationOutsideTransaction() {
    seedRecommendation();
    long before = recommendationRows.count();
    doAnswer(
            inv -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              var s = strategies.findById(source.getId()).orElseThrow();
              s.lock();
              strategies.saveAndFlush(s);
              return ((RecommendationInput) inv.getArgument(0)).expected();
            })
        .when(recommendationEngine)
        .recommend(any());
    assertThatThrownBy(() -> generate("race-lock")).isInstanceOf(IllegalStateException.class);
    assertThat(recommendationRows.count()).isEqualTo(before);
  }

  @Test
  void providerFailureAndTimeoutPreservePriorHistory() {
    seedRecommendation();
    var old = generate("old");
    doThrow(new IllegalStateException("private provider error"))
        .when(recommendationEngine)
        .recommend(any());
    assertThatThrownBy(() -> generate("failure"))
        .isInstanceOf(com.cadence.application.exceptions.RecommendationUnavailableException.class);
    assertThat(recommendations.read(old.id(), scope()).snapshotChecksum())
        .isEqualTo(old.snapshotChecksum());
    doAnswer(
            inv -> {
              Thread.sleep(4000);
              return ((RecommendationInput) inv.getArgument(0)).expected();
            })
        .when(recommendationEngine)
        .recommend(any());
    assertThatThrownBy(() -> generate("timeout"))
        .isInstanceOf(com.cadence.application.exceptions.RecommendationUnavailableException.class);
  }

  @Test
  void correctedMetricDeniesApplyAndPreservesOriginalSnapshot() {
    var ids = seedRecommendation();
    var r = generate("correction");
    var c = r.capture().sources().getFirst();
    var q = recommendationRequest().query();
    observation(
        new MetricRow(
            c.postId(),
            r.capture().query().platform(),
            q.from(),
            q.until(),
            c.observedAt(),
            100L,
            99L,
            1L,
            1L,
            5L,
            c.observationId()));
    assertThatThrownBy(() -> apply(r.id(), "stale-apply"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(recommendations.read(r.id(), scope()).snapshotChecksum())
        .isEqualTo(r.snapshotChecksum());
  }

  @Test
  void reclassifiedMetricDeniesApply() {
    seedRecommendation();
    var r = generate("context");
    var c = r.capture().sources().getFirst();
    performance.reviseAttribution(
        c.observationId(),
        scope(),
        c.postRevisionId(),
        c.postRevisionVersion(),
        c.pillarId().equals("education") ? "community" : "education",
        c.attributionNumber());
    assertThatThrownBy(() -> apply(r.id(), "context-apply"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void newerStrategyBranchDeniesApply() {
    seedRecommendation();
    var r = generate("branch");
    strategyLifecycle.updatePlaybook(
        source.getId(),
        scope(),
        source.getLockVersion(),
        r.capture().content(),
        r.capture().playbook());
    assertThatThrownBy(() -> apply(r.id(), "branch-apply"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void dismissNeverCreatesOrChangesStrategy() {
    seedRecommendation();
    var r = generate("dismiss");
    long count = strategies.count();
    var result =
        recommendations.resolve(
            r.id(), scope(), "dismiss-key", 0, RecommendationResolution.Action.DISMISS);
    assertThat(result.details().status()).isEqualTo("DISMISSED");
    assertThat(strategies.count()).isEqualTo(count);
    assertThatThrownBy(() -> apply(r.id(), "after-dismiss"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            recommendations
                .resolve(r.id(), scope(), "dismiss-key", 0, RecommendationResolution.Action.DISMISS)
                .created())
        .isFalse();
  }

  @Test
  void httpScopeStrictInputPaginationAndProvenance() throws Exception {
    seedRecommendation();
    var q = recommendationRequest();
    String path = "/api/v1/brands/" + brand.getId() + "/recommendations";
    var body =
        new LinkedHashMap<String, Object>(
            Map.of(
                "strategyId",
                q.strategyId(),
                "expectedStrategyVersion",
                q.expectedStrategyVersion(),
                "fromDate",
                q.fromDate(),
                "untilDate",
                q.untilDate(),
                "timezone",
                q.timezone(),
                "platform",
                q.platform()));
    var created =
        mvc.perform(
                post(path)
                    .with(authentication(owner))
                    .header("Idempotency-Key", "http")
                    .contentType("application/json")
                    .content(json(body)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.attribution.origin").value("HEURISTIC"))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andReturn();
    var id =
        UUID.fromString(
            mapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
    mvc.perform(get(path).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    mvc.perform(get(path).with(authentication(owner)).param("size", "51"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/recommendations/{id}", id).with(anonymous()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/recommendations/{id}", id)
                .with(authentication(auth(actorId, UserRole.VIEWER, scope()))))
        .andExpect(status().isForbidden());
    var ws = workspaces.saveAndFlush(new Workspace("Foreign", "Creator"));
    var u =
        users.saveAndFlush(
            new CadenceUser("reco-other-" + UUID.randomUUID(), UserRole.OWNER, Set.of(ws)));
    var foreign = auth(u.getId(), UserRole.OWNER, Set.of(ws.getId()));
    mvc.perform(get("/api/v1/recommendations/{id}", id).with(authentication(foreign)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/recommendations/{id}/apply", id)
                .with(authentication(foreign))
                .header("Idempotency-Key", "foreign")
                .contentType("application/json")
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isNotFound());
    body.put("actorId", u.getId());
    mvc.perform(
            post(path)
                .with(authentication(owner))
                .header("Idempotency-Key", "forged-http")
                .contentType("application/json")
                .content(json(body)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/recommendations/{id}/apply", id)
                .with(authentication(owner))
                .header("Idempotency-Key", "http-apply")
                .contentType("application/json")
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("APPLIED"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }
}
