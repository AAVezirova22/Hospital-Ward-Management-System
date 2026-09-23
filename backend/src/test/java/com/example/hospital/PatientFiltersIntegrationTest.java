package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PatientFiltersIntegrationTest extends HospitalSupport {
  @Test
  void searchAndOperationalFiltersComposeAndFollowTheCurrentRoom() throws Exception {
    var patient = createPatient();
    String search = patient.get("patientIdentifier").asText();
    var firstRoom = room(2);
    var nextRoom = room(2);
    var doctors = result(request("admin", "GET", "/api/v1/doctors", null), 200).get("items");
    long attendingDoctorId = doctors.get(0).get("id").asLong();
    var admission =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/admissions",
                Map.of(
                    "patientId",
                    patient.get("id").asLong(),
                    "doctorId",
                    attendingDoctorId,
                    "roomId",
                    firstRoom.get("id").asLong())),
            201);
    String patientPath = "/api/v1/patients?q=" + search;

    assertThat(
            patientItems(
                "admin",
                patientPath
                    + "&activeAdmission=true&doctorId="
                    + attendingDoctorId
                    + "&roomId="
                    + firstRoom.get("id").asLong()))
        .hasSize(1);

    var byName = patientItems(
        "admin",
        "/api/v1/patients?q="
            + patient.get("firstName").asText().toLowerCase()
            + "&activeAdmission=true");
    assertThat(byName).hasSize(1);

    Long otherDoctorId = null;
    for (var doctor : doctors) {
      if (doctor.get("id").asLong() != attendingDoctorId) {
        otherDoctorId = doctor.get("id").asLong();
        break;
      }
    }
    assertThat(otherDoctorId).isNotNull();
    assertThat(
            patientItems(
                "admin",
                patientPath + "&activeAdmission=true&doctorId=" + otherDoctorId + "&roomId="
                    + firstRoom.get("id").asLong()))
        .isEmpty();
    assertThat(patientItems("admin", patientPath + "&activeAdmission=false")).isEmpty();

    var moved =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/admissions/" + admission.get("id").asLong() + "/transfer",
                Map.of(
                    "roomId",
                    nextRoom.get("id").asLong(),
                    "reason",
                    "Filter verification",
                    "version",
                    admission.get("version").asLong())),
            200);
    assertThat(
            patientItems(
                "admin",
                patientPath + "&activeAdmission=true&roomId=" + firstRoom.get("id").asLong()))
        .isEmpty();
    assertThat(
            patientItems(
                "admin",
                patientPath + "&activeAdmission=true&roomId=" + nextRoom.get("id").asLong()))
        .hasSize(1);

    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + admission.get("id").asLong() + "/discharge",
            Map.of("version", moved.get("version").asLong()))
        .andExpect(status().isOk());
    assertThat(patientItems("admin", patientPath + "&activeAdmission=true")).isEmpty();
    assertThat(patientItems("admin", patientPath + "&activeAdmission=false")).hasSize(1);
    assertThat(patientItems("admin", patientPath + "&roomId=" + nextRoom.get("id").asLong()))
        .isEmpty();
  }

  @Test
  void requestedDoctorFilterCannotExpandDoctorAccess() throws Exception {
    var patient = createPatient();
    var room = room(1);
    long currentDoctorId = users.findByUsername("doctor").orElseThrow().getDoctorId();
    var doctors = result(request("admin", "GET", "/api/v1/doctors", null), 200).get("items");
    Long otherDoctorId = null;
    for (var doctor : doctors) {
      if (doctor.get("id").asLong() != currentDoctorId) {
        otherDoctorId = doctor.get("id").asLong();
        break;
      }
    }
    assertThat(otherDoctorId).isNotNull();
    request(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of(
                "patientId",
                patient.get("id").asLong(),
                "doctorId",
                otherDoctorId,
                "roomId",
                room.get("id").asLong()))
        .andExpect(status().isCreated());

    assertThat(
            patientItems(
                "doctor",
                "/api/v1/patients?q="
                    + patient.get("patientIdentifier").asText()
                    + "&doctorId="
                    + otherDoctorId
                    + "&activeAdmission=true"))
        .isEmpty();
  }

  @Test
  void rejectsNonPositiveOperationalFilterIds() throws Exception {
    request("admin", "GET", "/api/v1/patients?doctorId=0", null)
        .andExpect(status().isBadRequest());
    request("admin", "GET", "/api/v1/patients?roomId=-1", null)
        .andExpect(status().isBadRequest());
  }

  private JsonNode patientItems(String user, String path) throws Exception {
    return result(request(user, "GET", path, null), 200).get("items");
  }
}
