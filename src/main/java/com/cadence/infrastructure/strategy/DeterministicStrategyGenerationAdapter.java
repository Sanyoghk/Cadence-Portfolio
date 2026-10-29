package com.cadence.infrastructure.strategy;

import com.cadence.application.ports.*;
import java.time.LocalTime;
import java.util.List;

final class DeterministicStrategyGenerationAdapter implements StrategyGenerationService {
  public StrategyGenerationResult generate(StrategyGenerationCommand command) {
    return new StrategyGenerationResult(
        List.of(
            new StrategyGenerationResult.Pillar("Education", 60, "Explain the brand's value"),
            new StrategyGenerationResult.Pillar("Community", 40, "Invite audience feedback")),
        3,
        "UTC",
        List.of(LocalTime.NOON),
        List.of(
            StrategyGenerationResult.Warning.DEMONSTRATION_ONLY,
            StrategyGenerationResult.Warning.HUMAN_REVIEW_REQUIRED),
        "local-fake",
        "deterministic-v2",
        "strategy-playbook-v2",
        new com.cadence.domain.PlaybookContent(
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
                .toList()));
  }
}
