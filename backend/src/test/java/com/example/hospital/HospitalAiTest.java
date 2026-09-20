package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.getStatus();

import com.example.hospital.repository.AiPendingActionRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HospitalAiTest extends HospitalSupport {
  @Autowired AiPendingActionRepository actions;

  @Test
  void aiPreparationDoesNotMutateUntilOwnerConfirms() throws Exception {
    var p = createPatient();
    var source = room(1);
    var dest = room(1);
    var a = admit(p, source);
    var res =
        ai("admin", "Move him to room " + dest.get("roomNumber").asText(), p.get("id").asLong());
    assertThat(res.get("responseType").asText()).isEqualTo("CONFIRMATION_CARD");
    long actionId = res.path("data").path("action").path("id").asLong();
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(source.get("id").asLong())).isOne();
    request("staff", "POST", "/api/v1/ai-actions/" + actionId + "/confirm", null)
        .andExpect(status().isForbidden());
    request("admin", "POST", "/api/v1/ai-actions/" + actionId + "/confirm", null)
        .andExpect(status().isOk());
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(dest.get("id").asLong())).isOne();
    request("admin", "POST", "/api/v1/ai-actions/" + actionId + "/confirm", null)
        .andExpect(status().isConflict());
  }

  @Test
  void staleCapacityAtAiConfirmationIsRejected() throws Exception {
    var p = createPatient();
    admit(p, room(1));
    var dest = room(1);
    var res =
        ai("admin", "Move him to room " + dest.get("roomNumber").asText(), p.get("id").asLong());
    long id = res.path("data").path("action").path("id").asLong();
    admit(createPatient(), dest);
    request("admin", "POST", "/api/v1/ai-actions/" + id + "/confirm", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"));
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(dest.get("id").asLong())).isOne();
  }

  @Test
  void expiredAndCancelledActionsCannotExecute() throws Exception {
    var p = createPatient();
    admit(p, room(1));
    var res = ai("admin", "discharge him", p.get("id").asLong());
    long id = res.path("data").path("action").path("id").asLong();
    var action = actions.findById(id).orElseThrow();
    action.setExpiresAt(Instant.now().minusSeconds(10));
    actions.save(action);
    request("admin", "POST", "/api/v1/ai-actions/" + id + "/confirm", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACTION_EXPIRED"));
    assertThat(actions.findById(id).orElseThrow().getStatus()).isEqualTo("EXPIRED");
    res = ai("admin", "discharge him", p.get("id").asLong());
    id = res.path("data").path("action").path("id").asLong();
    request("admin", "POST", "/api/v1/ai-actions/" + id + "/cancel", null)
        .andExpect(status().isOk());
    request("admin", "POST", "/api/v1/ai-actions/" + id + "/confirm", null)
        .andExpect(status().isConflict());
    assertThat(admissions.findByPatientIdAndStatus(p.get("id").asLong(), "ACTIVE")).isPresent();
  }

  @Test
  void aiSessionsArePrivateAndWriteToolsRespectRoles() throws Exception {
    var p = createPatient();
    admit(p, room(1));
    var res = ai("admin", "Find " + p.get("patientIdentifier").asText(), null);
    String key = res.get("sessionId").asText();
    request("staff", "GET", "/api/v1/assistant/sessions/" + key, null)
        .andExpect(status().isForbidden());
    request(
            "staff",
            "POST",
            "/api/v1/assistant/messages",
            Map.of("message", "show department status", "sessionId", key))
        .andExpect(status().isForbidden());
    request(
            "doctor",
            "POST",
            "/api/v1/assistant/messages",
            Map.of("message", "discharge him", "selectedPatientId", p.get("id").asLong()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aiAdmissionIsOnlyCreatedAfterConfirmation() throws Exception {
    var p = createPatient();
    var r = room(1);
    var res =
        ai(
            "admin",
            "Admit "
                + p.get("firstName").asText()
                + " Patient doctor Dimitrova room "
                + r.get("roomNumber").asText(),
            null);
    assertThat(res.get("responseType").asText()).isEqualTo("CONFIRMATION_CARD");
    assertThat(admissions.findByPatientIdAndStatus(p.get("id").asLong(), "ACTIVE")).isEmpty();
    long id = res.path("data").path("action").path("id").asLong();
    request("admin", "POST", "/api/v1/ai-actions/" + id + "/confirm", null)
        .andExpect(status().isOk());
    assertThat(admissions.findByPatientIdAndStatus(p.get("id").asLong(), "ACTIVE")).isPresent();
  }

  @Test
  void manualTransferMakesPreviousAiProposalStale() throws Exception {
    var p = createPatient();
    var source = room(1);
    var dest = room(1);
    var other = room(1);
    var a = admit(p, source);
    var res =
        ai("admin", "Move him to room " + dest.get("roomNumber").asText(), p.get("id").asLong());
    long id = res.path("data").path("action").path("id").asLong();
    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + a.get("id").asLong() + "/transfer",
            Map.of("roomId", other.get("id").asLong(), "reason", "Changed placement", "version", 0))
        .andExpect(status().isOk());
    request("admin", "POST", "/api/v1/ai-actions/" + id + "/confirm", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_STATE"));
    assertThat(assignments.countByRoomIdAndReleasedAtIsNull(dest.get("id").asLong())).isZero();
  }

  @Test
  void roleChangeBetweenProposalAndConfirmationIsRechecked() throws Exception {
    String name = "r" + unique();
    var user =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/users",
                Map.of(
                    "username",
                    name,
                    "password",
                    "RoleTestPassword123!",
                    "role",
                    "MEDICAL_STAFF",
                    "enabled",
                    true)),
            201);
    var p = createPatient();
    admit(p, room(1));
    var res = ai(name, "discharge him", p.get("id").asLong());
    long id = res.path("data").path("action").path("id").asLong();
    request(
            "admin",
            "PUT",
            "/api/v1/users/" + user.get("id").asLong(),
            Map.of(
                "username", name, "role", "DOCTOR", "doctorId", 1, "enabled", true, "version", 0))
        .andExpect(status().isOk());
    request(name, "POST", "/api/v1/ai-actions/" + id + "/confirm", null)
        .andExpect(status().isForbidden());
    assertThat(admissions.findByPatientIdAndStatus(p.get("id").asLong(), "ACTIVE")).isPresent();
  }
}
