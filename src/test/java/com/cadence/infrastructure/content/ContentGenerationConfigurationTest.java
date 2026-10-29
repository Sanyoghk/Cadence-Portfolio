package com.cadence.infrastructure.content;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import com.cadence.domain.*;
import com.cadence.support.StrategyFixtures;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ContentGenerationConfigurationTest {
  @Test
  void productionDisabledAndFakeCannotEscapeIsolatedProfiles() {
    var config = new ContentGenerationConfiguration();
    assertThatThrownBy(() -> config.contentGenerationService(new MockEnvironment(), "fake"))
        .isInstanceOf(IllegalStateException.class);
    var prod = new MockEnvironment();
    prod.setActiveProfiles("local", "prod");
    assertThatThrownBy(() -> config.contentGenerationService(prod, "fake"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> config.contentGenerationService(new MockEnvironment(), "live"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> config.contentGenerationService(prod, "disabled").generate(null))
        .isInstanceOf(com.cadence.application.exceptions.BrandAnalysisUnavailableException.class);
  }

  @Test
  void deterministicDemoHasValidatedOutputWithoutInventedAssets() {
    var env = new MockEnvironment();
    env.setActiveProfiles("test");
    var adapter = new ContentGenerationConfiguration().contentGenerationService(env, "fake");
    var identity = StrategyFixtures.identityContent();
    var p = identity.palette();
    var command =
        new ContentGenerationCommand(
            UUID.randomUUID(),
            "Brand",
            "Creators",
            new StrategyGenerationCommand.Signature(
                new BrandAnalysisResult.Palette(
                    p.primary(), p.secondary(), p.accent(), p.neutral(), p.background()),
                identity.fonts(),
                identity.voiceUp(),
                identity.voiceDown(),
                identity.sampleCaption()),
            StrategyFixtures.output().content(),
            StrategyFixtures.playbook(),
            List.of("w1s0", "w4s0"),
            ContentGenerationRequest.Field.ALL,
            null);
    var result = adapter.generate(command);
    result.validateFor(command);
    assertThat(adapter.generate(command)).isEqualTo(result);
    assertThat(result.warnings())
        .contains(
            ContentGenerationResult.Warning.DEMONSTRATION_ONLY,
            ContentGenerationResult.Warning.HUMAN_REVIEW_REQUIRED);
    assertThat(result.items().getFirst().content().caption()).startsWith("[DEMO]");
  }
}
