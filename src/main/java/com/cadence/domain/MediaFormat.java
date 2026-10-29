package com.cadence.domain;

public enum MediaFormat {
  PNG("image/png", "png"),
  MP4("video/mp4", "mp4");
  private final String mime, extension;

  MediaFormat(String mime, String extension) {
    this.mime = mime;
    this.extension = extension;
  }

  public String mime() {
    return mime;
  }

  public String extension() {
    return extension;
  }
}
