package com.example.hospital.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.login-backoff")
public record LoginBackoffProperties(
    int maxEntries,
    int maxUsernameLength,
    int firstLockThreshold,
    Duration firstLockDuration,
    int extendedLockThreshold,
    Duration extendedLockDuration,
    Duration idleTtl) {
  public LoginBackoffProperties {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
    if (maxUsernameLength < 1)
      throw new IllegalArgumentException("maxUsernameLength must be positive");
    if (firstLockThreshold < 1)
      throw new IllegalArgumentException("firstLockThreshold must be positive");
    if (extendedLockThreshold <= firstLockThreshold)
      throw new IllegalArgumentException("extendedLockThreshold must exceed firstLockThreshold");
    requirePositive(firstLockDuration, "firstLockDuration");
    requirePositive(extendedLockDuration, "extendedLockDuration");
    requirePositive(idleTtl, "idleTtl");
  }

  private static void requirePositive(Duration duration, String name) {
    if (duration == null || duration.isZero() || duration.isNegative())
      throw new IllegalArgumentException(name + " must be positive");
  }
}
