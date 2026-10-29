package com.cadence.application.ports;

import com.cadence.domain.*;
import java.util.*;

public record ContentGenerationCommand(
    UUID generationId,
    String brandName,
    String industry,
    StrategyGenerationCommand.Signature identity,
    StrategyContent strategy,
    PlaybookContent playbook,
    List<String> slotIds,
    ContentGenerationRequest.Field field,
    PostContent baseline) {
  public ContentGenerationCommand {
    if (generationId == null
        || brandName == null
        || industry == null
        || identity == null
        || strategy == null
        || playbook == null
        || field == null) throw new IllegalArgumentException("Invalid Content input");
    for (var value : List.of(brandName, industry))
      if (value.isBlank()
          || value.length() > 255
          || value
              .codePoints()
              .anyMatch(cp -> Character.isISOControl(cp) || (cp >= 0xD800 && cp <= 0xDFFF)))
        throw new IllegalArgumentException("Invalid Content context");
    if (slotIds == null
        || (field != ContentGenerationRequest.Field.ALL && baseline == null)
        || (baseline != null && slotIds.size() != 1))
      throw new IllegalArgumentException("Invalid Content regeneration baseline");
    playbook.validateWith(strategy);
    slotIds = List.copyOf(slotIds);
    var all =
        playbook.fourWeekOutline().stream()
            .flatMap(w -> w.slots().stream())
            .map(PlaybookContent.Slot::slotId)
            .toList();
    if (slotIds.isEmpty()
        || slotIds.size() > 84
        || new HashSet<>(slotIds).size() != slotIds.size()
        || !all.containsAll(slotIds)) throw new IllegalArgumentException("Invalid selected slots");
  }

  public List<PlaybookContent.Slot> selected() {
    return playbook.fourWeekOutline().stream()
        .flatMap(w -> w.slots().stream())
        .filter(s -> slotIds.contains(s.slotId()))
        .toList();
  }
}
