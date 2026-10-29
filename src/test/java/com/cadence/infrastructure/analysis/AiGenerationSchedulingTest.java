package com.cadence.infrastructure.analysis;

import static org.mockito.Mockito.*;

import com.cadence.application.services.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class AiGenerationSchedulingTest {
  @Test
  void metricsExposeOnlyFixedCountersThroughLocalMBean() throws Exception {
    var server = javax.management.MBeanServerFactory.newMBeanServer();
    var exporter = new org.springframework.jmx.export.annotation.AnnotationMBeanExporter();
    var metrics = new AiGenerationTelemetry();
    exporter.setServer(server);
    exporter.setAutodetect(false);
    exporter.setBeans(java.util.Map.of("metrics", metrics));
    exporter.afterPropertiesSet();
    exporter.afterSingletonsInstantiated();
    try {
      var name = new javax.management.ObjectName("com.cadence:type=AiGenerationMetrics");
      org.assertj.core.api.Assertions.assertThat(server.getAttribute(name, "PollFailures"))
          .isEqualTo(0L);
      var counts =
          (java.util.Map<?, ?>)
              server.invoke(name, "completedCounts", new Object[0], new String[0]);
      org.assertj.core.api.Assertions.assertThat(counts).hasSize(6);
    } finally {
      exporter.destroy();
    }
  }

  @Test
  void outageUsesBoundedBackoffAndRecoversWithoutLeakingException() {
    var worker = mock(AiGenerationService.class);
    var metrics = mock(AiGenerationTelemetry.class);
    var clock = mock(Clock.class);
    var now = Instant.parse("2026-10-16T00:00:00Z");
    when(clock.instant()).thenReturn(now);
    var poller = new AiGenerationScheduling(worker, metrics, clock);
    doThrow(new IllegalStateException("PRIVATE SQL USER CONTENT")).when(worker).runOnce();
    poller.tick();
    poller.tick();
    verify(worker, times(1)).runOnce();
    verify(metrics).pollFailed();
    when(clock.instant()).thenReturn(now.plusSeconds(1));
    poller.tick();
    verify(worker, times(1)).runOnce();
    when(clock.instant()).thenReturn(now.plusSeconds(2));
    poller.tick();
    verify(worker, times(2)).runOnce();
    when(clock.instant()).thenReturn(now.plusSeconds(5));
    poller.tick();
    verify(worker, times(2)).runOnce();
    doNothing().when(worker).runOnce();
    when(clock.instant()).thenReturn(now.plusSeconds(6));
    poller.tick();
    poller.tick();
    verify(worker, times(4)).runOnce();
    verify(metrics, times(2)).pollFailed();
  }
}
