package com.cadence.application.ports;

import com.cadence.domain.*;
import java.util.*;

public record ContentGenerationResult(
    int schemaVersion,
    List<Item> items,
    String provider,
    String modelVersion,
    String promptVersion,
    List<Warning> warnings)
    implements StrictPlaybookValue {
  public enum Warning {
    DEMONSTRATION_ONLY,
    HUMAN_REVIEW_REQUIRED
  }

  public record Item(String slotId, PlaybookContent.Channel channel, PostContent content)
      implements StrictPlaybookValue {
    public Item {
      if (slotId == null
          || !slotId.matches("[A-Za-z0-9_-]{1,64}")
          || channel == null
          || content == null) throw new IllegalArgumentException("Invalid Content item");
      content.validateFor(channel);
    }
  }

  public ContentGenerationResult {
    if (schemaVersion != 1
        || items == null
        || items.isEmpty()
        || items.size() > 84
        || warnings == null) throw new IllegalArgumentException("Invalid Content output");
    items = List.copyOf(items);
    warnings = List.copyOf(warnings);
    if (warnings.size() > 2 || new HashSet<>(warnings).size() != warnings.size())
      throw new IllegalArgumentException("Invalid Content warnings");
    if (new HashSet<>(items.stream().map(Item::slotId).toList()).size() != items.size()
        || !warnings.contains(Warning.HUMAN_REVIEW_REQUIRED))
      throw new IllegalArgumentException("Invalid Content output");
    for (String id : new String[] {provider, modelVersion, promptVersion})
      if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
        throw new IllegalArgumentException("Invalid Content provenance");
  }

  public void validateFor(ContentGenerationCommand input) {
    if (items.size() != input.slotIds().size()
        || !new HashSet<>(items.stream().map(Item::slotId).toList())
            .equals(new HashSet<>(input.slotIds())))
      throw new IllegalArgumentException("Content slot set mismatch");
    for (var item : items) {
      var slot =
          input.selected().stream()
              .filter(s -> s.slotId().equals(item.slotId()))
              .findFirst()
              .orElseThrow();
      if (slot.channel() != item.channel())
        throw new IllegalArgumentException("Content channel mismatch");
      item.content().validateFor(slot.channel());
    }
  }
}
