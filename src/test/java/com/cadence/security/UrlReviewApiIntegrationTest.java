package com.cadence.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.support.UrlReviewApiContract;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class UrlReviewApiIntegrationTest extends UrlReviewApiContract {
  @Test
  void changedStorageGenerationFails() {
    jdbc.update(
        "update brand_evidence set fetched_reference=? where id=?",
        "quarantine:quarantine/writes/" + ws.getId() + "/" + UUID.randomUUID() + ".html",
        source.getId());
    assertThatThrownBy(() -> token()).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(storage);
  }
}
