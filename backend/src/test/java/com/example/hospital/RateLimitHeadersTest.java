package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.hospital.api.ApiException;
import com.example.hospital.service.RateLimitService;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitHeadersTest extends HospitalSupport {
  @Autowired RateLimitService rates;

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
  }

  private MockHttpServletResponse bind() {
    var response = new MockHttpServletResponse();
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest(), response));
    return response;
  }

  @Test
  void acceptedRequestsReportRemainingBudget() {
    String key = "test:" + unique();
    var first = bind();
    var budget = rates.hit(key, 2, Duration.ofMinutes(1), "RATE_LIMITED", "Slow down.");
    assertThat(budget.remaining()).isEqualTo(1);
    assertThat(first.getHeader("RateLimit-Limit")).isEqualTo("2");
    assertThat(first.getHeader("RateLimit-Remaining")).isEqualTo("1");
    assertThat(Long.parseLong(first.getHeader("RateLimit-Reset"))).isBetween(1L, 60L);

    var second = bind();
    rates.hit(key, 2, Duration.ofMinutes(1), "RATE_LIMITED", "Slow down.");
    assertThat(second.getHeader("RateLimit-Remaining")).isEqualTo("0");
  }

  @Test
  void rejectedRequestsCarryRetryAfter() {
    String key = "test:" + unique();
    bind();
    rates.hit(key, 1, Duration.ofMinutes(1), "RATE_LIMITED", "Slow down.");
    bind();
    var rejected =
        catchThrowableOfType(
            ApiException.class,
            () -> rates.hit(key, 1, Duration.ofMinutes(1), "RATE_LIMITED", "Slow down."));
    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.headers())
        .containsEntry("RateLimit-Limit", "1")
        .containsEntry("RateLimit-Remaining", "0")
        .containsKey("Retry-After");
    assertThat(Long.parseLong(rejected.headers().get("Retry-After"))).isBetween(1L, 60L);
  }

  @Test
  void shortWindowsDoNotEraseLongerWindowsOfOtherKeys() {
    String longKey = "test-long:" + unique();
    rates.hit(longKey, 1, Duration.ofMinutes(10), "RATE_LIMITED", "Slow down.");
    jdbc.update(
        "update rate_windows set window_start = now() - interval '2 minutes' where rate_key = ?", longKey);

    rates.hit("test-short:" + unique(), 5, Duration.ofMinutes(1), "RATE_LIMITED", "Slow down.");

    var rejected =
        catchThrowableOfType(
            ApiException.class,
            () -> rates.hit(longKey, 1, Duration.ofMinutes(10), "RATE_LIMITED", "Slow down."));
    assertThat(rejected).as("the 10 minute window must still be in force").isNotNull();
    assertThat(rejected.getStatus()).isEqualTo(429);
  }
}
