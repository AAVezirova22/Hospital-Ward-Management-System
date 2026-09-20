package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "app.seed=true",
      "app.bootstrap-password=IntegrationPassword123!",
      "server.servlet.session.cookie.secure=false"
    })
@AutoConfigureMockMvc
class HospitalAuditTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    HospitalIntegrationTest.database(r);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

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
}
