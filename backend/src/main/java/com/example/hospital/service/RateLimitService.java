package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitService {
  private final JdbcTemplate jdbc;

  public RateLimitService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void hit(String key, int max, Duration window, String code, String message) {
    Instant now = Instant.now();
    jdbc.update(
        "delete from rate_windows where window_start < ?", Timestamp.from(now.minus(window)));
    Timestamp timestamp = Timestamp.from(now);
    Timestamp cutoff = Timestamp.from(now.minus(window));
    List<Integer> acceptedHits =
        jdbc.query(
            "insert into rate_windows(rate_key, window_start, hit_count) values (?,?,1) "
                + "on conflict (rate_key) do update set "
                + "window_start = case when rate_windows.window_start < ? "
                + "then excluded.window_start else rate_windows.window_start end, "
                + "hit_count = case when rate_windows.window_start < ? "
                + "then excluded.hit_count else rate_windows.hit_count + 1 end "
                + "where rate_windows.window_start < ? or rate_windows.hit_count < ? "
                + "returning hit_count",
            (rs, row) -> rs.getInt(1),
            key,
            timestamp,
            cutoff,
            cutoff,
            cutoff,
            max);
    if (acceptedHits.isEmpty()) throw new ApiException(429, code, message);
  }
}
