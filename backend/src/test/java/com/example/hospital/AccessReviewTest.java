package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccessReviewTest extends HospitalSupport {
  private static final String REVIEW = "/api/v1/workspaces/hospitals/1/access-review";

  private JsonNode member(JsonNode report, String username) {
    for (var member : report.get("members"))
      if (member.get("username").asText().equals(username)) return member;
    throw new AssertionError(username + " missing from the access review");
  }

  @Test
  void ownersSeeEveryWorkforceMemberWithoutClinicalOrSecretFields() throws Exception {
    createPatient();
    var report = result(request("admin", "GET", REVIEW, null), 200);

    var doctor = member(report, "doctor");
    assertThat(doctor.get("departments").get(0).get("role").asText()).isEqualTo("DOCTOR");
    assertThat(doctor.get("departments").get(0).get("doctorName").asText()).isNotBlank();
    assertThat(member(report, "staff").get("departments").get(0).get("role").asText())
        .isEqualTo("MEDICAL_STAFF");
    assertThat(member(report, "admin").get("owner").asBoolean()).isTrue();
    assertThat(member(report, "admin").get("lastLoginAt").isNull()
            || !member(report, "admin").get("lastLoginAt").asText().isBlank())
        .isTrue();
    assertThat(report.get("outcomes").has("UNREVIEWED")).isTrue();

    String body = report.toString();
    assertThat(body)
        .doesNotContain("passwordHash", "sessionStamp", "patientIdentifier", "dateOfBirth", "PATIENT\"");
  }

  @Test
  void onlyOwnersCanOpenOrRecordReviews() throws Exception {
    request("staff", "GET", REVIEW, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("HOSPITAL_OWNER_REQUIRED"));
    long staffId = users.findByUsername("staff").orElseThrow().getId();
    request("staff", "POST", REVIEW + "/" + staffId, Map.of("outcome", "KEEP"))
        .andExpect(status().isForbidden());
  }

  @Test
  void decisionsAreStoredAuditedAndShownAsTheLatestOutcome() throws Exception {
    long staffId = users.findByUsername("staff").orElseThrow().getId();
    request("admin", "POST", REVIEW + "/" + staffId, Map.of("outcome", "change", "note", "Role too broad"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("CHANGE"));
    request("admin", "POST", REVIEW + "/" + staffId, Map.of("outcome", "KEEP"))
        .andExpect(status().isOk());

    var latest = member(result(request("admin", "GET", REVIEW, null), 200), "staff").get("latestReview");
    assertThat(latest.get("outcome").asText()).isEqualTo("KEEP");
    assertThat(latest.get("reviewedBy").asText()).isEqualTo("admin");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from access_reviews where hospital_id = 1 and user_id = ?", Long.class, staffId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where event_type = 'ACCESS_REVIEWED' and entity_id = ?",
                Long.class,
                staffId))
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void invalidDecisionsAndNonMembersAreRejected() throws Exception {
    long staffId = users.findByUsername("staff").orElseThrow().getId();
    request("admin", "POST", REVIEW + "/" + staffId, Map.of("outcome", "MAYBE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_OUTCOME"));
    request("admin", "POST", REVIEW + "/999999999", Map.of("outcome", "KEEP"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
    request("admin", "GET", REVIEW + "?inactiveAfterDays=0", null).andExpect(status().isBadRequest());
  }
}
