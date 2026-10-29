package com.cadence.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.presentation.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {
  @Autowired MockMvc mvc;
  @Autowired RequestMappingHandlerMapping mappings;

  @Test
  void contractIsPublishedButRequiresOwner() throws Exception {
    mvc.perform(get("/api/v1/openapi"))
        .andExpect(status().isUnauthorized())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/openapi")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
        .andExpect(status().isForbidden())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/openapi")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().json(OpenApiContractAssertions.DOCUMENT.toString(), true));
  }

  @Test
  void everyVersionedControllerOperationIsDocumentedAndNoFutureOperationsAreInvented() {
    var actual = new HashSet<String>();
    mappings
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              for (var path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/v1/")) continue;
                for (var method : mapping.getMethodsCondition().getMethods())
                  actual.add(method.name().toLowerCase(Locale.ROOT) + " " + path);
              }
            });
    var declared = new HashSet<String>();
    var ids = new HashSet<String>();
    OpenApiContractAssertions.DOCUMENT
        .path("paths")
        .fields()
        .forEachRemaining(
            path ->
                path.getValue()
                    .fields()
                    .forEachRemaining(
                        operation -> {
                          declared.add(operation.getKey() + " " + path.getKey());
                          assertThat(ids.add(operation.getValue().path("operationId").asText()))
                              .isTrue();
                        }));
    assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
    referencesResolve(OpenApiContractAssertions.DOCUMENT);
  }

  @Test
  void dtoFieldsAndEnumsStayAlignedWithTheReviewedContract() {
    for (var dto :
        List.of(
            CsvExportRequestDto.class,
            CreatorReportRequestDto.class,
            com.cadence.domain.CreatorReportRequest.class,
            com.cadence.application.results.CreatorReportDetails.class,
            com.cadence.application.results.CreatorReportSnapshot.class,
            com.cadence.application.results.CreatorReportSnapshot.Source.class,
            com.cadence.application.results.CreatorReportSnapshot.Observation.class,
            com.cadence.application.results.CreatorReportSnapshot.Group.class,
            com.cadence.application.results.CreatorReportSnapshot.Advice.class,
            RecommendationRequest.class,
            RecommendationResolutionRequest.class,
            com.cadence.application.results.RecommendationDetails.class,
            com.cadence.application.results.RecommendationSummary.class,
            com.cadence.application.results.RecommendationDetails.Resolution.class,
            com.cadence.application.ports.RecommendationEngine.Attribution.class,
            com.cadence.application.ports.RecommendationCommand.class,
            com.cadence.domain.RecommendationProposal.class,
            com.cadence.domain.PerformanceQuery.class,
            com.cadence.domain.StrategyContent.class,
            MetricAttributionRequest.class,
            com.cadence.application.results.MetricAttributionDetails.class,
            com.cadence.application.results.PerformanceReport.class,
            com.cadence.application.results.PerformanceReport.PerformanceGroup.class,
            com.cadence.application.results.PerformanceReport.PerformanceContribution.class,
            com.cadence.domain.PerformanceMetrics.PerformanceRate.class,
            com.cadence.application.results.MetricImportDetails.class,
            com.cadence.application.results.MetricObservationDetails.class,
            com.cadence.application.results.CsvExportDetails.class,
            CreateMediaRequest.class,
            ReviewMediaRequest.class,
            ReplacePostMediaRequest.class,
            com.cadence.application.results.MediaDetails.class,
            CalendarScheduleRequest.class,
            com.cadence.application.results.ScheduleDetails.class,
            WorkspaceResponse.class,
            BrandResponse.class,
            BrandEvidenceResponse.class,
            BrandSignatureResponse.class,
            ColorPaletteResponse.class,
            JobResponse.class,
            EvidenceRegistrationResponse.class,
            CreateWorkspaceRequest.class,
            CreateBrandRequest.class,
            UpdateWorkspaceRequest.class,
            UpdateBrandRequest.class,
            VersionRequest.class,
            RegisterEvidenceRequest.class,
            RegisterSocialEvidenceRequest.class,
            StrategyResponse.class,
            UpdateStrategyRequest.class,
            BrandIdentityResponse.class,
            CreateBrandIdentityRequest.class,
            UpdateBrandIdentityRequest.class,
            IdentityContentRequest.class,
            IdentityFromAnalysisRequest.class,
            AdmitEvidenceRequest.class,
            AdmitPdfEvidenceRequest.class,
            AdmitUrlEvidenceRequest.class,
            AdmitSocialEvidenceRequest.class,
            com.cadence.application.results.SocialReviewPreview.class,
            com.cadence.application.results.SocialAdmissionDetails.class,
            com.cadence.application.results.SocialAdmissionSnapshot.class,
            com.cadence.application.results.UrlReviewPreview.class,
            com.cadence.application.results.UrlAdmissionDetails.class,
            com.cadence.application.results.UrlAdmissionSnapshot.class,
            com.cadence.domain.WebsiteMetadata.class,
            com.cadence.application.results.PdfAdmissionDetails.class,
            com.cadence.application.results.PdfAdmissionSnapshot.class,
            EvidenceAnalysisResponse.class,
            com.cadence.application.results.PdfReviewPreview.class,
            EvidenceIdentityFromAnalysisRequest.class,
            com.cadence.application.results.IdentityEvidenceProvenance.class,
            com.cadence.application.results.IdentityEvidenceProvenance.Citation.class,
            com.cadence.application.ports.AnalysisCitation.class,
            com.cadence.application.results.EvidenceReview.class,
            com.cadence.application.results.EvidenceAdmissionDetails.class,
            com.cadence.application.results.EvidenceAdmissionSnapshot.class,
            com.cadence.application.results.EvidenceWithdrawalDetails.class,
            com.cadence.application.results.IdentityAnalysisProvenance.class)) {
      var properties =
          OpenApiContractAssertions.DOCUMENT.at(
              "/components/schemas/" + dto.getSimpleName() + "/properties");
      var names = new HashSet<String>();
      properties.fieldNames().forEachRemaining(names::add);
      assertThat(names)
          .containsExactlyInAnyOrderElementsOf(
              Arrays.stream(dto.getRecordComponents())
                  .map(java.lang.reflect.RecordComponent::getName)
                  .toList());
      for (var field : dto.getRecordComponents()) {
        var schema = properties.path(field.getName());
        if (schema.has("anyOf")) schema = schema.path("anyOf").get(0);
        if (field.getType().isEnum()) {
          var values = new ArrayList<String>();
          schema.path("enum").forEach(value -> values.add(value.asText()));
          assertThat(values)
              .containsExactlyElementsOf(
                  Arrays.stream(field.getType().getEnumConstants()).map(Object::toString).toList());
        }
        if (field.getType().equals(java.time.Instant.class))
          assertThat(schema.path("format").asText()).isEqualTo("date-time");
      }
    }
  }

  private void referencesResolve(JsonNode node) {
    if (node.has("$ref"))
      assertThat(
              OpenApiContractAssertions.DOCUMENT
                  .at(node.path("$ref").asText().substring(1))
                  .isMissingNode())
          .isFalse();
    node.elements().forEachRemaining(this::referencesResolve);
  }
}
