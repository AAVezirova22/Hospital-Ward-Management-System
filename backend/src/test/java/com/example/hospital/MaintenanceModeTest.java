package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hospital.security.MaintenanceMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "app.maintenance.enabled=true",
      "app.maintenance.message=Database upgrade in progress until 22:00.",
      "app.maintenance.retry-after-seconds=300",
      "app.maintenance.bypass-token=operator-bypass-0123456789abcdef"
    })
class MaintenanceModeTest extends HospitalSupport {
  private static final String TOKEN = "operator-bypass-0123456789abcdef";

  @Test
  void apiAnswersMaintenanceWithRetryHintWhileHealthAndStatusStayOpen() throws Exception {
    mvc.perform(get("/api/v1/patients").with(user("admin").roles("ADMIN")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "300"))
        .andExpect(jsonPath("$.code").value("MAINTENANCE"))
        .andExpect(jsonPath("$.message").value("Database upgrade in progress until 22:00."))
        .andExpect(jsonPath("$.retryAfterSeconds").value(300))
        .andExpect(jsonPath("$.path").value("/api/v1/patients"));
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"irrelevant\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("MAINTENANCE"));

    mvc.perform(get("/api/v1/health/live")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/maintenance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.message").value("Database upgrade in progress until 22:00."))
        .andExpect(jsonPath("$.retryAfterSeconds").value(300));
  }

  @Test
  void operatorsWithTheBypassTokenCanVerifyTheRelease() throws Exception {
    mvc.perform(get("/api/v1/patients").with(user("admin").roles("ADMIN")).header(MaintenanceMode.BYPASS_HEADER, TOKEN))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/patients")
                .with(user("admin").roles("ADMIN"))
                .header(MaintenanceMode.BYPASS_HEADER, TOKEN.substring(1) + "x"))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(get("/api/v1/patients").header(MaintenanceMode.BYPASS_HEADER, TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void inactiveStatusRevealsNothingAndUnsafeSettingsAreRefused() {
    assertThat(new MaintenanceMode(false, "hidden", 120, "").status()).containsOnlyKeys("active");
    assertThatThrownBy(() -> new MaintenanceMode(true, "", 120, "short-token"))
        .hasMessageContaining("MAINTENANCE_BYPASS_TOKEN");
    assertThatThrownBy(() -> new MaintenanceMode(true, "", 0, ""))
        .hasMessageContaining("MAINTENANCE_RETRY_AFTER_SECONDS");
  }
}
