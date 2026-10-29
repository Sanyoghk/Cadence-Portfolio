package com.cadence.infrastructure.content;

import static org.assertj.core.api.Assertions.*;

import com.cadence.domain.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class DemoMediaConfigurationTest {
  @ParameterizedTest
  @ValueSource(strings = {"prod", "prod,local", "prod,test", "default"})
  void productionAndNonDemoEnvironmentsStayDisabled(String profiles) {
    var env = new MockEnvironment();
    env.setActiveProfiles(profiles.split(","));
    var source = new DemoMediaConfiguration().demoMediaSource(env);
    assertThatThrownBy(() -> source.bytes(DemoMediaFixture.IMAGE_V1))
        .isInstanceOf(com.cadence.application.exceptions.MediaUnavailableException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "test"})
  void catalogMatchesActualPackagedFixtureBytes(String profile) {
    var env = new MockEnvironment();
    env.setActiveProfiles(profile);
    var source = new DemoMediaConfiguration().demoMediaSource(env);
    for (var fixture : DemoMediaFixture.values()) {
      byte[] bytes = source.bytes(fixture);
      fixture.verify(bytes);
      bytes[0] = 0;
      fixture.verify(source.bytes(fixture));
    }
  }
}
