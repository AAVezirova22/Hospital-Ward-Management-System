package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ExportAuditTest extends HospitalSupport {
  @Test
  void csvExportRecordsScopeButNotTheExportedRows() throws Exception {
    request("admin", "GET", "/api/v1/reports/procedures.csv?from=2021-02-03&to=2021-02-04&doctorId=987654", null)
        .andExpect(status().isOk());

    var events =
        result(request("admin", "GET", "/api/v1/audit?page=0&size=5&eventType=DATA_EXPORTED", null), 200)
            .get("events");
    assertThat(events.size()).isPositive();
    String latest = events.get(0).toString();
    assertThat(latest)
        .contains("procedures.csv")
        .contains("2021-02-03")
        .contains("2021-02-04")
        .contains("987654")
        .contains("rows=0")
        .doesNotContain("Cost EUR");
  }
}
