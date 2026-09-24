package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowDryRunTest extends HospitalSupport {
  private JsonNode dryRun(String who, Object plan) throws Exception {
    return result(request(who, "POST", "/api/v1/assistant/workflows/dry-run", Map.of("plan", plan)), 200);
  }

  private Map<String, Object> admitNewPatient(String identifier, long roomId) {
    long doctorId = users.findByUsername("doctor").orElseThrow().getDoctorId();
    return Map.of(
        "title", "Admit from referral",
        "steps", List.of(
            Map.of("key", "p1", "operation", "createPatient", "source", "referral.pdf",
                "fields", Map.of("patientIdentifier", identifier, "firstName", "Dry", "lastName", "Run",
                    "dateOfBirth", "1990-01-01")),
            Map.of("key", "a1", "operation", "admit", "source", "referral.pdf",
                "fields", Map.of("patientId", "$p1", "doctorId", doctorId, "roomId", roomId))));
  }

  @Test
  void validPlansReportTheirEffectWithoutSavingAnything() throws Exception {
    long roomId = room(2).get("id").asLong();
    String identifier = "DRY-" + unique();
    long actionsBefore = jdbc.queryForObject("select count(*) from ai_pending_actions", Long.class);

    var result = dryRun("admin", json.writeValueAsString(admitNewPatient(identifier, roomId)));

    assertThat(result.get("valid").asBoolean()).isTrue();
    assertThat(result.get("committed").asBoolean()).isFalse();
    assertThat(result.get("operations").get("createPatient").asInt()).isOne();
    var change = result.get("capacityChanges").get(0);
    assertThat(change.get("roomId").asLong()).isEqualTo(roomId);
    assertThat(change.get("occupiedBefore").asInt()).isZero();
    assertThat(change.get("occupiedAfter").asInt()).isOne();
    assertThat(jdbc.queryForObject("select count(*) from patients where patient_identifier = ?", Long.class, identifier)).isZero();
    assertThat(activeAssignments(roomId)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from ai_pending_actions", Long.class)).isEqualTo(actionsBefore);
  }

  @Test
  void plansThatWouldFailAreReportedWithTheReason() throws Exception {
    var room = room(1);
    admit(createPatient(), room);
    var full = dryRun("admin", admitNewPatient("DRY-" + unique(), room.get("id").asLong()));
    assertThat(full.get("valid").asBoolean()).isFalse();
    assertThat(full.get("code").asText()).isNotBlank();

    var malformed = dryRun("admin", "{\"title\":\"x\",\"steps\":[]}");
    assertThat(malformed.get("valid").asBoolean()).isFalse();
    assertThat(malformed.get("code").asText()).isEqualTo("INVALID_WORKFLOW");
  }

  @Test
  void doctorsCannotDryRunWriteWorkflows() throws Exception {
    var result = dryRun("doctor", admitNewPatient("DRY-" + unique(), room(1).get("id").asLong()));
    assertThat(result.get("valid").asBoolean()).isFalse();
    assertThat(result.get("code").asText()).isEqualTo("ACCESS_DENIED");
  }
}
