package com.cadence.application.ports;

public interface UrlEvidenceFetcher {
  FetchedUrl fetch(String source);

  record FetchedUrl(byte[] body, String mediaType) {}
}
