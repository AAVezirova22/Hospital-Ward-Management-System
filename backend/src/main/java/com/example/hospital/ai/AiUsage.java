package com.example.hospital.ai;

/**
 * Collects token usage reported by the provider during one assistant request on the current
 * thread, so the interaction record can store counts without the model client knowing about it.
 */
public final class AiUsage {
  public record Tokens(long prompt, long completion) {}

  private static final ThreadLocal<long[]> CURRENT = new ThreadLocal<>();

  private AiUsage() {}

  public static void start() {
    CURRENT.set(null);
  }

  static void record(long prompt, long completion) {
    long[] totals = CURRENT.get();
    if (totals == null) CURRENT.set(new long[] {prompt, completion});
    else {
      totals[0] += prompt;
      totals[1] += completion;
    }
  }

  /** Returns the usage collected since {@link #start()}, or null when the provider reported none. */
  public static Tokens drain() {
    long[] totals = CURRENT.get();
    CURRENT.remove();
    return totals == null ? null : new Tokens(totals[0], totals[1]);
  }
}
