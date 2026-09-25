package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Fixed-window limits shared through the database. Every checked request carries
 * {@code RateLimit-Limit}, {@code RateLimit-Remaining} and {@code RateLimit-Reset} (seconds until
 * the window resets); a rejected request is a 429 that also carries {@code Retry-After}.
 */
@Service
public class RateLimitService {
  public static final String LIMIT = "RateLimit-Limit";
  public static final String REMAINING = "RateLimit-Remaining";
  public static final String RESET = "RateLimit-Reset";

  /** Budget left in the current window after this request. */
  public record Budget(int limit, int remaining, long resetSeconds) {}

  private record Window(int count, Instant start) {}

  /** Longest supported window; stale rows older than this are removed on every hit. */
  public static final Duration MAX_WINDOW = Duration.ofDays(1);

  private final JdbcTemplate jdbc;

  public RateLimitService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Budget hit(String key, int max, Duration window, String code, String message) {
    if (window.isNegative() || window.isZero() || window.compareTo(MAX_WINDOW) > 0)
      throw new IllegalArgumentException("Rate-limit windows must be between 1 ms and 1 day.");
    Instant now = Instant.now();
    Timestamp timestamp = Timestamp.from(now);
    Timestamp cutoff = Timestamp.from(now.minus(window));
    // Housekeeping only. Each key resets its own expired window in the upsert below, so removing
    // rows by this call's window would cut short other keys that use longer windows.
    jdbc.update("delete from rate_windows where window_start < ?", Timestamp.from(now.minus(MAX_WINDOW)));
    // One atomic upsert: starts a new window, counts the hit, or matches nothing when the budget
    // of the current window is spent. Concurrent requests cannot both take the last slot.
    List<Window> accepted =
        jdbc.query(
            "insert into rate_windows(rate_key, window_start, hit_count) values (?,?,1) "
                + "on conflict (rate_key) do update set "
                + "window_start = case when rate_windows.window_start < ? "
                + "then excluded.window_start else rate_windows.window_start end, "
                + "hit_count = case when rate_windows.window_start < ? "
                + "then excluded.hit_count else rate_windows.hit_count + 1 end "
                + "where rate_windows.window_start < ? or rate_windows.hit_count < ? "
                + "returning hit_count, window_start",
            (rs, row) ->
                new Window(rs.getInt("hit_count"), rs.getTimestamp("window_start").toInstant()),
            key,
            timestamp,
            cutoff,
            cutoff,
            cutoff,
            max);
    if (accepted.isEmpty()) {
      Instant start =
          jdbc.queryForObject(
              "select window_start from rate_windows where rate_key=?",
              (rs, row) -> rs.getTimestamp(1).toInstant(),
              key);
      long reset = reset(start, window, now);
      publish(new Budget(max, 0, reset));
      throw new ApiException(429, code, message)
          .withHeader(LIMIT, String.valueOf(max))
          .withHeader(REMAINING, "0")
          .withHeader(RESET, String.valueOf(reset))
          .withHeader("Retry-After", String.valueOf(reset));
    }
    Window current = accepted.getFirst();
    return publish(
        new Budget(max, Math.max(0, max - current.count()), reset(current.start(), window, now)));
  }

  private static long reset(Instant start, Duration window, Instant now) {
    long millis = Duration.between(now, start.plus(window)).toMillis();
    return Math.max(1, (millis + 999) / 1000);
  }

  private static Budget publish(Budget budget) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      HttpServletResponse response = attrs.getResponse();
      if (response != null && !response.isCommitted()) {
        response.setHeader(LIMIT, String.valueOf(budget.limit()));
        response.setHeader(REMAINING, String.valueOf(budget.remaining()));
        response.setHeader(RESET, String.valueOf(budget.resetSeconds()));
      }
    }
    return budget;
  }
}
