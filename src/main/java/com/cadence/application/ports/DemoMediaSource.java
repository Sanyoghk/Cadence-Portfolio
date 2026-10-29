package com.cadence.application.ports;

import com.cadence.domain.DemoMediaFixture;

/** Local/test catalog only. A future live media adapter needs a separate reviewed contract. */
public interface DemoMediaSource {
  void requireEnabled();

  byte[] bytes(DemoMediaFixture fixture);
}
