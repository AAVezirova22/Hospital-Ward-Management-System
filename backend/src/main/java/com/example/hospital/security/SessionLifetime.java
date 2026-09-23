package com.example.hospital.security;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hard upper bound on an authenticated session, counted from sign-in. The servlet idle timeout
 * still applies; this stops an active session from living forever.
 */
@Component
public class SessionLifetime {
  static final String AUTHENTICATED_AT = "authenticatedAt";

  private final Duration max;

  public SessionLifetime(@Value("${app.session.max-lifetime:12h}") Duration max) {
    if (max.isNegative() || max.isZero())
      throw new IllegalStateException("SESSION_MAX_LIFETIME must be positive.");
    this.max = max;
  }

  public static void markAuthenticated(HttpSession session) {
    session.setAttribute(AUTHENTICATED_AT, Instant.now().toEpochMilli());
  }

  /** Sessions from before this setting existed fall back to their creation time. */
  public boolean expired(HttpSession session) {
    Object at = session.getAttribute(AUTHENTICATED_AT);
    long start = at instanceof Long millis ? millis : session.getCreationTime();
    return Instant.ofEpochMilli(start).plus(max).isBefore(Instant.now());
  }
}
