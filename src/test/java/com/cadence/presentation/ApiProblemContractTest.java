package com.cadence.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ApiProblemContractTest {
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(new Fixture())
            .setControllerAdvice(new ApiExceptionHandler(), new ApiProblemHeaders())
            .build();
  }

  @Test
  void malformedUuidAndJsonAreControlled400() throws Exception {
    check(
        mvc.perform(get("/fixture/id/secret-input"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_request"))
            .andReturn()
            .getResponse());
    check(
        mvc.perform(
                post("/fixture").contentType(MediaType.APPLICATION_JSON).content("secret-input"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("malformed_body"))
            .andReturn()
            .getResponse());
  }

  @Test
  void validationHasStableFieldCodesWithoutRejectedValues() throws Exception {
    check(
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"))
            .andExpect(jsonPath("$.errors[0].field").value("name"))
            .andExpect(jsonPath("$.errors[0].code").value("required"))
            .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
            .andReturn()
            .getResponse());
  }

  @Test
  void frameworkErrorsPreserveStatusAndProtocolHeaders() throws Exception {
    check(
        mvc.perform(put("/fixture"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow"))
            .andExpect(jsonPath("$.code").value("method_not_allowed"))
            .andReturn()
            .getResponse());
    check(
        mvc.perform(post("/fixture").contentType(MediaType.TEXT_PLAIN).content("secret-input"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("unsupported_media_type"))
            .andReturn()
            .getResponse());
  }

  @Test
  void internalFailureNeverExposesExceptionOrCause() throws Exception {
    check(
        mvc.perform(get("/fixture/fail"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("internal_error"))
            .andReturn()
            .getResponse());
  }

  private void check(org.springframework.mock.web.MockHttpServletResponse response)
      throws Exception {
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    var json =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
    assertThat(json.path("type").asText()).isEqualTo("about:blank");
    assertThat(json.path("status").asInt()).isEqualTo(response.getStatus());
    assertThat(json.path("instance").asText()).startsWith("urn:uuid:");
    UUID.fromString(json.path("instance").asText().substring(9));
    assertThat(json.path("retryable").isBoolean()).isTrue();
    assertThat(response.getContentAsString())
        .doesNotContain("secret-input", "password", "SELECT", "stackTrace");
  }

  @RestController
  static class Fixture {
    record Input(@NotBlank String name) {}

    @PostMapping(value = "/fixture", consumes = MediaType.APPLICATION_JSON_VALUE)
    Input create(@Valid @RequestBody Input input) {
      return input;
    }

    @GetMapping("/fixture/id/{id}")
    UUID id(@PathVariable UUID id) {
      return id;
    }

    @GetMapping("/fixture/fail")
    String fail() {
      throw new RuntimeException("password=secret-input SELECT", new Exception("secret-input"));
    }
  }
}
