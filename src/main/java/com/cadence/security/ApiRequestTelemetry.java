package com.cadence.security;

import java.util.concurrent.atomic.LongAdder;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/** Fixed-cardinality process counters; remote JMX is not enabled by this bean. */
@Component
@ManagedResource(objectName = "com.cadence:type=ApiRequestMetrics")
public class ApiRequestTelemetry {
  private final LongAdder[] statuses = {
    new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()
  };
  private final LongAdder limited = new LongAdder();
  private final LongAdder busy = new LongAdder();

  void completed(int status) {
    statuses[status >= 500 ? 3 : status >= 400 ? 2 : status >= 300 ? 1 : 0].increment();
  }

  void limited() {
    limited.increment();
  }

  void busy() {
    busy.increment();
  }

  @ManagedAttribute
  public long getSuccesses() {
    return statuses[0].sum();
  }

  @ManagedAttribute
  public long getRedirects() {
    return statuses[1].sum();
  }

  @ManagedAttribute
  public long getClientErrors() {
    return statuses[2].sum();
  }

  @ManagedAttribute
  public long getServerErrors() {
    return statuses[3].sum();
  }

  @ManagedAttribute
  public long getRateLimited() {
    return limited.sum();
  }

  @ManagedAttribute
  public long getCapacityRejected() {
    return busy.sum();
  }
}
