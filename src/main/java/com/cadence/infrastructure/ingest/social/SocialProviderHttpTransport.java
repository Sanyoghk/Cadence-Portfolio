package com.cadence.infrastructure.ingest.social;

import java.net.URI;
import java.time.Duration;

/** Package-owned seam for deterministic official API contracts; credentials never form a URL. */
public interface SocialProviderHttpTransport {
  byte[] request(URI uri, String bearer, String postJson, Duration remaining);
}
