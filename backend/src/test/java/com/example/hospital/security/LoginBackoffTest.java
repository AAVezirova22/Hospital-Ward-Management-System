package com.example.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginBackoffTest {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void highCardinalityFailuresStayBoundedAndExpire() {
    var clock = new MutableClock(START);
    var backoff = new LoginBackoff(settings(3, 5, 8), clock);

    for (int index = 0; index < 1_000; index++) {
      backoff.failure("unknown-" + index);
    }

    assertThat(backoff.trackedEntryCount()).isEqualTo(3);

    clock.advance(Duration.ofHours(24).plusSeconds(1));
    backoff.cleanupExpired();

    assertThat(backoff.trackedEntryCount()).isZero();
  }

  @Test
  void capacityEvictionPreservesActiveLockouts() {
    var clock = new MutableClock(START);
    var backoff = new LoginBackoff(settings(2, 2, 4), clock);
    backoff.failure("locked-user");
    backoff.failure("locked-user");
    backoff.failure("other-user");
    backoff.failure("new-user");

    assertThat(backoff.trackedEntryCount()).isEqualTo(2);
    assertThat(backoff.blocked("locked-user")).isTrue();
  }

  @Test
  void fullCapacityOfActiveLockoutsDoesNotGrowForNewNames() {
    var clock = new MutableClock(START);
    var backoff = new LoginBackoff(settings(1, 2, 4), clock);
    backoff.failure("locked-user");
    backoff.failure("locked-user");
    backoff.failure("new-user");

    assertThat(backoff.trackedEntryCount()).isEqualTo(1);
    assertThat(backoff.blocked("locked-user")).isTrue();
  }

  @Test
  void lockoutAndEscalationStateSurviveUntilIdleExpiry() {
    var clock = new MutableClock(START);
    var backoff = new LoginBackoff(settings(4, 2, 4), clock);
    backoff.failure("member");
    backoff.failure("member");

    clock.advance(Duration.ofSeconds(31));
    backoff.cleanupExpired();
    assertThat(backoff.trackedEntryCount()).isEqualTo(1);
    assertThat(backoff.blocked("member")).isFalse();

    backoff.failure("member");
    assertThat(backoff.blocked("member")).isTrue();
    clock.advance(Duration.ofSeconds(31));
    backoff.failure("member");
    assertThat(backoff.blocked("member")).isTrue();
    clock.advance(Duration.ofMinutes(5).plusSeconds(1));
    assertThat(backoff.blocked("member")).isFalse();

    clock.advance(Duration.ofMinutes(10));
    backoff.cleanupExpired();
    assertThat(backoff.trackedEntryCount()).isZero();
  }

  @Test
  void successfulLoginClearsStateAndOversizedNamesAreNotStored() {
    var clock = new MutableClock(START);
    var backoff = new LoginBackoff(settings(2, 2, 4), clock);
    backoff.failure(" MEMBER ");
    backoff.failure("member");
    assertThat(backoff.blocked("member")).isTrue();

    backoff.success("Member");
    assertThat(backoff.blocked("member")).isFalse();

    String oversized = "x".repeat(65);
    assertThat(backoff.blocked(oversized)).isTrue();
    backoff.failure(oversized);
    assertThat(backoff.trackedEntryCount()).isZero();
  }

  private static LoginBackoffProperties settings(
      int maxEntries, int firstThreshold, int extendedThreshold) {
    return new LoginBackoffProperties(
        maxEntries,
        64,
        firstThreshold,
        Duration.ofSeconds(30),
        extendedThreshold,
        Duration.ofMinutes(5),
        Duration.ofMinutes(10));
  }

  @Test
  void accountLockIsSourceScopedAndDoesNotRearmFromOneFailureAfterExpiry() {
    var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    var backoff = new LoginBackoff(settings(10_000, 5, 8), clock);
    String username = "  Victim  ";
    String attacker = "203.0.113.10";
    String victim = "198.51.100.20";

    for (int attempt = 0; attempt < 5; attempt++) backoff.failure(username, attacker);
    assertThat(backoff.blocked("victim", attacker)).isTrue();
    assertThat(backoff.blocked("victim", victim)).isFalse();

    clock.advance(Duration.ofSeconds(31));
    assertThat(backoff.blocked("victim", attacker)).isFalse();
    for (int attempt = 0; attempt < 3; attempt++) {
      backoff.failure(username, attacker);
      if (attempt < 2) clock.advance(Duration.ofSeconds(31));
    }
    assertThat(backoff.blocked("victim", attacker)).isTrue();

    backoff.success("victim", victim);
    assertThat(backoff.blocked("victim", attacker)).isTrue();
    assertThat(backoff.blocked("victim", victim)).isFalse();

    clock.advance(Duration.ofMinutes(5));
    assertThat(backoff.blocked("victim", attacker)).isFalse();
    backoff.failure(username, attacker);
    assertThat(backoff.blocked("victim", attacker)).isFalse();
  }

  @Test
  void sourceLimitCountsFailuresAcrossAccountsAndExpires() {
    var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    var backoff = new LoginBackoff(settings(10_000, 5, 8), clock);
    String attacker = "203.0.113.10";

    for (int attempt = 0; attempt < 29; attempt++)
      backoff.failure("user" + attempt, attacker);
    assertThat(backoff.blocked("another-user", attacker)).isFalse();

    backoff.failure("last-user", attacker);
    assertThat(backoff.blocked("another-user", attacker)).isTrue();
    assertThat(backoff.blocked("another-user", "198.51.100.20")).isFalse();

    clock.advance(Duration.ofMinutes(5));
    assertThat(backoff.blocked("another-user", attacker)).isFalse();
    backoff.failure("new-user", attacker);
    assertThat(backoff.blocked("another-user", attacker)).isFalse();
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("UTC clock required");
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
