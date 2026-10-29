package com.cadence.infrastructure.content;

import com.cadence.application.exceptions.BrandAnalysisUnavailableException;
import com.cadence.application.ports.*;
import com.cadence.domain.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

@Configuration
public class ContentGenerationConfiguration {
  @Bean
  ContentGenerationService contentGenerationService(
      Environment environment,
      @Value("${cadence.content-generation.adapter:disabled}") String adapter) {
    return switch (adapter) {
      case "disabled" ->
          command -> {
            throw new BrandAnalysisUnavailableException();
          };
      case "fake" -> {
        if (environment.acceptsProfiles(Profiles.of("prod"))
            || !environment.acceptsProfiles(Profiles.of("local", "test")))
          throw new IllegalStateException(
              "Fake Content generator requires local/test and forbids prod");
        yield command ->
            new ContentGenerationResult(
                1,
                command.selected().stream()
                    .map(
                        s ->
                            new ContentGenerationResult.Item(
                                s.slotId(),
                                s.channel(),
                                new PostContent(
                                    "Demo: " + s.slotId(),
                                    "[DEMO] " + s.topic() + " — " + s.intent(),
                                    List.of("#demo"),
                                    s.channel() == PlaybookContent.Channel.INSTAGRAM
                                        ? PostFormat.IMAGE
                                        : PostFormat.VIDEO,
                                    "[DEMO] Visual concept: " + s.topic(),
                                    s.channel() == PlaybookContent.Channel.TIKTOK
                                        ? "[DEMO] Introduce the topic; explain; invite feedback."
                                        : null)))
                    .toList(),
                "deterministic-fake",
                "demo-content-v1",
                "content-v1",
                List.of(
                    ContentGenerationResult.Warning.DEMONSTRATION_ONLY,
                    ContentGenerationResult.Warning.HUMAN_REVIEW_REQUIRED));
      }
      default -> throw new IllegalStateException("Unsupported Content generation adapter");
    };
  }
}
