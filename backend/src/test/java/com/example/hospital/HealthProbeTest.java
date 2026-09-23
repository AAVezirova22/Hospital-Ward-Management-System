package com.example.hospital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class HealthProbeTest extends HospitalSupport {
  @Test
  void livenessAndReadinessArePublicAndSeparate() throws Exception {
    mvc.perform(get("/api/v1/health/live"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.database").doesNotExist());
    mvc.perform(get("/api/v1/health/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.database").value("UP"))
        .andExpect(jsonPath("$.migrations").value("UP"))
        .andExpect(jsonPath("$.version").doesNotExist());
    mvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database").value("UP"));
  }
}
