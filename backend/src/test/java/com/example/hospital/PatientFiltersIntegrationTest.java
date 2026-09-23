package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    var doctors = result(request("admin", "GET", "/api/v1/doctors", null), 200);
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
            result(
                    request(
                        "admin",
                        "GET",
                        patientPath
                            + "&activeAdmission=true&doctorId="
                            + attendingDoctorId
                            + "&roomId="
                            + firstRoom.get("id").asLong(),
                        null),
                    200))
        .hasSize(1);

    var byName =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/patients?q="
                    + patient.get("firstName").asText().toLowerCase()
                    + "&activeAdmission=true",
                null),
            200);
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
            result(
                    request(
                        "admin",
                        "GET",
                        patientPath + "&activeAdmission=true&doctorId=" + otherDoctorId + "&roomId="
                            + firstRoom.get("id").asLong(),
                        null),
                    200))
        .isEmpty();
    assertThat(
            result(
                    request("admin", "GET", patientPath + "&activeAdmission=false", null), 200))
        .isEmpty();

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
            result(
                    request(
                        "admin",
                        "GET",
                        patientPath + "&activeAdmission=true&roomId=" + firstRoom.get("id").asLong(),
                        null),
                    200))
        .isEmpty();
    assertThat(
            result(
                    request(
                        "admin",
                        "GET",
                        patientPath + "&activeAdmission=true&roomId=" + nextRoom.get("id").asLong(),
                        null),
                    200))
        .hasSize(1);

    request(
            "admin",
            "POST",
            "/api/v1/admissions/" + admission.get("id").asLong() + "/discharge",
            Map.of("version", moved.get("version").asLong()))
        .andExpect(status().isOk());
    assertThat(
            result(
                    request(
                        "admin",
                        "GET",
                        patientPath + "&activeAdmission=true",
                        null),
                    200))
        .isEmpty();
    assertThat(
            result(
                    request(
                        "admin",
                        "GET",
                        patientPath + "&activeAdmission=false",
                        null),
                    200))
        .hasSize(1);
    assertThat(
            result(
                    request(
                        "admin",
                        "GET",
                        patientPath + "&roomId=" + nextRoom.get("id").asLong(),
                        null),
                    200))
        .isEmpty();
  }

  @Test
  void requestedDoctorFilterCannotExpandDoctorAccess() throws Exception {
    var patient = createPatient();
    var room = room(1);
    var currentUser = result(request("doctor", "GET", "/api/v1/auth/me", null), 200);
    long currentDoctorId = currentUser.get("doctorId").asLong();
    var doctors = result(request("admin", "GET", "/api/v1/doctors", null), 200);
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
            result(
                    request(
                        "doctor",
                        "GET",
                        "/api/v1/patients?q="
                            + patient.get("patientIdentifier").asText()
                            + "&doctorId="
                            + otherDoctorId
                            + "&activeAdmission=true",
                        null),
                    200))
        .isEmpty();
  }

  @Test
  void rejectsNonPositiveOperationalFilterIds() throws Exception {
    request("admin", "GET", "/api/v1/patients?doctorId=0", null)
        .andExpect(status().isBadRequest());
    request("admin", "GET", "/api/v1/patients?roomId=-1", null)
        .andExpect(status().isBadRequest());
  }
}
