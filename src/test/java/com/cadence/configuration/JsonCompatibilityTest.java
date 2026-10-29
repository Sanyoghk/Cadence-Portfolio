package com.cadence.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadence.application.services.AiGenerationSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/** Locks the reviewed wire and persisted JSON contract across the Boot major upgrade. */
@SpringBootTest
@ActiveProfiles("test")
class JsonCompatibilityTest {
  @Autowired ObjectMapper json;
  @Autowired RequestMappingHandlerAdapter adapter;

  @Test
  void mvcUsesTheReviewedMapperAndUtcIsoTimestamps() throws Exception {
    var converter =
        adapter.getMessageConverters().stream()
            .filter(c -> c.canWrite(Timestamp.class, MediaType.APPLICATION_JSON))
            .findFirst()
            .orElseThrow();
    assertThat(converter).isInstanceOf(MappingJackson2HttpMessageConverter.class);
    assertThat(((MappingJackson2HttpMessageConverter) converter).getObjectMapper()).isSameAs(json);
    assertThat(json.writeValueAsString(new Timestamp(Instant.parse("2026-10-15T12:30:00Z"))))
        .isEqualTo("{\"at\":\"2026-10-15T12:30:00Z\"}");
  }

  @Test
  void existingVersionOneSnapshotRemainsReadableWithoutRewriting() throws Exception {
    String stored =
        """
        {"schemaVersion":1,"analysis":{"analysisId":"00000000-0000-0000-0000-000000000001",
        "brandVersion":2,"workspaceVersion":3,"brandName":"Creator","industry":"Art",
        "sources":[]},"strategy":null,"identity":null}
        """;
    var snapshot = json.readValue(stored, AiGenerationSnapshot.class);
    assertThat(snapshot.input().analysis().brandName()).isEqualTo("Creator");
    assertThat(snapshot.input().analysis().brandVersion()).isEqualTo(2);
    assertThat(json.readTree(json.writeValueAsString(snapshot))).isEqualTo(json.readTree(stored));
  }

  public record Timestamp(Instant at) {}
}
