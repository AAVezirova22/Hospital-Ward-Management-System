package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.hospital.api.ApiException;
import com.example.hospital.service.RateLimitService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RateLimitServiceConcurrencyTest extends HospitalSupport {
  @Autowired RateLimitService rates;
  @Autowired JdbcTemplate jdbc;

  @Test
  void concurrentFirstHitsCreateOneWindowAndCountEveryAcceptedRequest() throws Exception {
    String key = "issue-132:first:" + UUID.randomUUID();
    int concurrentHits = 24;

    List<Boolean> accepted = hitTogether(key, concurrentHits, concurrentHits);

    assertThat(accepted).containsOnly(true);
    assertThat(windowRows(key)).isOne();
    assertThat(hitCount(key)).isEqualTo(concurrentHits);
  }

  @Test
  void concurrentHitsAtTheLimitAcceptOnlyTheRemainingCapacity() throws Exception {
    String key = "issue-132:limit:" + UUID.randomUUID();
    int max = 10;
    int concurrentHits = 24;
    for (int hit = 0; hit < max - 1; hit++) hit(key, max);

    List<Boolean> accepted = hitTogether(key, max, concurrentHits);

    assertThat(accepted.stream().filter(Boolean::booleanValue).count()).isOne();
    assertThat(accepted.stream().filter(result -> !result).count())
        .isEqualTo(concurrentHits - 1);
    assertThat(windowRows(key)).isOne();
    assertThat(hitCount(key)).isEqualTo(max);
  }

  private List<Boolean> hitTogether(String key, int max, int requestCount) throws Exception {
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var ready = new CountDownLatch(requestCount);
      var start = new CountDownLatch(1);
      List<Future<Boolean>> tasks = new ArrayList<>();
      for (int request = 0; request < requestCount; request++)
        tasks.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Timed out waiting for concurrent rate-limit hits.");
                  return hit(key, max);
                }));

      boolean allReady;
      try {
        allReady = ready.await(10, TimeUnit.SECONDS);
      } finally {
        start.countDown();
      }
      assertThat(allReady).isTrue();

      List<Boolean> accepted = new ArrayList<>();
      for (var task : tasks) accepted.add(task.get(20, TimeUnit.SECONDS));
      return accepted;
    }
  }

  private boolean hit(String key, int max) {
    try {
      rates.hit(key, max, Duration.ofMinutes(1), "TEST_RATE_LIMIT", "Wait before retrying.");
      return true;
    } catch (ApiException exception) {
      assertThat(exception.getStatus()).isEqualTo(429);
      return false;
    }
  }

  private long windowRows(String key) {
    return jdbc.queryForObject(
        "select count(*) from rate_windows where rate_key = ?", Long.class, key);
  }

  private int hitCount(String key) {
    return jdbc.queryForObject(
        "select hit_count from rate_windows where rate_key = ?", Integer.class, key);
  }
}
