package com.cadence.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "cadence.brand-analysis.adapter=disabled")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrandAnalysisFailClosedTest {
  @Autowired MockMvc mvc;
  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired CadenceUserRepository users;
  @Autowired BrandAnalysisAttemptRepository attempts;
  @MockitoBean JwtDecoder decoder;

  @Test
  void disabledAdapterReturnsDocumented503WithoutOverwritingSignature() throws Exception {
    var workspace = workspaces.saveAndFlush(new Workspace("Disabled studio", "Creator"));
    var brand = new Brand("Brand", workspace);
    brand.updateSignature(
        new BrandSignature(
            new ColorPalette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
            List.of("Inter"),
            List.of("clear"),
            List.of("spam"),
            "Previous"));
    brand = brands.saveAndFlush(brand);
    String subject = "disabled-owner-" + UUID.randomUUID();
    users.saveAndFlush(new CadenceUser(subject, UserRole.OWNER, List.of(workspace)));
    when(decoder.decode("owner"))
        .thenReturn(
            new Jwt(
                "owner",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                    "sub",
                    subject,
                    "cadence_role",
                    "OWNER",
                    "cadence_workspace_ids",
                    List.of(workspace.getId().toString()))));
    mvc.perform(
            post("/api/v1/cadence/brands/{brandId}/analyze", brand.getId())
                .header("Authorization", "Bearer owner"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("brand_analysis_unavailable"))
        .andExpect(jsonPath("$.retryable").value(true))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    assertThat(brands.findById(brand.getId()).orElseThrow().getSignature().getSampleCaption())
        .isEqualTo("Previous");
    UUID brandId = brand.getId();
    assertThat(attempts.findAll().stream().filter(a -> a.getBrandId().equals(brandId)).toList())
        .hasSize(1)
        .allMatch(a -> a.getStatus() == BrandAnalysisAttempt.Status.FAILED);
  }
}
