package com.cadence.application.ports;

/**
 * Internal bytes-only boundary. Call only after source checksum and CLEAN scan have been verified.
 */
public interface EvidenceRenderer {
  int MAX_HTML_BYTES = 2 * 1024 * 1024;
  int MAX_SCREENSHOT_BYTES = 3 * 1024 * 1024;
  int MAX_DOCUMENT_TEXT = 16000;

  Screenshot screenshot(byte[] utf8Html);

  DocumentText extractPdf(byte[] pdf);

  record Screenshot(byte[] bytes, int width, int height, String version) {
    public Screenshot {
      if (bytes == null
          || bytes.length == 0
          || bytes.length > MAX_SCREENSHOT_BYTES
          || width != 1280
          || height != 720
          || !"isolated-v1".equals(version))
        throw new IllegalArgumentException("Invalid evidence screenshot");
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  record DocumentText(String text, String version) {
    public DocumentText {
      if (text == null || text.length() > MAX_DOCUMENT_TEXT || !"isolated-v1".equals(version))
        throw new IllegalArgumentException("Invalid evidence document text");
    }
  }
}
