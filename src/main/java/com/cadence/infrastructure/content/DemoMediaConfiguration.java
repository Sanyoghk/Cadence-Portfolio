package com.cadence.infrastructure.content;

import com.cadence.application.exceptions.MediaUnavailableException;
import com.cadence.application.ports.DemoMediaSource;
import com.cadence.domain.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

@Configuration
public class DemoMediaConfiguration {
  @Bean
  DemoMediaSource demoMediaSource(Environment env) {
    boolean enabled = !env.matchesProfiles("prod") && env.matchesProfiles("local", "test");
    return new DemoMediaSource() {
      public void requireEnabled() {
        if (!enabled) throw new MediaUnavailableException();
      }

      public byte[] bytes(DemoMediaFixture fixture) {
        requireEnabled();
        if (fixture == null) throw new IllegalArgumentException("Fixture required");
        try (var in =
            DemoMediaConfiguration.class.getResourceAsStream("/demo-media/" + fixture.resource())) {
          if (in == null) throw new MediaUnavailableException();
          var bytes = in.readNBytes(MediaObjectKey.MAX_BYTES + 1);
          fixture.verify(bytes);
          return bytes;
        } catch (java.io.IOException | IllegalArgumentException unavailable) {
          throw new MediaUnavailableException();
        }
      }
    };
  }
}
