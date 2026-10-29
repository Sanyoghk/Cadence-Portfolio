package com.cadence.presentation.dto;

import com.cadence.domain.*;
import jakarta.validation.constraints.*;
import java.util.List;

public record PostContentRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 4000) String caption,
    @NotNull @Size(max = 30) List<@NotNull @Size(max = 100) String> hashtags,
    @NotNull PostFormat format,
    @NotBlank @Size(max = 2000) String mediaPrompt,
    @Size(max = 5000) String videoScript)
    implements StrictPostRequest {
  public PostContent content() {
    return new PostContent(title, caption, hashtags, format, mediaPrompt, videoScript);
  }
}
