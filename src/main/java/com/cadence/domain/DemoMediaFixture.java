package com.cadence.domain;

/** Exact authored fixtures, not inferred decoder metadata or AI output. */
public enum DemoMediaFixture {
  IMAGE_V1(
      MediaFormat.PNG,
      6688,
      "6318b17380802c6bf6ecc6b0333af1a064ea70b5944ddbc36da4319d671ffc99",
      512,
      512,
      0,
      "demo-image-v1.png"),
  VIDEO_V1(
      MediaFormat.MP4,
      6288,
      "ac056108384f7da890c0c38f7171ea46447e69d4536b9ef4405a35f7296be9f2",
      360,
      640,
      2000,
      "demo-video-v1.mp4");
  private final MediaFormat format;
  private final int size, width, height, durationMillis;
  private final String checksum, resource;

  DemoMediaFixture(
      MediaFormat format,
      int size,
      String checksum,
      int width,
      int height,
      int durationMillis,
      String resource) {
    this.format = format;
    this.size = size;
    this.checksum = checksum;
    this.width = width;
    this.height = height;
    this.durationMillis = durationMillis;
    this.resource = resource;
  }

  public MediaFormat format() {
    return format;
  }

  public int size() {
    return size;
  }

  public String checksum() {
    return checksum;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int durationMillis() {
    return durationMillis;
  }

  public String resource() {
    return resource;
  }

  public void verify(byte[] bytes) {
    if (bytes == null
        || bytes.length != size
        || bytes.length > MediaObjectKey.MAX_BYTES
        || !MediaObjectKey.hash(bytes).equals(checksum))
      throw new IllegalArgumentException("Media fixture bytes do not match catalog");
  }
}
