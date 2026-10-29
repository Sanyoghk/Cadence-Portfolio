package com.cadence.security;

import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded process-local fixed windows, keyed by persisted account ID, never by client headers. */
@Component
public final class ApiRateLimit {
  private static final long WINDOW = Duration.ofMinutes(1).toNanos();

  private record Window(long start, int reads, int writes) {}

  private final Map<UUID, Window> accounts = new HashMap<>();
  private final int reads;
  private final int writes;
  private final int maxAccounts;
  private final LongSupplier ticker;

  @Autowired
  public ApiRateLimit(
      @Value("${cadence.http.reads-per-minute:1200}") int reads,
      @Value("${cadence.http.writes-per-minute:120}") int writes,
      @Value("${cadence.http.max-rate-accounts:2048}") int maxAccounts) {
    this(reads, writes, maxAccounts, System::nanoTime);
  }

  ApiRateLimit(int reads, int writes, int maxAccounts, LongSupplier ticker) {
    if (reads < 1
        || reads > 10000
        || writes < 1
        || writes > 1000
        || maxAccounts < 1
        || maxAccounts > 10000) throw new IllegalArgumentException("Invalid HTTP rate policy");
    this.reads = reads;
    this.writes = writes;
    this.maxAccounts = maxAccounts;
    this.ticker = Objects.requireNonNull(ticker);
  }

  public synchronized boolean allow(UUID actor, boolean read) {
    Objects.requireNonNull(actor);
    long now = ticker.getAsLong();
    Window old = accounts.get(actor);
    if (old == null || now - old.start() >= WINDOW) {
      accounts.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW);
      if (accounts.size() >= maxAccounts) return false;
      old = new Window(now, 0, 0);
    }
    if ((read ? old.reads() >= reads : old.writes() >= writes)) return false;
    accounts.put(
        actor,
        new Window(old.start(), old.reads() + (read ? 1 : 0), old.writes() + (read ? 0 : 1)));
    return true;
  }
}
