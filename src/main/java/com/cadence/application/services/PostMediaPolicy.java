package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.MediaAssetRepository;
import com.cadence.application.results.MediaDetails;
import com.cadence.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Caller holds actual authorization and Account/Workspace/Brand/Strategy/Post locks. */
@Service
public class PostMediaPolicy {
  private final MediaAssetRepository assets;

  public PostMediaPolicy(MediaAssetRepository assets) {
    this.assets = assets;
  }

  public List<MediaDetails> capture(ContentPost post, PostFormat format, List<UUID> ids) {
    if (ids == null
        || ids.size() > 10
        || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size())
      throw new IllegalArgumentException("Invalid media references");
    var found = new HashMap<UUID, MediaAsset>();
    ids.stream()
        .sorted()
        .forEach(
            id -> {
              var a =
                  assets
                      .findLocked(id, post.getBrand().getId())
                      .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
              a.requireUsable();
              found.put(id, a);
            });
    if (!ids.isEmpty()) {
      var formats = ids.stream().map(id -> found.get(id).getFormat()).toList();
      boolean valid =
          switch (format) {
            case IMAGE -> formats.size() == 1 && formats.getFirst() == MediaFormat.PNG;
            case CAROUSEL ->
                formats.size() >= 2 && formats.stream().allMatch(f -> f == MediaFormat.PNG);
            case REEL, VIDEO -> formats.size() == 1 && formats.getFirst() == MediaFormat.MP4;
            case STORY -> formats.size() == 1;
            default -> false;
          };
      if (!valid) throw new IllegalArgumentException("Media incompatible with Post format");
    }
    return ids.stream().map(id -> MediaDetails.from(found.get(id))).toList();
  }
}
