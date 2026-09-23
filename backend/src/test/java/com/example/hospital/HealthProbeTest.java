package com.example.hospital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;

class HealthProbeTest extends HospitalSupport {
  @Autowired ApplicationContext context;

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

  @Test
  void readinessFailsWhileRefusingTrafficButLivenessStaysUp() throws Exception {
    AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
    try {
      mvc.perform(get("/api/v1/health/ready"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.traffic").value("REFUSING"))
          .andExpect(jsonPath("$.status").value("DOWN"));
      mvc.perform(get("/api/v1/health/live")).andExpect(status().isOk());
    } finally {
      AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }
  }
}
