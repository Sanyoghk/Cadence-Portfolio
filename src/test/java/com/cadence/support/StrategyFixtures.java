package com.cadence.support;

import com.cadence.application.ports.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;

public final class StrategyFixtures {
  private StrategyFixtures() {}

  public static StrategyGenerationResult output() {
    return new StrategyGenerationResult(
        List.of(
            new StrategyGenerationResult.Pillar("Education", 60, "Explain value"),
            new StrategyGenerationResult.Pillar("Community", 40, "Invite feedback")),
        3,
        "UTC",
        List.of(LocalTime.NOON),
        List.of(StrategyGenerationResult.Warning.HUMAN_REVIEW_REQUIRED),
        "test-provider",
        "model-v1",
        "prompt-v2",
        playbook());
  }

  public static PlaybookContent playbook() {
    return new com.cadence.domain.PlaybookContent(
        2,
        List.of("education", "community"),
        List.of(
            new com.cadence.domain.PlaybookContent.Audience(
                "creators",
                "Independent creators",
                "Understand the brand offer",
                "Demonstration assumption requiring human review")),
        List.of(
            new com.cadence.domain.PlaybookContent.ChannelPlan(
                com.cadence.domain.PlaybookContent.Channel.INSTAGRAM,
                100,
                List.of(
                    new com.cadence.domain.PlaybookContent.Goal(
                        "Explain value", "Saves", "Demonstration goal")))),
        List.of(
            new com.cadence.domain.PlaybookContent.Tone(
                "voice",
                "Informal",
                "Formal",
                40,
                "Apply approved voice; review this demonstration")),
        new com.cadence.domain.PlaybookContent.Whitespace(
            com.cadence.domain.PlaybookContent.EvidenceStatus.INSUFFICIENT_EVIDENCE, List.of()),
        java.util.stream.IntStream.rangeClosed(1, 4)
            .mapToObj(
                w ->
                    new com.cadence.domain.PlaybookContent.Week(
                        w,
                        java.util.stream.IntStream.range(0, 3)
                            .mapToObj(
                                i ->
                                    new com.cadence.domain.PlaybookContent.Slot(
                                        "w" + w + "s" + i,
                                        com.cadence.domain.PlaybookContent.Channel.INSTAGRAM,
                                        i == 2 ? "community" : "education",
                                        "creators",
                                        "Demonstration topic " + (i + 1),
                                        "Invite considered engagement",
                                        i * 2))
                            .toList()))
            .toList());
  }

  public static PlaybookContent editPlaybook() {
    var p = playbook();
    return new PlaybookContent(
        2,
        List.of("community"),
        p.audienceAssumptions(),
        p.channels(),
        p.toneSpectrum(),
        p.competitiveWhitespace(),
        java.util.stream.IntStream.rangeClosed(1, 4)
            .mapToObj(
                w ->
                    new PlaybookContent.Week(
                        w,
                        java.util.stream.IntStream.range(0, 4)
                            .mapToObj(
                                i ->
                                    new PlaybookContent.Slot(
                                        "w" + w + "s" + i,
                                        PlaybookContent.Channel.INSTAGRAM,
                                        "community",
                                        "creators",
                                        "Human topic " + i,
                                        "Inform",
                                        i))
                            .toList()))
            .toList());
  }

  public static BrandIdentityContent identityContent() {
    return new BrandIdentityContent(
        new BrandIdentityContent.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
        List.of("Inter"),
        List.of("clear"),
        List.of("spam"),
        "Caption");
  }

  public static BrandSignature signature() {
    return new BrandSignature(
        new ColorPalette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
        List.of("Inter"),
        List.of("clear"),
        List.of("spam"),
        "Caption");
  }

  public static StrategyGenerationCommand command() {
    return new StrategyGenerationCommand(
        UUID.randomUUID(),
        0,
        0,
        "Brand",
        "Creator",
        new StrategyGenerationCommand.Signature(
            new BrandAnalysisResult.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
            List.of("Inter"),
            List.of("clear"),
            List.of("spam"),
            "Caption"),
        Instant.now());
  }
}
