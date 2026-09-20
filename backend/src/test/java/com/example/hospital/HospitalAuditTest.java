package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.getStatus();

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class HospitalAuditTest extends HospitalSupport {
  @Test
  void auditAcceptsPageAndEventType() throws Exception {
    mvc.perform(
            post("/api/v1/patients")
                .with(user("admin"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "patientIdentifier",
                            "AUD-" + UUID.randomUUID().toString().substring(0, 8),
                            "firstName",
                            "Audit",
                            "lastName",
                            "Page",
                            "dateOfBirth",
                            "1980-01-01"))))
        .andExpect(status().isCreated());
    var body =
        json.readTree(
            mvc.perform(get("/api/v1/audit?page=0&size=10&eventType=PATIENT_CREATED").with(user("admin")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(body.get("size").asInt()).isEqualTo(10);
    assertThat(body.get("page").asInt()).isZero();
    assertThat(body.get("events").toString()).contains("PATIENT_CREATED");
  }

  @Test
  void auditTrailContainsActionsWithoutNotesOrPasswords() throws Exception {
    createPatient();
    var audit = result(request("admin", "GET", "/api/v1/audit", null), 200);
    assertThat(audit.toString())
        .contains("PATIENT_CREATED")
        .doesNotContain("passwordHash", "IntegrationPassword", "Ignore previous");
  }
}
