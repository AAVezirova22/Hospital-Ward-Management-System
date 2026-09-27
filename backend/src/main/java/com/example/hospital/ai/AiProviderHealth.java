package com.example.hospital.ai;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Last known health of the external assistant provider on this instance (#339), fed by real
 * assistant requests, administrator connection tests and an optional scheduled check. It keeps
 * only a status, a failure category and times; never prompts, replies or provider error text.
 */
@Component
public class AiProviderHealth {
  public record Snapshot(
      String status,
      String category,
      String source,
      Instant lastCheckedAt,
      Instant lastSuccessAt,
      Instant lastFailureAt,
      int consecutiveFailures) {}

  static final int UNAVAILABLE_AFTER = 3;

  private final boolean external;
  private final boolean scheduledChecks;
  private final AiProviderCheck check;
  private Snapshot current;

  public AiProviderHealth(
      @Value("${app.ai.mode}") String mode,
      @Value("${app.ai.health-check-enabled:false}") boolean scheduledChecks,
      AiProviderCheck check) {
    this.external = "external".equals(mode);
    this.scheduledChecks = scheduledChecks;
    this.check = check;
    this.current = new Snapshot(external ? "UNKNOWN" : "NOT_EXTERNAL", null, null, null, null, null, 0);
  }

  public synchronized Snapshot snapshot() {
    return current;
  }

  /**
   * Records one observation. {@code COMPATIBLE} or {@code OK} is healthy. A failed administrator
   * test or scheduled check marks the provider unavailable at once; failed assistant requests
   * degrade it first and mark it unavailable after three in a row.
   */
  public synchronized void record(String source, String outcome) {
    if (!external || outcome == null || "NOT_EXTERNAL".equals(outcome)) return;
    Instant now = Instant.now();
    if ("COMPATIBLE".equals(outcome) || "OK".equals(outcome)) {
      current = new Snapshot("HEALTHY", "OK", source, now, now, current.lastFailureAt(), 0);
      return;
    }
    int failures = current.consecutiveFailures() + 1;
    boolean definitive = !"TRAFFIC".equals(source);
    String status = definitive || failures >= UNAVAILABLE_AFTER ? "UNAVAILABLE" : "DEGRADED";
    current = new Snapshot(status, outcome, source, now, current.lastSuccessAt(), now, failures);
  }

  @Scheduled(
      initialDelayString = "${app.ai.health-check-initial-delay-ms:60000}",
      fixedDelayString = "${app.ai.health-check-interval-ms:900000}")
  public void scheduledCheck() {
    if (!external || !scheduledChecks) return;
    record("SCHEDULED", String.valueOf(check.run().get("outcome")));
  }
}
