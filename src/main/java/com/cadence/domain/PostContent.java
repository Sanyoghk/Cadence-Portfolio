package com.cadence.domain;

import java.util.*;

/** Validated editorial content, never an executable publishing request. */
public record PostContent(
    String title,
    String caption,
    List<String> hashtags,
    PostFormat format,
    String mediaPrompt,
    String videoScript)
    implements StrictPlaybookValue {
  public PostContent {
    title = DomainText.required(title, "Post title", 255);
    caption = DomainText.required(caption, "Post caption", 4000);
    mediaPrompt = DomainText.required(mediaPrompt, "Media prompt", 2000);
    videoScript = DomainText.optional(videoScript, "Video script", 5000);
    for (String value : new String[] {title, caption, mediaPrompt, videoScript}) {
      if (value != null
          && value
              .codePoints()
              .anyMatch(
                  cp ->
                      (Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t')
                          || (cp >= 0xD800 && cp <= 0xDFFF)))
        throw new IllegalArgumentException("Invalid Post text encoding");
    }
    if (format == null || hashtags == null || hashtags.size() > 30)
      throw new IllegalArgumentException("Invalid Post content");
    if (hashtags.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid Post hashtags");
    hashtags = List.copyOf(hashtags);
    if (hashtags.stream()
            .anyMatch(h -> h == null || h.length() > 100 || !h.matches("#[\\p{L}\\p{N}_]+"))
        || new HashSet<>(hashtags).size() != hashtags.size())
      throw new IllegalArgumentException("Invalid Post hashtags");
    if (videoScript != null && format != PostFormat.VIDEO && format != PostFormat.REEL)
      throw new IllegalArgumentException("Video script requires video format");
  }

  public void validateFor(PlaybookContent.Channel platform) {
    if (platform == null) throw new IllegalArgumentException("Post platform required");
    var formats =
        platform == PlaybookContent.Channel.INSTAGRAM
            ? Set.of(PostFormat.IMAGE, PostFormat.CAROUSEL, PostFormat.REEL, PostFormat.STORY)
            : Set.of(PostFormat.VIDEO, PostFormat.CAROUSEL);
    if (!formats.contains(format)
        || (platform == PlaybookContent.Channel.INSTAGRAM && caption.length() > 2200))
      throw new IllegalArgumentException("Unsupported platform content");
  }
}
