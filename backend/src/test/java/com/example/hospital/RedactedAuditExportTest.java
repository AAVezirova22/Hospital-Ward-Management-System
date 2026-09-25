package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedactedAuditExportTest extends HospitalSupport {
  private static final String EXPORT = "/api/v1/audit/export.csv?eventType=PATIENT_CREATED&limit=50";

  @Test
  void redactedProfilePseudonymisesActorsAndDropsIdentifiersAndMetadata() throws Exception {
    var byAdmin = createPatient();
    var byStaff =
        result(
            request("staff", "POST", "/api/v1/patients",
                Map.of("patientIdentifier", "RED-" + unique(), "firstName", "Red", "lastName", "Acted",
                    "dateOfBirth", "1970-02-03")),
            201);
    long adminId = users.findByUsername("admin").orElseThrow().getId();
    long staffId = users.findByUsername("staff").orElseThrow().getId();

    var response =
        mvc.perform(get(EXPORT + "&profile=redacted").with(user("admin")))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Audit-Export-Profile", "redacted"))
            .andExpect(header().string("X-Redacted-Fields", "actorId,entityId,metadata"))
            .andReturn()
            .getResponse();
    List<String> lines = Arrays.asList(response.getContentAsString().split("\r\n"));
    assertThat(lines.getFirst()).isEqualTo("Department ID,Audit ID,Actor,Event type,Entity type,Source,Timestamp");
    List<String> actors = lines.stream().skip(1).map(line -> line.split(",")[2]).distinct().toList();
    assertThat(actors).allMatch(actor -> actor.matches("A\\d+")).contains("A1", "A2");
    String body = response.getContentAsString();
    assertThat(body)
        .doesNotContain(byAdmin.get("id").asText() + ",")
        .doesNotContain("id=" + byStaff.get("id").asText())
        .doesNotContain("{event=")
        .doesNotContain("," + adminId + ",")
        .doesNotContain("," + staffId + ",");

    var exportAudit =
        result(request("admin", "GET", "/api/v1/audit?eventType=DATA_EXPORTED&page=0&size=1", null), 200);
    assertThat(exportAudit.path("events").get(0).path("metadata").asText())
        .contains("profile=redacted", "removedFields=actorId+entityId+metadata");
  }

  @Test
  void fullRemainsTheDefaultAndUnknownProfilesAreRejected() throws Exception {
    createPatient();
    mvc.perform(get(EXPORT).with(user("admin")))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Audit-Export-Profile", "full"));
    request("admin", "GET", EXPORT + "&profile=anonymous", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PROFILE"));
  }
}
