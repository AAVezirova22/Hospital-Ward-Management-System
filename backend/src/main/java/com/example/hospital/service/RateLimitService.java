package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

  private final JdbcTemplate jdbc;

  public RateLimitService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Budget hit(String key, int max, Duration window, String code, String message) {
    Instant now = Instant.now();
    jdbc.update(
        "delete from rate_windows where window_start < ?", Timestamp.from(now.minus(window)));
    Window current =
        jdbc.query(
            "select hit_count, window_start from rate_windows where rate_key=?",
            rs -> rs.next() ? new Window(rs.getInt(1), rs.getTimestamp(2).toInstant()) : null,
            key);
    if (current == null) {
      jdbc.update(
          "insert into rate_windows(rate_key, window_start, hit_count) values (?,?,1)",
          key,
          Timestamp.from(now));
      return publish(new Budget(max, Math.max(0, max - 1), reset(now, window, now)));
    }
    long reset = reset(current.start(), window, now);
    if (current.count() >= max) {
      var budget = publish(new Budget(max, 0, reset));
      throw new ApiException(429, code, message)
          .withHeader(LIMIT, String.valueOf(budget.limit()))
          .withHeader(REMAINING, "0")
          .withHeader(RESET, String.valueOf(reset))
          .withHeader("Retry-After", String.valueOf(reset));
    }
    jdbc.update("update rate_windows set hit_count=hit_count+1 where rate_key=?", key);
    return publish(new Budget(max, Math.max(0, max - current.count() - 1), reset));
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
