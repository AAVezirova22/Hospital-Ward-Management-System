package com.example.hospital;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ConnectionPoolMetricsTest extends HospitalSupport {
  @Test
  void poolSaturationMetricsAreAvailableToAdministratorsOnly() throws Exception {
    for (String metric :
        new String[] {
          "hikaricp.connections.active",
          "hikaricp.connections.pending",
          "hikaricp.connections.max",
          "hikaricp.connections.timeout"
        }) {
      mvc.perform(
              get("/api/v1/management/metrics/" + metric)
                  .param("tag", "pool:hospital-db")
                  .with(user("admin").roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value(metric));
    }
    mvc.perform(
            get("/api/v1/management/metrics/hikaricp.connections.max")
                .with(user("staff").roles("MEDICAL_STAFF")))
        .andExpect(status().isForbidden());
  }
}
