package com.example.hospital;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.getStatus();

import java.util.Map;
import org.junit.jupiter.api.Test;

class HospitalValidationTest extends HospitalSupport {
  @Test
  void validationAndUniqueIdentifiers() throws Exception {
    request(
            "admin",
            "POST",
            "/api/v1/patients",
            Map.of(
                "firstName",
                "",
                "lastName",
                "Bad",
                "patientIdentifier",
                "BAD",
                "dateOfBirth",
                "2999-01-01"))
        .andExpect(status().isBadRequest());
    var p = createPatient();
    request(
            "admin",
            "POST",
            "/api/v1/patients",
            Map.of(
                "firstName",
                "Duplicate",
                "lastName",
                "Person",
                "patientIdentifier",
                p.get("patientIdentifier").asText(),
                "dateOfBirth",
                "1980-01-01"))
        .andExpect(status().isConflict());
    request(
            "admin",
            "POST",
            "/api/v1/rooms",
            Map.of("roomNumber", "BAD", "bedCount", 0, "active", true))
        .andExpect(status().isBadRequest());
  }

  @Test
  void fractionalBedCountsCannotBeSilentlyTruncated() throws Exception {
    request(
            "admin",
            "POST",
            "/api/v1/rooms",
            Map.of("roomNumber", "FRACTIONAL", "bedCount", 1.5, "active", true))
        .andExpect(status().isBadRequest());
  }
}
