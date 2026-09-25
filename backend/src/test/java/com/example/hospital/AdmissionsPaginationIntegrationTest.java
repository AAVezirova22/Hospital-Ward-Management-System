package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

class AdmissionsPaginationIntegrationTest extends HospitalSupport {
  private static final LocalDate FILTER_DAY = LocalDate.of(2098, 7, 6);

  @Autowired JdbcTemplate jdbc;

  @Test
  @Transactional
  @Rollback
  void pagesAndCombinesAdmissionFiltersWithStableInclusiveDateOrdering() throws Exception {
    long homeDepartment = activeDepartment();
    long scopedDoctor = doctorId("doctor");
    long anotherDoctor = anotherDoctor(scopedDoctor, homeDepartment);

    long firstCreatedAdmission = createAdmission(scopedDoctor, homeDepartment);
    long secondCreatedAdmission = createAdmission(scopedDoctor, homeDepartment);
    long dischargedAdmission = createAdmission(anotherDoctor, homeDepartment);
    long otherActiveAdmission = createAdmission(anotherDoctor, homeDepartment);

    Instant endOfFilterDay = Instant.parse("2098-07-06T23:59:59.999Z");
    setAdmission(firstCreatedAdmission, endOfFilterDay, "ACTIVE");
    setAdmission(secondCreatedAdmission, endOfFilterDay, "ACTIVE");
    setAdmission(dischargedAdmission, Instant.parse("2098-07-06T18:00:00Z"), "DISCHARGED");
    setAdmission(otherActiveAdmission, Instant.parse("2098-07-06T09:00:00Z"), "ACTIVE");

    String filters =
        "status=active&doctorId="
            + scopedDoctor
            + "&from="
            + FILTER_DAY
            + "&to="
            + FILTER_DAY;
    JsonNode firstPage =
        result(
            request("admin", "GET", "/api/v1/admissions?" + filters + "&page=0&size=1", null),
            200);
    assertThat(firstPage.get("page").asInt()).isZero();
    assertThat(firstPage.get("size").asInt()).isEqualTo(1);
    assertThat(firstPage.get("totalElements").asLong()).isEqualTo(2);
    assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
    assertThat(firstPage.get("hasNext").asBoolean()).isTrue();
    assertThat(firstPage.get("nextPage").asInt()).isEqualTo(1);
    assertThat(admissionId(firstPage.get("items").get(0))).isEqualTo(secondCreatedAdmission);

    JsonNode secondPage =
        result(
            request("admin", "GET", "/api/v1/admissions?" + filters + "&page=1&size=1", null),
            200);
    assertThat(secondPage.get("page").asInt()).isEqualTo(1);
    assertThat(secondPage.get("hasNext").asBoolean()).isFalse();
    assertThat(secondPage.get("nextPage").isNull()).isTrue();
    assertThat(admissionId(secondPage.get("items").get(0))).isEqualTo(firstCreatedAdmission);

    JsonNode clampedPage =
        result(
            request("admin", "GET", "/api/v1/admissions?" + filters + "&page=1000000000&size=1", null),
            200);
    assertThat(clampedPage.get("page").asInt()).isEqualTo(1);
    assertThat(admissionId(clampedPage.get("items").get(0))).isEqualTo(firstCreatedAdmission);

    JsonNode cappedSize =
        result(request("admin", "GET", "/api/v1/admissions?size=101", null), 200);
    assertThat(cappedSize.get("size").asInt()).isEqualTo(100);

    JsonNode statusFiltered =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/admissions?status=DISCHARGED&doctorId="
                    + anotherDoctor
                    + "&from="
                    + FILTER_DAY
                    + "&to="
                    + FILTER_DAY,
                null),
            200);
    assertThat(statusFiltered.get("totalElements").asLong()).isEqualTo(1);
    assertThat(admissionId(statusFiltered.get("items").get(0))).isEqualTo(dischargedAdmission);

    JsonNode doctorOwnScope =
        result(
            request(
                "doctor",
                "GET",
                "/api/v1/admissions?status=ACTIVE&from=" + FILTER_DAY + "&to=" + FILTER_DAY,
                null),
            200);
    assertThat(doctorOwnScope.get("totalElements").asLong()).isEqualTo(2);

    JsonNode doctorCannotExpandScope =
        result(
            request(
                "doctor",
                "GET",
                "/api/v1/admissions?status=ACTIVE&doctorId="
                    + anotherDoctor
                    + "&from="
                    + FILTER_DAY
                    + "&to="
                    + FILTER_DAY,
                null),
            200);
    assertThat(doctorCannotExpandScope.get("totalElements").asLong()).isZero();
  }

  @Test
  @Transactional
  @Rollback
  void rejectsInvalidAdmissionPaginationAndFilters() throws Exception {
    request("admin", "GET", "/api/v1/admissions?page=-1", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    request("admin", "GET", "/api/v1/admissions?size=0", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    request("admin", "GET", "/api/v1/admissions?status=UNKNOWN", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    request("admin", "GET", "/api/v1/admissions?from=2026-02-02&to=2026-02-01", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @Transactional
  @Rollback
  void admissionsRemainInsideTheSelectedDepartment() throws Exception {
    long homeDepartment = activeDepartment();
    JsonNode workspace =
        result(
            requestAt(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Admission Scope " + unique(), "departmentName", "Admissions"),
                homeDepartment),
            201);
    long otherDepartment = workspace.get("departmentId").asLong();
    long otherDoctor = createDoctor(otherDepartment);
    long otherAdmission = createAdmission(otherDoctor, otherDepartment);

    JsonNode homePage =
        result(requestAt("admin", "GET", "/api/v1/admissions", null, homeDepartment), 200);
    JsonNode otherPage =
        result(requestAt("admin", "GET", "/api/v1/admissions", null, otherDepartment), 200);

    assertThat(containsAdmission(homePage, otherAdmission)).isFalse();
    assertThat(containsAdmission(otherPage, otherAdmission)).isTrue();
  }

  private long activeDepartment() throws Exception {
    return result(request("admin", "GET", "/api/v1/workspaces", null), 200)
        .get("activeDepartmentId")
        .asLong();
  }

  private long doctorId(String username) throws Exception {
    return result(request(username, "GET", "/api/v1/auth/me", null), 200)
        .get("doctorId")
        .asLong();
  }

  private long anotherDoctor(long currentDoctor, long department) throws Exception {
    JsonNode doctors = result(requestAt("admin", "GET", "/api/v1/doctors", null, department), 200);
    for (JsonNode doctor : doctors.path("items")) {
      long id = doctor.get("id").asLong();
      if (id != currentDoctor) return id;
    }
    return createDoctor(department);
  }

  private long createDoctor(long department) throws Exception {
    JsonNode doctor =
        result(
            requestAt(
                "admin",
                "POST",
                "/api/v1/doctors",
                Map.of(
                    "doctorIdentifier", "TEST-" + unique(),
                    "firstName", "Admissions",
                    "lastName", "Doctor",
                    "specialty", "General Medicine",
                    "active", true),
                department),
            201);
    return doctor.get("id").asLong();
  }

  private long createAdmission(long doctorId, long department) throws Exception {
    JsonNode patient =
        result(
            requestAt(
                "admin",
                "POST",
                "/api/v1/patients",
                Map.of(
                    "patientIdentifier", "TEST-" + unique(),
                    "firstName", "Admission",
                    "lastName", "Patient",
                    "dateOfBirth", "1980-01-01"),
                department),
            201);
    JsonNode room =
        result(
            requestAt(
                "admin",
                "POST",
                "/api/v1/rooms",
                Map.of("roomNumber", "T-" + unique(), "bedCount", 1, "active", true),
                department),
            201);
    JsonNode admission =
        result(
            requestAt(
                "admin",
                "POST",
                "/api/v1/admissions",
                Map.of(
                    "patientId", patient.get("id").asLong(),
                    "doctorId", doctorId,
                    "roomId", room.get("id").asLong()),
                department),
            201);
    return admission.get("id").asLong();
  }

  private void setAdmission(long id, Instant admittedAt, String status) {
    jdbc.update(
        "update admissions set admission_date_time = ?, status = ? where id = ?",
        Timestamp.from(admittedAt),
        status,
        id);
  }

  private ResultActions requestAt(
      String who, String method, String path, Object body, long departmentId) throws Exception {
    MockHttpServletRequestBuilder request =
        "POST".equals(method) ? post(path) : get(path);
    request.with(user(who)).with(csrf()).header("X-Department-Id", departmentId);
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request);
  }

  private long admissionId(JsonNode item) {
    return item.get("admission").get("id").asLong();
  }

  private boolean containsAdmission(JsonNode page, long id) {
    for (JsonNode item : page.get("items")) {
      if (admissionId(item) == id) return true;
    }
    return false;
  }
}
