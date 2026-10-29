package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.services.WorkerFailureDiagnostics;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorkerFailureDiagnosticsTest {
  @Test
  void safeCorrelationNeverLogsExceptionTextCauseOrPayload() {
    var logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WorkerFailureDiagnostics.class);
    var events =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    events.start();
    logger.addAppender(events);
    try {
      var id = UUID.randomUUID();
      WorkerFailureDiagnostics.rejected(
          WorkerFailureDiagnostics.Kind.METRIC_IMPORT,
          id,
          2,
          new IllegalStateException(
              "private-prompt\r\nAuthorization: Bearer private-token",
              new RuntimeException("private-password")));
      assertThat(events.list).hasSize(1);
      assertThat(events.list.getFirst().getFormattedMessage())
          .contains(id.toString(), "attempt=2", "STATE_REJECTED")
          .doesNotContain("private", "Authorization", "Bearer");
      assertThat(events.list.getFirst().getThrowableProxy()).isNull();
    } finally {
      logger.detachAppender(events);
      events.stop();
    }
  }
}
