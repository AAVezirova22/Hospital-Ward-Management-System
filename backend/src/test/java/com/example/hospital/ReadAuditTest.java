package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ReadAuditTest extends HospitalSupport {
  private long views(String event, long entityId) {
    return jdbc.queryForObject(
        "select count(*) from audit_events where event_type = ? and entity_id = ?",
        Long.class,
        event,
        entityId);
  }

  @Test
  void openingAPatientRecordIsAuditedOncePerUserAndWindow() throws Exception {
    var patient = createPatient();
    long id = patient.get("id").asLong();

    request("admin", "GET", "/api/v1/patients/" + id, null).andExpect(status().isOk());
    request("admin", "GET", "/api/v1/patients/" + id, null).andExpect(status().isOk());
    assertThat(views("PATIENT_VIEWED", id)).isOne();

    request("staff", "GET", "/api/v1/patients/" + id, null).andExpect(status().isOk());
    assertThat(views("PATIENT_VIEWED", id)).isEqualTo(2);

    var stored =
        jdbc.queryForList(
            "select source, metadata, user_id from audit_events where event_type = 'PATIENT_VIEWED' and entity_id = ?",
            id);
    assertThat(stored).allSatisfy(row -> {
      assertThat(row.get("source")).isEqualTo("UI");
      assertThat(row.get("user_id")).isNotNull();
      assertThat(row.get("metadata").toString())
          .doesNotContain(patient.get("firstName").asText())
          .doesNotContain(patient.get("patientIdentifier").asText());
    });
  }

  @Test
  void openingAnAdmissionIsAuditedButListsAndSearchesAreNot() throws Exception {
    var patient = createPatient();
    long patientId = patient.get("id").asLong();
    long admissionId = admit(patient, room(1)).get("id").asLong();

    request("admin", "GET", "/api/v1/patients?q=" + patient.get("patientIdentifier").asText(), null)
        .andExpect(status().isOk());
    request("admin", "GET", "/api/v1/admissions", null).andExpect(status().isOk());
    assertThat(views("PATIENT_VIEWED", patientId)).isZero();
    assertThat(views("ADMISSION_VIEWED", admissionId)).isZero();

    request("doctor", "GET", "/api/v1/admissions/" + admissionId, null).andExpect(status().isOk());
    assertThat(views("ADMISSION_VIEWED", admissionId)).isOne();
  }

  @Test
  void deniedReadsAreNotRecordedAsViews() throws Exception {
    request("admin", "GET", "/api/v1/patients/999999999", null).andExpect(status().isNotFound());
    assertThat(views("PATIENT_VIEWED", 999999999L)).isZero();
  }
}
