package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AiSettingsTest extends HospitalSupport {
  @Test
  void administratorsSeeConfigurationAndTestsAreAudited() throws Exception {
    var configuration = result(request("admin", "GET", "/api/v1/settings/ai", null), 200);
    assertThat(configuration.get("mode").asText()).isEqualTo("local");
    assertThat(configuration.has("apiKeyConfigured")).isTrue();

    var test = result(request("admin", "POST", "/api/v1/settings/ai/test", null), 200);
    assertThat(test.get("outcome").asText()).isEqualTo("NOT_EXTERNAL");
    assertThat(jdbc.queryForObject(
            "select count(*) from audit_events where event_type = 'AI_PROVIDER_TESTED'", Long.class))
        .isPositive();
  }

  @Test
  void otherRolesCannotUseTheCheck() throws Exception {
    request("staff", "GET", "/api/v1/settings/ai", null).andExpect(status().isForbidden());
    request("doctor", "POST", "/api/v1/settings/ai/test", null).andExpect(status().isForbidden());
  }
}
