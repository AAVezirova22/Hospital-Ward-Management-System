package com.example.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.hospital.service.RateLimitService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiRateLimitsTest {
  private final ApiRateLimits limits =
      new ApiRateLimits(true, "60/1m", "30/10m", "300/1m", "off", "20/PT10M", mock(RateLimitService.class), new ClientAddressResolver());

  @Test
  void parsesCountPerWindowAndOff() {
    assertThat(ApiRateLimits.parse("x", "5/30s", true, "V")).isEqualTo(new ApiRateLimits.Policy("x", 5, Duration.ofSeconds(30), true));
    assertThat(ApiRateLimits.parse("x", " 2/1d ", false, "V").window()).isEqualTo(Duration.ofDays(1));
    assertThat(ApiRateLimits.parse("x", "20/PT10M", true, "V").window()).isEqualTo(Duration.ofMinutes(10));
    assertThat(ApiRateLimits.parse("x", "OFF", true, "V")).isNull();
    assertThat(limits.policy("reports")).as("off removes the policy").isNull();
  }

  @Test
  void rejectsMalformedOrUnboundedPolicies() {
    for (String bad : new String[] {"", "60", "0/1m", "60/0s", "60/2d", "sixty/1m", "60/1w"})
      assertThatThrownBy(() -> ApiRateLimits.parse("search", bad, true, "API_RATE_LIMIT_SEARCH"))
          .as(bad)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("API_RATE_LIMIT_SEARCH");
  }

  @Test
  void mapsRequestsToPolicies() {
    assertThat(limits.policyFor(request("POST", "/api/v1/auth/login"))).isEqualTo("auth");
    assertThat(limits.policyFor(request("POST", "/api/v1/registration/resend"))).isEqualTo("registration");
    assertThat(limits.policyFor(request("GET", "/api/v1/patients"))).isEqualTo("search");
    assertThat(limits.policyFor(request("GET", "/api/v1/reports/dashboard"))).isEqualTo("reports");
    assertThat(limits.policyFor(request("GET", "/api/v1/reports/procedures.csv"))).isEqualTo("exports");
    assertThat(limits.policyFor(request("GET", "/api/v1/patients/7"))).isNull();
    assertThat(limits.policyFor(request("GET", "/api/v1/auth/me"))).isNull();
    assertThat(limits.policyFor(request("POST", "/api/v1/patients"))).isNull();
  }

  private static MockHttpServletRequest request(String method, String uri) {
    var request = new MockHttpServletRequest(method, uri);
    request.setServletPath(uri);
    return request;
  }
}
