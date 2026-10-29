package com.cadence.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import com.cadence.presentation.OpenApiContractAssertions;
import com.cadence.support.StrategyFixtures;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "cadence.strategy-generation.adapter=disabled")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StrategyApiFailClosedTest {
  @Autowired MockMvc mvc;
  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired StrategyRepository strategies;
  @Autowired CadenceUserRepository users;
  @Autowired com.cadence.application.usecases.BrandIdentityUseCase identities;

  @Test
  void actualDisabledAdapterReturns503WithoutFakeFallbackOrDraft() throws Exception {
    var workspace = workspaces.saveAndFlush(new Workspace("Disabled", "Creator"));
    var brand = new Brand("Brand", workspace);
    brand.updateSignature(StrategyFixtures.signature());
    brand = brands.saveAndFlush(brand);
    var actor =
        users.saveAndFlush(
            new CadenceUser("disabled-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    var principal =
        new CadencePrincipal(
            actor.getId(), "disabled-strategy", UserRole.OWNER, Set.of(workspace.getId()));
    var owner =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(owner);
    try {
      var identity =
          identities.create(
              brand.getId(), Set.of(workspace.getId()), StrategyFixtures.identityContent());
      identities.approve(identity.id(), Set.of(workspace.getId()), 0, actor.getId());
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(owner)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("strategy_generation_unavailable"))
        .andExpect(jsonPath("$.retryable").value(true))
        .andDo(OpenApiContractAssertions::verify);
    assertThat(strategies.findAllByBrandId(brand.getId(), PageRequest.of(0, 20)).getTotalElements())
        .isZero();
  }
}
