package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.getStatus();

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HospitalAccessTest extends HospitalSupport {
  @Test
  void doctorsSeeOnlyAssignedPatientsAndAdmissions() throws Exception {
    var p = createPatient();
    var r = room(2);
    var a =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/admissions",
                Map.of(
                    "patientId",
                    p.get("id").asLong(),
                    "doctorId",
                    2,
                    "roomId",
                    r.get("id").asLong())),
            201);
    request("doctor", "GET", "/api/v1/patients/" + p.get("id").asLong(), null)
        .andExpect(status().isForbidden());
    request("doctor", "GET", "/api/v1/admissions/" + a.get("id").asLong(), null)
        .andExpect(status().isForbidden());
    var search =
        result(
            request(
                "doctor", "GET", "/api/v1/patients?q=" + p.get("patientIdentifier").asText(), null),
            200);
    assertThat(search).isEmpty();
    request(
            "doctor",
            "GET",
            "/api/v1/reports/procedures?from=2020-01-01&to=2030-01-01&patientId="
                + p.get("id").asLong(),
            null)
        .andExpect(status().isForbidden());
  }

  @Test
  void staffAndDoctorsCannotAdministerUsersOrCatalogue() throws Exception {
    for (String who : List.of("staff", "doctor")) {
      request(who, "GET", "/api/v1/users", null).andExpect(status().isForbidden());
      request(
              who,
              "POST",
              "/api/v1/rooms",
              Map.of("roomNumber", "BAD", "bedCount", 1, "active", true))
          .andExpect(status().isForbidden());
    }
    request(
            "doctor",
            "POST",
            "/api/v1/patients",
            Map.of(
                "patientIdentifier",
                "X",
                "firstName",
                "A",
                "lastName",
                "B",
                "dateOfBirth",
                "1980-01-01"))
        .andExpect(status().isForbidden());
  }
}
