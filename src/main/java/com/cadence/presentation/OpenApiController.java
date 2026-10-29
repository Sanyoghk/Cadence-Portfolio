package com.cadence.presentation;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reviewed contract is versioned with the implementation, without a runtime schema generator. */
@RestController
public class OpenApiController {
  private final byte[] contract;

  public OpenApiController() throws IOException {
    try (var input = new ClassPathResource("openapi/cadence.json").getInputStream()) {
      contract = input.readAllBytes();
    }
  }

  @GetMapping(value = "/api/v1/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<byte[]> contract() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_JSON)
        .body(contract);
  }
}
