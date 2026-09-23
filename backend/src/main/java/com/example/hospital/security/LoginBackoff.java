package com.example.hospital.security;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class LoginBackoff {
  private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
  private static final int SOURCE_LOCK_THRESHOLD = 30;

  private record AccountKey(String username, String sourceAddress) {}

  private record State(
      int failures,
      Instant windowStartedAt,
      Instant lockedUntil,
      boolean resetAfterLock,
      Instant expiresAt) {}

  private final LoginBackoffProperties settings;
  private final Clock clock;
  private final Map<AccountKey, State> accountStates = new HashMap<>();
  private final Map<String, State> sourceStates = new HashMap<>();

  public LoginBackoff(LoginBackoffProperties settings, Clock clock) {
    this.settings = settings;
    this.clock = clock;
  }

  public synchronized boolean blocked(String username) {
    return blocked(username, null);
  }

  public synchronized boolean blocked(String username, String sourceAddress) {
    String normalizedUsername = usernameKey(username);
    if (normalizedUsername == null) return true;

    Instant now = clock.instant();
    purgeExpired(now);

    AccountKey accountKey = new AccountKey(normalizedUsername, normalizedSource(sourceAddress));
    if (isBlocked(accountStates, accountKey, now)) return true;

    String sourceKey = sourceKey(sourceAddress);
    return sourceKey != null && isBlocked(sourceStates, sourceKey, now);
  }

  public synchronized void failure(String username) {
    failure(username, null);
  }

  public synchronized void failure(String username, String sourceAddress) {
    String normalizedUsername = usernameKey(username);
    if (normalizedUsername == null) return;

    Instant now = clock.instant();
    purgeExpired(now);

    AccountKey accountKey = new AccountKey(normalizedUsername, normalizedSource(sourceAddress));
    State previousAccount = accountStates.get(accountKey);
    if (previousAccount != null || makeRoom(now)) {
      accountStates.put(accountKey, nextAccountState(previousAccount, now));
    }

    String sourceKey = sourceKey(sourceAddress);
    if (sourceKey != null) {
      State previousSource = sourceStates.get(sourceKey);
      if (previousSource != null || makeRoom(now)) {
        sourceStates.put(sourceKey, nextSourceState(previousSource, now));
      }
    }
  }

  public synchronized void success(String username) {
    String normalizedUsername = usernameKey(username);
    if (normalizedUsername != null) {
      accountStates.remove(new AccountKey(normalizedUsername, ""));
    }
  }

  public synchronized void success(String username, String sourceAddress) {
    String normalizedUsername = usernameKey(username);
    if (normalizedUsername != null) {
      accountStates.remove(new AccountKey(normalizedUsername, normalizedSource(sourceAddress)));
    }
  }

  @Scheduled(fixedDelayString = "${app.security.login-backoff.cleanup-interval}")
  public synchronized void cleanupExpired() {
    purgeExpired(clock.instant());
  }

  synchronized int trackedEntryCount() {
    return accountStates.size() + sourceStates.size();
  }

  private State nextAccountState(State previous, Instant now) {
    if (isLocked(previous, now)) return previous;

    boolean reset =
        previous == null
            || previous.resetAfterLock()
            || !previous.windowStartedAt().plus(FAILURE_WINDOW).isAfter(now);

    int failures =
        reset
            ? 1
            : previous.failures() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : previous.failures() + 1;
    Instant windowStartedAt = reset ? now : previous.windowStartedAt();

    Instant lockedUntil = null;
    boolean resetAfterLock = false;
    if (failures >= settings.extendedLockThreshold()) {
      lockedUntil = now.plus(settings.extendedLockDuration());
      resetAfterLock = true;
    } else if (failures >= settings.firstLockThreshold()) {
      lockedUntil = now.plus(settings.firstLockDuration());
    }

    return newState(failures, windowStartedAt, lockedUntil, resetAfterLock, now);
  }

  private State nextSourceState(State previous, Instant now) {
    if (isLocked(previous, now)) return previous;

    boolean reset =
        previous == null
            || previous.resetAfterLock()
            || !previous.windowStartedAt().plus(FAILURE_WINDOW).isAfter(now);

    int failures =
        reset
            ? 1
            : previous.failures() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : previous.failures() + 1;
    Instant windowStartedAt = reset ? now : previous.windowStartedAt();

    boolean resetAfterLock = failures >= SOURCE_LOCK_THRESHOLD;
    Instant lockedUntil =
        resetAfterLock ? now.plus(settings.extendedLockDuration()) : null;

    return newState(failures, windowStartedAt, lockedUntil, resetAfterLock, now);
  }

  private State newState(
      int failures,
      Instant windowStartedAt,
      Instant lockedUntil,
      boolean resetAfterLock,
      Instant now) {
    Instant expiryBase =
        lockedUntil != null && lockedUntil.isAfter(now) ? lockedUntil : now;
    Instant expiresAt = expiryBase.plus(settings.idleTtl());
    Instant windowEndsAt = windowStartedAt.plus(FAILURE_WINDOW);
    if (windowEndsAt.isAfter(expiresAt)) expiresAt = windowEndsAt;

    return new State(failures, windowStartedAt, lockedUntil, resetAfterLock, expiresAt);
  }

  private <K> boolean isBlocked(Map<K, State> states, K key, Instant now) {
    State state = states.get(key);
    if (state == null) return false;
    if (isLocked(state, now)) return true;
    if (isExpired(state, now)) states.remove(key);
    return false;
  }

  private boolean makeRoom(Instant now) {
    if (trackedEntryCount() < settings.maxEntries()) return true;
    return evictUnlockedState(now);
  }

  private boolean evictUnlockedState(Instant now) {
    AccountKey accountToEvict = null;
    State oldestAccount = null;
    for (Map.Entry<AccountKey, State> entry : accountStates.entrySet()) {
      State state = entry.getValue();
      if (!isLocked(state, now)
          && (oldestAccount == null || state.expiresAt().isBefore(oldestAccount.expiresAt()))) {
        accountToEvict = entry.getKey();
        oldestAccount = state;
      }
    }

    String sourceToEvict = null;
    State oldestSource = null;
    for (Map.Entry<String, State> entry : sourceStates.entrySet()) {
      State state = entry.getValue();
      if (!isLocked(state, now)
          && (oldestSource == null || state.expiresAt().isBefore(oldestSource.expiresAt()))) {
        sourceToEvict = entry.getKey();
        oldestSource = state;
      }
    }

    if (accountToEvict == null && sourceToEvict == null) return false;

    if (sourceToEvict == null
        || (accountToEvict != null
            && !oldestSource.expiresAt().isBefore(oldestAccount.expiresAt()))) {
      accountStates.remove(accountToEvict);
    } else {
      sourceStates.remove(sourceToEvict);
    }
    return true;
  }

  private void purgeExpired(Instant now) {
    accountStates.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    sourceStates.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
  }

  private static boolean isLocked(State state, Instant now) {
    return state != null
        && state.lockedUntil() != null
        && state.lockedUntil().isAfter(now);
  }

  private static boolean isExpired(State state, Instant now) {
    return !isLocked(state, now) && !state.expiresAt().isAfter(now);
  }

  private String usernameKey(String username) {
    if (username == null) return "";
    if (username.length() > settings.maxUsernameLength()) return null;
    return username.strip().toLowerCase(Locale.ROOT);
  }

  private static String normalizedSource(String sourceAddress) {
    return sourceAddress == null ? "" : sourceAddress.strip();
  }

  private static String sourceKey(String sourceAddress) {
    if (sourceAddress == null || sourceAddress.isBlank()) return null;
    return sourceAddress.strip();
  }
}