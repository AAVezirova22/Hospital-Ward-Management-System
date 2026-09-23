package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HospitalAccessTest extends HospitalSupport {
  @Test
  void doctorsSeeOnlyAssignedPatientsAndAdmissions() throws Exception {
    var p = createPatient();
    var r = room(2);
    var a =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/admissions",
                Map.of(
                    "patientId",
                    p.get("id").asLong(),
                    "doctorId",
                    2,
                    "roomId",
                    r.get("id").asLong())),
            201);
    request("doctor", "GET", "/api/v1/patients/" + p.get("id").asLong(), null)
        .andExpect(status().isForbidden());
    request("doctor", "GET", "/api/v1/admissions/" + a.get("id").asLong(), null)
        .andExpect(status().isForbidden());
    var search =
        result(
            request(
                "doctor", "GET", "/api/v1/patients?q=" + p.get("patientIdentifier").asText(), null),
            200);
    assertThat(search.path("items").size()).isZero();
    request(
            "doctor",
            "GET",
            "/api/v1/reports/procedures?from=2020-01-01&to=2030-01-01&patientId="
                + p.get("id").asLong(),
            null)
        .andExpect(status().isForbidden());
  }

  @Test
  void patientDirectoryUsesBoundedPagesStableOrderingAndSearch() throws Exception {
    String group = "PAGE-" + unique() + "-";
    createDirectoryPatient(group + "C", "Charlie");
    createDirectoryPatient(group + "A", "Alpha");
    createDirectoryPatient(group + "B", "Bravo");

    var first =
        result(
            request("admin", "GET", "/api/v1/patients?q=" + group.toLowerCase() + "&page=0&size=1", null),
            200);
    assertThat(first.path("items").size()).isEqualTo(1);
    assertThat(first.path("items").get(0).path("lastName").asText()).isEqualTo("Alpha");
    assertThat(first.path("page").asInt()).isZero();
    assertThat(first.path("size").asInt()).isEqualTo(1);
    assertThat(first.path("totalElements").asLong()).isEqualTo(3);
    assertThat(first.path("totalPages").asInt()).isEqualTo(3);
    assertThat(first.path("hasNext").asBoolean()).isTrue();
    assertThat(first.path("nextPage").asInt()).isEqualTo(1);

    var last =
        result(
            request("admin", "GET", "/api/v1/patients?q=" + group + "&page=2&size=1", null),
            200);
    assertThat(last.path("items").get(0).path("lastName").asText()).isEqualTo("Charlie");
    assertThat(last.path("hasNext").asBoolean()).isFalse();
    assertThat(last.path("nextPage").isNull()).isTrue();

    var outOfRange =
        result(
            request("admin", "GET", "/api/v1/patients?q=" + group + "&page=999&size=1", null),
            200);
    assertThat(outOfRange.path("page").asInt()).isEqualTo(2);
    assertThat(outOfRange.path("items").get(0).path("lastName").asText())
        .isEqualTo("Charlie");

    var capped = result(request("admin", "GET", "/api/v1/patients?q=" + group + "&size=500", null), 200);
    assertThat(capped.path("size").asInt()).isEqualTo(100);
    request("admin", "GET", "/api/v1/patients?page=-1", null)
        .andExpect(status().isBadRequest());
    request("admin", "GET", "/api/v1/patients?size=0", null)
        .andExpect(status().isBadRequest());
  }

  @Test
  void doctorPatientSearchExcludesUnassignedPatients() throws Exception {
    var patient = createPatient();
    var search =
        result(
            request(
                "doctor",
                "GET",
                "/api/v1/patients?q=" + patient.get("patientIdentifier").asText(),
                null),
            200);
    assertThat(search.path("items").size()).isZero();
    assertThat(search.path("totalElements").asLong()).isZero();
  }

  private void createDirectoryPatient(String identifier, String lastName) throws Exception {
    request(
            "admin",
            "POST",
            "/api/v1/patients",
            Map.of(
                "patientIdentifier", identifier,
                "firstName", "Directory",
                "lastName", lastName,
                "dateOfBirth", "1980-01-01"))
        .andExpect(status().isCreated());
  }

  @Test
  void staffAndDoctorsCannotAdministerUsersOrCatalogue() throws Exception {
    for (String who : List.of("staff", "doctor")) {
      request(who, "GET", "/api/v1/users", null).andExpect(status().isForbidden());
      request(
              who,
              "POST",
              "/api/v1/rooms",
              Map.of("roomNumber", "BAD", "bedCount", 1, "active", true))
          .andExpect(status().isForbidden());
    }
    request(
            "doctor",
            "POST",
            "/api/v1/patients",
            Map.of(
                "patientIdentifier",
                "X",
                "firstName",
                "A",
                "lastName",
                "B",
                "dateOfBirth",
                "1980-01-01"))
        .andExpect(status().isForbidden());
  }
}
