package com.example.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginBackoffTest {
  @Test
  void accountLockIsSourceScopedAndDoesNotRearmFromOneFailureAfterExpiry() {
    var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    var backoff = new LoginBackoff(clock);
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
    var backoff = new LoginBackoff(clock);
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

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
