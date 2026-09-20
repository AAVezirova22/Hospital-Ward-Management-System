package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
    Integer count =
        jdbc.query(
            "select hit_count from rate_windows where rate_key=?",
            rs -> rs.next() ? rs.getInt(1) : null,
            key);
    if (count == null) {
      jdbc.update(
          "insert into rate_windows(rate_key, window_start, hit_count) values (?,?,1)",
          key,
          Timestamp.from(now));
      return;
    }
    if (count >= max) throw new ApiException(429, code, message);
    jdbc.update("update rate_windows set hit_count=hit_count+1 where rate_key=?", key);
  }
}
