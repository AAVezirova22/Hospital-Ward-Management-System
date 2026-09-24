package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DoctorWorkloadReportTest extends HospitalSupport {
  @Test
  void reportSummarizesActiveDoctorsAndCurrentWorkloadWithinRoleAndDepartmentScope()
      throws Exception {
    var marker = unique();
    var busyDoctor = createDoctor("BUSY-" + marker, true);
    var idleDoctor = createDoctor("IDLE-" + marker, true);
    var inactiveDoctor = createDoctor("INACTIVE-" + marker, false);

    var patientOne = createPatient();
    var firstRoom = room(2);
    var secondRoom = room(2);
    var thirdRoom = room(2);
    var firstAdmission =
        admitToDoctor(patientOne, firstRoom, busyDoctor.path("id").asLong());
    result(
        request(
            "admin",
            "POST",
            "/api/v1/admissions/" + firstAdmission.path("id").asLong() + "/transfer",
            Map.of(
                "roomId", secondRoom.path("id").asLong(),
                "reason", "Workload report transfer",
                "version", firstAdmission.path("version").asLong())),
        200);
    admitToDoctor(createPatient(), thirdRoom, busyDoctor.path("id").asLong());

    result(
        request(
            "admin",
            "POST",
            "/api/v1/admissions/" + firstAdmission.path("id").asLong() + "/procedures",
            Map.of(
                "medicalProcedureId", 1,
                "doctorId", busyDoctor.path("id").asLong(),
                "performedAt",
                Instant.parse(firstAdmission.path("admissionDateTime").asText())
                    .plusMillis(1)
                    .toString())),
        201);

    var today = LocalDate.now(ZoneOffset.UTC);
    var period = "from=" + today.minusDays(3) + "&to=" + today.plusDays(3);
    var report =
        result(request("admin", "GET", "/api/v1/reports/doctor-workload?" + period, null), 200);
    var busyRow = rowFor(report.path("rows"), busyDoctor.path("id").asLong());
    var idleRow = rowFor(report.path("rows"), idleDoctor.path("id").asLong());

    assertThat(busyRow.path("activeAdmissions").asInt()).isEqualTo(2);
    assertThat(busyRow.path("assignedBeds").asInt()).isEqualTo(2);
    assertThat(busyRow.path("recentProcedures").asInt()).isEqualTo(1);
    assertThat(idleRow.path("activeAdmissions").asInt()).isZero();
    assertThat(idleRow.path("assignedBeds").asInt()).isZero();
    assertThat(idleRow.path("recentProcedures").asInt()).isZero();
    assertThat(findRow(report.path("rows"), inactiveDoctor.path("id").asLong())).isNull();

    var futureDay = today.plusDays(3);
    var futureReport =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/reports/doctor-workload?from=" + futureDay + "&to=" + futureDay,
                null),
            200);
    assertThat(rowFor(futureReport.path("rows"), busyDoctor.path("id").asLong())
            .path("recentProcedures").asInt())
        .isZero();

    var staffReport =
        result(request("staff", "GET", "/api/v1/reports/doctor-workload?" + period, null), 200);
    assertThat(findRow(staffReport.path("rows"), busyDoctor.path("id").asLong())).isNotNull();
    assertThat(findRow(staffReport.path("rows"), idleDoctor.path("id").asLong())).isNotNull();

    long signedInDoctorId = users.findByUsername("doctor").orElseThrow().getDoctorId();
    var doctorReport =
        result(
            request(
                "doctor",
                "GET",
                "/api/v1/reports/doctor-workload?" + period + "&doctorId="
                    + busyDoctor.path("id").asLong(),
                null),
            200);
    assertThat(doctorReport.path("rows").size()).isEqualTo(1);
    assertThat(doctorReport.path("rows").get(0).path("doctor").path("id").asLong())
        .isEqualTo(signedInDoctorId);
    assertThat(doctorReport.path("scope").asText()).isEqualTo("Your workload");

    var otherDepartment =
        result(
            request(
                "admin",
                "POST",
                "/api/v1/workspaces/hospitals",
                Map.of("name", "Workload Clinic " + marker, "departmentName", "Imaging"),
                1L),
            201);
    var isolatedReport =
        result(
            request(
                "admin",
                "GET",
                "/api/v1/reports/doctor-workload?" + period,
                null,
                otherDepartment.path("departmentId").asLong()),
            200);
    assertThat(isolatedReport.path("rows").size()).isZero();
  }

  @Test
  void reportRejectsAnInvertedProcedurePeriod() throws Exception {
    var today = LocalDate.now(ZoneOffset.UTC);
    request(
            "admin",
            "GET",
            "/api/v1/reports/doctor-workload?from=" + today.plusDays(1) + "&to=" + today,
            null)
        .andExpect(status().isBadRequest());
  }

  private JsonNode createDoctor(String identifier, boolean active) throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/doctors",
            Map.of(
                "doctorIdentifier", identifier,
                "firstName", "Workload",
                "lastName", identifier,
                "specialty", "General",
                "active", active)),
        201);
  }

  private JsonNode admitToDoctor(JsonNode patient, JsonNode room, long doctorId)
      throws Exception {
    return result(
        request(
            "admin",
            "POST",
            "/api/v1/admissions",
            Map.of(
                "patientId", patient.path("id").asLong(),
                "doctorId", doctorId,
                "roomId", room.path("id").asLong())),
        201);
  }

  private JsonNode rowFor(JsonNode rows, long doctorId) {
    var row = findRow(rows, doctorId);
    if (row == null) throw new AssertionError("Workload row not found for doctor " + doctorId);
    return row;
  }

  private JsonNode findRow(JsonNode rows, long doctorId) {
    for (JsonNode row : rows) {
      if (row.path("doctor").path("id").asLong() == doctorId) return row;
    }
    return null;
  }
}
