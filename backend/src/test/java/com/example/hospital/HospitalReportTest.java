package com.example.hospital;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.getStatus();

import org.junit.jupiter.api.Test;

class HospitalReportTest extends HospitalSupport {
  @Test
  void reportsRejectInvertedPeriodsAndCoverCapacityCensus() throws Exception {
    request("admin", "GET", "/api/v1/reports/procedures?from=2030-01-01&to=2020-01-01", null)
        .andExpect(status().isBadRequest());
    request("admin", "GET", "/api/v1/reports/capacity", null).andExpect(status().isOk());
    request("admin", "GET", "/api/v1/reports/census?doctorId=1", null).andExpect(status().isOk());
    request("admin", "GET", "/api/v1/reports/dashboard", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableBeds").isNumber());
  }
}
