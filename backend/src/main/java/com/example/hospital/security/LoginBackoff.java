package com.example.hospital.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class LoginBackoff {
  private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
  private static final Duration SHORT_LOCK = Duration.ofSeconds(30);
  private static final Duration LONG_LOCK = Duration.ofMinutes(5);
  private static final int SHORT_LOCK_THRESHOLD = 5;
  private static final int LONG_LOCK_THRESHOLD = 8;
  private static final int SOURCE_LOCK_THRESHOLD = 30;
  private static final int CLEANUP_INTERVAL = 256;

  private record AccountKey(String username, String sourceAddress) {}

  private record State(
      int failures, Instant windowStartedAt, Instant lockedUntil, boolean resetAfterLock) {}

  private final ConcurrentHashMap<AccountKey, State> accountStates = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, State> sourceStates = new ConcurrentHashMap<>();
  private final AtomicInteger failureCount = new AtomicInteger();
  private final Clock clock;

  public LoginBackoff() {
    this(Clock.systemUTC());
  }

  LoginBackoff(Clock clock) {
    this.clock = clock;
  }

  public boolean blocked(String username, String sourceAddress) {
    Instant now = clock.instant();
    AccountKey accountKey = accountKey(username, sourceAddress);
    boolean accountBlocked = isBlocked(accountStates, accountKey, now);
    String sourceKey = sourceKey(sourceAddress);
    boolean sourceBlocked = sourceKey != null && isBlocked(sourceStates, sourceKey, now);
    return accountBlocked || sourceBlocked;
  }

  public void failure(String username, String sourceAddress) {
    Instant now = clock.instant();
    AccountKey accountKey = accountKey(username, sourceAddress);
    accountStates.compute(accountKey, (ignored, previous) -> nextAccountState(previous, now));

    String sourceKey = sourceKey(sourceAddress);
    if (sourceKey != null) {
      sourceStates.compute(sourceKey, (ignored, previous) -> nextSourceState(previous, now));
    }

    if ((failureCount.incrementAndGet() & (CLEANUP_INTERVAL - 1)) == 0) purgeExpired(now);
  }

  public void success(String username, String sourceAddress) {
    accountStates.remove(accountKey(username, sourceAddress));
  }

  private static State nextAccountState(State previous, Instant now) {
    if (isLocked(previous, now)) return previous;

    boolean reset =
        previous == null
            || previous.resetAfterLock()
            || !previous.windowStartedAt().plus(FAILURE_WINDOW).isAfter(now);
    int failures = reset ? 1 : previous.failures() + 1;
    Instant windowStartedAt = reset ? now : previous.windowStartedAt();
    if (failures >= LONG_LOCK_THRESHOLD)
      return new State(failures, windowStartedAt, now.plus(LONG_LOCK), true);
    if (failures >= SHORT_LOCK_THRESHOLD)
      return new State(failures, windowStartedAt, now.plus(SHORT_LOCK), false);
    return new State(failures, windowStartedAt, Instant.MIN, false);
  }

  private static State nextSourceState(State previous, Instant now) {
    if (isLocked(previous, now)) return previous;

    boolean reset =
        previous == null
            || previous.resetAfterLock()
            || !previous.windowStartedAt().plus(FAILURE_WINDOW).isAfter(now);
    int failures = reset ? 1 : previous.failures() + 1;
    Instant windowStartedAt = reset ? now : previous.windowStartedAt();
    Instant lockedUntil =
        failures >= SOURCE_LOCK_THRESHOLD ? now.plus(LONG_LOCK) : Instant.MIN;
    return new State(failures, windowStartedAt, lockedUntil, failures >= SOURCE_LOCK_THRESHOLD);
  }

  private static <K> boolean isBlocked(
      ConcurrentHashMap<K, State> states, K key, Instant now) {
    State state = states.get(key);
    if (state == null) return false;
    if (isLocked(state, now)) return true;
    if (isExpired(state, now)) states.remove(key, state);
    return false;
  }

  private static boolean isLocked(State state, Instant now) {
    return state != null && state.lockedUntil().isAfter(now);
  }

  private static boolean isExpired(State state, Instant now) {
    return !state.lockedUntil().isAfter(now)
        && (state.resetAfterLock()
            || !state.windowStartedAt().plus(FAILURE_WINDOW).isAfter(now));
  }

  private void purgeExpired(Instant now) {
    accountStates.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    sourceStates.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
  }

  private static AccountKey accountKey(String username, String sourceAddress) {
    String normalizedUsername =
        username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    String normalizedSource = sourceAddress == null ? "" : sourceAddress.strip();
    return new AccountKey(normalizedUsername, normalizedSource);
  }

  private static String sourceKey(String sourceAddress) {
    if (sourceAddress == null || sourceAddress.isBlank()) return null;
    return sourceAddress.strip();
  }
}
