package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DepartmentTimeZoneIntegrationTest extends HospitalSupport {
  @Autowired JdbcTemplate jdbc;

  @Test
  void departmentTimeZoneDefaultsToUtcCanBeChangedAndRejectsInvalidOrUnauthorizedValues()
      throws Exception {
    var created =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Time Zone Clinic " + unique(), "departmentName", "Imaging")),
            201);
    long departmentId = created.path("departmentId").asLong();
    assertThat(jdbc.queryForObject(
            "select time_zone from departments where id=?", String.class, departmentId))
        .isEqualTo("UTC");

    result(
        request(
            "admin",
            "PUT",
            "/api/v1/workspaces/departments/" + departmentId + "/timezone",
            Map.of("timeZone", "Europe/Paris")),
        200);
    request(
            "admin",
            "PUT",
            "/api/v1/workspaces/departments/" + departmentId + "/timezone",
            Map.of("timeZone", "+02:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
    long staffDepartmentId = jdbc.queryForObject(
        "select department_id from department_memberships where user_id=(select id from app_users where username=?) order by department_id limit 1",
        Long.class,
        "staff");
    request("staff", "PUT", "/api/v1/workspaces/departments/" + staffDepartmentId + "/timezone", Map.of("timeZone", "Europe/Paris"))
        .andExpect(status().isForbidden());

    assertThat(jdbc.queryForObject(
            "select time_zone from departments where id=?", String.class, departmentId))
        .isEqualTo("Europe/Paris");
    var listed = result(request("admin", "GET", "/api/v1/workspaces", null, departmentId), 200);
    assertThat(listed.path("timeZone").asText()).isEqualTo("Europe/Paris");
    assertThat(listed.toString()).contains("\"timeZone\":\"Europe/Paris\"");
  }

  @Test
  void procedureReportUsesDepartmentCalendarDaysAcrossBothDstTransitions() throws Exception {
    long departmentId = jdbc.queryForObject(
        "select department_id from department_memberships where user_id=(select id from app_users where username=?) order by department_id limit 1",
        Long.class,
        "admin");
    ZoneId zone = ZoneId.of("America/New_York");
    LocalDate springDay = LocalDate.of(2025, 3, 9);
    LocalDate fallDay = LocalDate.of(2025, 11, 2);
    Instant springStart = springDay.atStartOfDay(zone).toInstant();
    Instant springEnd = springDay.plusDays(1).atStartOfDay(zone).toInstant();
    Instant fallStart = fallDay.atStartOfDay(zone).toInstant();
    Instant fallEnd = fallDay.plusDays(1).atStartOfDay(zone).toInstant();
    var seed = jdbc.queryForMap(
        "select a.id,a.patient_id,a.attending_doctor_id from admissions a where a.department_id=? and not exists ("
            + "select 1 from performed_procedures pp where pp.admission_id=a.id and "
            + "((pp.performed_at>=? and pp.performed_at<?) or (pp.performed_at>=? and pp.performed_at<?))) "
            + "order by a.id limit 1",
        departmentId,
        Timestamp.from(springStart),
        Timestamp.from(springEnd),
        Timestamp.from(fallStart),
        Timestamp.from(fallEnd));
    long admission = ((Number) seed.get("id")).longValue();
    long patientId = ((Number) seed.get("patient_id")).longValue();
    long doctorId = ((Number) seed.get("attending_doctor_id")).longValue();
    Long procedureId = jdbc.queryForObject(
        "select id from medical_procedures where department_id=? order by id limit 1", Long.class, departmentId);

    request(
            "admin",
            "PUT",
            "/api/v1/workspaces/departments/" + departmentId + "/timezone",
            Map.of("timeZone", zone.getId()))
        .andExpect(status().isOk());
    try {
      assertReportContainsOnlyLocalDay(
          departmentId, patientId, admission, procedureId, doctorId, springDay, Duration.ofHours(23), zone);
      assertReportContainsOnlyLocalDay(
          departmentId, patientId, admission, procedureId, doctorId, fallDay, Duration.ofHours(25), zone);
    } finally {
      request(
              "admin",
              "PUT",
              "/api/v1/workspaces/departments/" + departmentId + "/timezone",
              Map.of("timeZone", "UTC"))
          .andExpect(status().isOk());
    }
  }

  @Test
  void operationsTrendUsesTheDepartmentLocalDate() throws Exception {
    long departmentId = jdbc.queryForObject(
        "select department_id from department_memberships where user_id=(select id from app_users where username=?) order by department_id limit 1",
        Long.class,
        "admin");
    ZoneId zone = ZoneId.of("Pacific/Kiritimati");
    request(
            "admin",
            "PUT",
            "/api/v1/workspaces/departments/" + departmentId + "/timezone",
            Map.of("timeZone", zone.getId()))
        .andExpect(status().isOk());
    try {
      var overview = result(
          request("admin", "GET", "/api/v1/reports/operations", null, departmentId), 200);
      LocalDate localAsOf = Instant.parse(overview.path("asOf").asText()).atZone(zone).toLocalDate();
      var trends = overview.path("trends");
      assertThat(overview.path("timeZone").asText()).isEqualTo(zone.getId());
      assertThat(trends.get(trends.size() - 1).path("date").asText())
          .isEqualTo(localAsOf.toString());
    } finally {
      request(
              "admin",
              "PUT",
              "/api/v1/workspaces/departments/" + departmentId + "/timezone",
              Map.of("timeZone", "UTC"))
          .andExpect(status().isOk());
    }
  }

  private void assertReportContainsOnlyLocalDay(
      long departmentId,
      long patientId,
      long admissionId,
      long procedureId,
      long doctorId,
      LocalDate day,
      Duration expectedLength,
      ZoneId zone)
      throws Exception {
    Instant start = day.atStartOfDay(zone).toInstant();
    Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
    assertThat(Duration.between(start, end)).isEqualTo(expectedLength);
    recordProcedure(departmentId, admissionId, procedureId, doctorId, start.minusSeconds(1));
    long atStart = recordProcedure(departmentId, admissionId, procedureId, doctorId, start);
    long beforeEnd = recordProcedure(departmentId, admissionId, procedureId, doctorId, end.minusSeconds(1));
    recordProcedure(departmentId, admissionId, procedureId, doctorId, end);

    var report = result(
        request(
            "admin",
            "GET",
            "/api/v1/reports/procedures?from=" + day + "&to=" + day + "&patientId=" + patientId,
            null,
            departmentId),
        200);
    List<Long> included = new ArrayList<>();
    for (JsonNode row : report.path("rows")) included.add(row.path("record").path("id").asLong());
    assertThat(included).containsExactlyInAnyOrder(atStart, beforeEnd);
    assertThat(report.path("timeZone").asText()).isEqualTo(zone.getId());
  }

  private long recordProcedure(long departmentId, long admissionId, long procedureId, long doctorId, Instant at) {
    return jdbc.queryForObject(
        "insert into performed_procedures(department_id,admission_id,medical_procedure_id,performed_by_doctor_id,performed_at,note,price_at_execution) "
            + "values (?,?,?,?,?,?,?) returning id",
        Long.class,
        departmentId,
        admissionId,
        procedureId,
        doctorId,
        Timestamp.from(at),
        "department day test",
        new BigDecimal("10.00"));
  }
}
