package com.example.hospital.security;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoginBackoff {
  private record State(int failures, Instant lockedUntil, Instant expiresAt) {}

  private record Expiry(Instant at, String username) implements Comparable<Expiry> {
    @Override
    public int compareTo(Expiry other) {
      int byTime = at.compareTo(other.at);
      return byTime != 0 ? byTime : username.compareTo(other.username);
    }
  }

  private record Deadline(Instant at, String username) implements Comparable<Deadline> {
    @Override
    public int compareTo(Deadline other) {
      int byTime = at.compareTo(other.at);
      return byTime != 0 ? byTime : username.compareTo(other.username);
    }
  }

  private final LoginBackoffProperties settings;
  private final Clock clock;
  private final Map<String, State> states = new HashMap<>();
  private final NavigableSet<Expiry> expirations = new TreeSet<>();
  private final NavigableSet<Expiry> unlockedExpirations = new TreeSet<>();
  private final NavigableSet<Deadline> lockDeadlines = new TreeSet<>();

  public LoginBackoff(LoginBackoffProperties settings, Clock clock) {
    this.settings = settings;
    this.clock = clock;
  }

  public synchronized boolean blocked(String username) {
    String key = key(username);
    if (key == null) return true;

    Instant now = clock.instant();
    purgeExpired(now);
    promoteUnlocked(now);
    State state = states.get(key);
    return state != null && isLocked(state, now);
  }

  public synchronized void failure(String username) {
    String key = key(username);
    if (key == null) return;

    Instant now = clock.instant();
    purgeExpired(now);
    promoteUnlocked(now);
    State previous = states.get(key);
    if (previous == null && states.size() >= settings.maxEntries()) {
      if (!evictUnlockedState(now)) return;
    }

    int failures =
        previous == null
            ? 1
            : previous.failures() == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.failures() + 1;
    Instant lockedUntil = previous == null ? null : previous.lockedUntil();
    if (failures >= settings.extendedLockThreshold()) {
      lockedUntil = later(lockedUntil, now.plus(settings.extendedLockDuration()));
    } else if (failures >= settings.firstLockThreshold()) {
      lockedUntil = later(lockedUntil, now.plus(settings.firstLockDuration()));
    }

    Instant expiryBase = lockedUntil != null && lockedUntil.isAfter(now) ? lockedUntil : now;
    putState(key, new State(failures, lockedUntil, expiryBase.plus(settings.idleTtl())), now);
  }

  public synchronized void success(String username) {
    String key = key(username);
    if (key != null) removeState(key);
  }

  @Scheduled(fixedDelayString = "${app.security.login-backoff.cleanup-interval}")
  public synchronized void cleanupExpired() {
    Instant now = clock.instant();
    purgeExpired(now);
    promoteUnlocked(now);
  }

  synchronized int trackedEntryCount() {
    return states.size();
  }

  private boolean evictUnlockedState(Instant now) {
    promoteUnlocked(now);
    if (unlockedExpirations.isEmpty()) return false;
    // Keep active lockouts; at capacity, discard the least recently failed unlocked name first.
    removeState(unlockedExpirations.first().username());
    return true;
  }

  private void purgeExpired(Instant now) {
    while (!expirations.isEmpty() && !expirations.first().at().isAfter(now)) {
      removeState(expirations.first().username());
    }
  }

  private void promoteUnlocked(Instant now) {
    while (!lockDeadlines.isEmpty() && !lockDeadlines.first().at().isAfter(now)) {
      Deadline deadline = lockDeadlines.pollFirst();
      State state = states.get(deadline.username());
      if (state != null
          && state.lockedUntil() != null
          && state.lockedUntil().equals(deadline.at())
          && !isLocked(state, now)) {
        unlockedExpirations.add(new Expiry(state.expiresAt(), deadline.username()));
      }
    }
  }

  private void putState(String username, State state, Instant now) {
    removeState(username);
    states.put(username, state);
    expirations.add(new Expiry(state.expiresAt(), username));
    if (isLocked(state, now)) {
      lockDeadlines.add(new Deadline(state.lockedUntil(), username));
    } else {
      unlockedExpirations.add(new Expiry(state.expiresAt(), username));
    }
  }

  private void removeState(String username) {
    State removed = states.remove(username);
    if (removed != null) {
      expirations.remove(new Expiry(removed.expiresAt(), username));
      unlockedExpirations.remove(new Expiry(removed.expiresAt(), username));
      if (removed.lockedUntil() != null)
        lockDeadlines.remove(new Deadline(removed.lockedUntil(), username));
    }
  }

  private String key(String username) {
    if (username == null) return "";
    if (username.length() > settings.maxUsernameLength()) return null;
    return username.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean isLocked(State state, Instant now) {
    return state.lockedUntil() != null && state.lockedUntil().isAfter(now);
  }

  private static Instant later(Instant first, Instant second) {
    return first == null || second.isAfter(first) ? second : first;
  }
}
