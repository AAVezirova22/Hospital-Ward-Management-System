package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomUtilizationReportTest extends HospitalSupport {
  @Test
  void dailyAndWeeklyReportsUseClippedAssignmentIntervalsAndExposeNoPatientDetails()
      throws Exception {
    var room = room(2);
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(14)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(1);
    Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(6 * 3600L);
    Instant end = start.plusSeconds(6 * 3600L);
    var patient = createPatient();
    var admission = admit(patient, room);
    setRoomCreatedAt(room.path("id").asLong(), day.minusDays(10).atStartOfDay(ZoneOffset.UTC).toInstant());
    setAssignmentInterval(admission.path("id").asLong(), start, end);

    var daily = report("admin", day, day, "day", room.path("id").asLong(), null);
    var dailyRow = daily.path("rows").get(0);
    assertThat(daily.path("rows").size()).isEqualTo(1);
    assertThat(dailyRow.path("periodStart").asText()).isEqualTo(day.toString());
    assertThat(dailyRow.path("periodEnd").asText()).isEqualTo(day.toString());
    assertThat(dailyRow.path("occupiedBedHours").asDouble()).isEqualTo(6d);
    assertThat(dailyRow.path("capacityBedHours").asDouble()).isEqualTo(48d);
    assertThat(dailyRow.path("utilizationPercent").asDouble()).isEqualTo(12.5d);
    assertThat(dailyRow.has("patient")).isFalse();
    assertThat(dailyRow.has("patientId")).isFalse();

    var weekly = report("admin", day, day.plusDays(1), "week", room.path("id").asLong(), null);
    assertThat(weekly.path("rows").size()).isEqualTo(1);
    assertThat(weekly.path("rows").get(0).path("periodStart").asText()).isEqualTo(day.toString());
    assertThat(weekly.path("rows").get(0).path("periodEnd").asText()).isEqualTo(day.plusDays(1).toString());
    assertThat(weekly.path("rows").get(0).path("occupiedBedHours").asDouble()).isEqualTo(6d);
    assertThat(weekly.path("rows").get(0).path("capacityBedHours").asDouble()).isEqualTo(96d);
    assertThat(weekly.path("bucket").asText()).isEqualTo("week");
    assertThat(weekly.path("capacityBasis").asText()).contains("Current configured bed count");

    setOpenAssignment(admission.path("id").asLong(), day.atStartOfDay(ZoneOffset.UTC).toInstant());
    var future = report("admin", LocalDate.now(ZoneOffset.UTC).plusDays(1),
        LocalDate.now(ZoneOffset.UTC).plusDays(1), "day", room.path("id").asLong(), null);
    assertThat(future.path("rows").get(0).path("capacityBedHours").asDouble()).isZero();
    assertThat(future.path("rows").get(0).path("occupiedBedHours").asDouble()).isZero();
  }

  @Test
  void dailyBucketsSplitCrossMidnightAssignmentsAndUseDepartmentDstDayLength() throws Exception {
    var room = room(1);
    var patient = createPatient();
    var admission = admit(patient, room);
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(7);
    Instant crossMidnightStart = day.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(22 * 3600L);
    Instant crossMidnightEnd = crossMidnightStart.plusSeconds(4 * 3600L);
    setRoomCreatedAt(room.path("id").asLong(), day.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());
    setAssignmentInterval(admission.path("id").asLong(), crossMidnightStart, crossMidnightEnd);
    var split = report("admin", day, day.plusDays(1), "day", room.path("id").asLong(), null);
    assertThat(split.path("rows").size()).isEqualTo(2);
    assertThat(split.path("rows").get(0).path("occupiedBedHours").asDouble()).isEqualTo(2d);
    assertThat(split.path("rows").get(1).path("occupiedBedHours").asDouble()).isEqualTo(2d);

    long departmentId = jdbc.queryForObject(
        "select department_id from rooms where id=?", Long.class, room.path("id").asLong());
    String originalZone = jdbc.queryForObject(
        "select time_zone from departments where id=?", String.class, departmentId);
    jdbc.update("update departments set time_zone='America/New_York' where id=?", departmentId);
    try {
      LocalDate springDstDay = LocalDate.of(2026, 3, 8);
      ZoneId zone = ZoneId.of("America/New_York");
      Instant dstStart = springDstDay.atStartOfDay(zone).toInstant();
      Instant dstEnd = springDstDay.plusDays(1).atStartOfDay(zone).toInstant();
      setAssignmentInterval(admission.path("id").asLong(), dstStart, dstEnd);
      setRoomCreatedAt(room.path("id").asLong(), dstStart.minusSeconds(24 * 3600L));
      var dst = report("admin", springDstDay, springDstDay, "day", room.path("id").asLong(), null);
      assertThat(dst.path("timeZone").asText()).isEqualTo("America/New_York");
      assertThat(dst.path("rows").get(0).path("occupiedBedHours").asDouble()).isEqualTo(23d);
      assertThat(dst.path("rows").get(0).path("capacityBedHours").asDouble()).isEqualTo(23d);
      assertThat(dst.path("rows").get(0).path("utilizationPercent").asDouble()).isEqualTo(100d);
    } finally {
      jdbc.update("update departments set time_zone=? where id=?", originalZone, departmentId);
    }
  }

  @Test
  void weeklyBucketsSplitAtMondayAndClipBothEndsToTheRequestedDates() throws Exception {
    var room = room(2);
    var patient = createPatient();
    var admission = admit(patient, room);
    LocalDate monday = LocalDate.now(ZoneOffset.UTC).minusDays(14)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate sunday = monday.plusDays(6);
    LocalDate nextMonday = monday.plusDays(7);
    setRoomCreatedAt(room.path("id").asLong(), monday.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());
    Instant assignmentStart = sunday.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(22 * 3600L);
    setAssignmentInterval(admission.path("id").asLong(), assignmentStart,
        assignmentStart.plusSeconds(4 * 3600L));

    var report = report("admin", sunday, nextMonday, "week", room.path("id").asLong(), null);
    assertThat(report.path("rows").size()).isEqualTo(2);
    var sundayRow = report.path("rows").get(0);
    var mondayRow = report.path("rows").get(1);
    assertThat(sundayRow.path("periodStart").asText()).isEqualTo(sunday.toString());
    assertThat(sundayRow.path("periodEnd").asText()).isEqualTo(sunday.toString());
    assertThat(sundayRow.path("occupiedBedHours").asDouble()).isEqualTo(2d);
    assertThat(sundayRow.path("capacityBedHours").asDouble()).isEqualTo(48d);
    assertThat(mondayRow.path("periodStart").asText()).isEqualTo(nextMonday.toString());
    assertThat(mondayRow.path("periodEnd").asText()).isEqualTo(nextMonday.toString());
    assertThat(mondayRow.path("occupiedBedHours").asDouble()).isEqualTo(2d);
    assertThat(mondayRow.path("capacityBedHours").asDouble()).isEqualTo(48d);
  }

  @Test
  void filtersPatientAndDoctorScopeAndRejectsInvalidPeriods() throws Exception {
    var room = room(3);
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(4);
    Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
    var firstPatient = createPatient();
    var firstAdmission = admit(firstPatient, room);
    setRoomCreatedAt(room.path("id").asLong(), day.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());
    setAssignmentInterval(firstAdmission.path("id").asLong(), start, start.plusSeconds(2 * 3600L));
    var secondPatient = createPatient();
    var secondAdmission = admit(secondPatient, room);
    setAssignmentInterval(secondAdmission.path("id").asLong(), start, start.plusSeconds(5 * 3600L));

    var all = report("admin", day, day, "day", room.path("id").asLong(), null);
    var onePatient = report("admin", day, day, "day", room.path("id").asLong(), firstPatient.path("id").asLong());
    assertThat(all.path("rows").get(0).path("occupiedBedHours").asDouble()).isEqualTo(7d);
    assertThat(onePatient.path("rows").get(0).path("occupiedBedHours").asDouble()).isEqualTo(2d);
    assertThat(onePatient.path("scope").asText()).isEqualTo("Patient");

    request("admin", "GET", "/api/v1/reports/room-utilization?from=" + day + "&to=" + day
        + "&bucket=month", null).andExpect(status().isBadRequest());
    request("admin", "GET", "/api/v1/reports/room-utilization?from=" + day.plusDays(1)
        + "&to=" + day + "&bucket=day", null).andExpect(status().isBadRequest());
    request("admin", "GET", "/api/v1/reports/room-utilization?from=" + day.minusDays(400)
        + "&to=" + day + "&bucket=day", null).andExpect(status().isBadRequest());
  }

  @Test
  void doctorCannotRequestAReportForAnUnassignedPatientAndDepartmentScopeIsIsolated()
      throws Exception {
    var room = room(2);
    var unassignedPatient = createPatient();
    var anotherDoctor = createDoctor("UTIL-" + unique());
    var unassignedAdmission = createAdmission(unassignedPatient, room, anotherDoctor.path("id").asLong());
    long signedInDoctorId = users.findByUsername("doctor").orElseThrow().getDoctorId();
    var assignedPatient = createPatient();
    var assignedAdmission = createAdmission(assignedPatient, room, signedInDoctorId);
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(3);
    setRoomCreatedAt(room.path("id").asLong(), day.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant());
    setAssignmentInterval(unassignedAdmission.path("id").asLong(), day.atStartOfDay(ZoneOffset.UTC).toInstant(),
        day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    setAssignmentInterval(assignedAdmission.path("id").asLong(), day.atStartOfDay(ZoneOffset.UTC).toInstant(),
        day.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(2 * 3600L));
    var doctorScoped = report("doctor", day, day, "day", room.path("id").asLong(), null);
    assertThat(doctorScoped.path("rows").get(0).path("occupiedBedHours").asDouble()).isEqualTo(2d);
    assertThat(doctorScoped.path("scope").asText()).isEqualTo("Your assigned admissions");
    request("doctor", "GET", "/api/v1/reports/room-utilization?from=" + day + "&to=" + day
        + "&bucket=day&patientId=" + unassignedPatient.path("id").asLong(), null)
        .andExpect(status().isForbidden());

    var otherDepartment = result(request("admin", "POST", "/api/v1/workspaces/hospitals", Map.of(
        "name", "Utilization Clinic " + unique(), "departmentName", "Imaging"), 1L), 201);
    var isolated = result(request("admin", "GET", "/api/v1/reports/room-utilization?from=" + day
        + "&to=" + day + "&bucket=day", null, otherDepartment.path("departmentId").asLong()), 200);
    assertThat(isolated.path("rows").size()).isZero();
  }

  private JsonNode report(String who, LocalDate from, LocalDate to, String bucket, Long roomId,
      Long patientId) throws Exception {
    String query = "from=" + from + "&to=" + to + "&bucket=" + bucket;
    if (roomId != null) query += "&roomId=" + roomId;
    if (patientId != null) query += "&patientId=" + patientId;
    return result(request(who, "GET", "/api/v1/reports/room-utilization?" + query, null), 200);
  }

  private void setRoomCreatedAt(long roomId, Instant createdAt) {
    jdbc.update("update rooms set created_at=? where id=?", Timestamp.from(createdAt), roomId);
  }

  private void setAssignmentInterval(long admissionId, Instant assignedAt, Instant releasedAt) {
    jdbc.update("update room_assignments set assigned_at=?, released_at=? where admission_id=?",
        Timestamp.from(assignedAt), Timestamp.from(releasedAt), admissionId);
  }

  private void setOpenAssignment(long admissionId, Instant assignedAt) {
    jdbc.update("update room_assignments set assigned_at=?, released_at=null where admission_id=?",
        Timestamp.from(assignedAt), admissionId);
  }

  private JsonNode createDoctor(String identifier) throws Exception {
    return result(request("admin", "POST", "/api/v1/doctors", Map.of(
        "doctorIdentifier", identifier, "firstName", "Report", "lastName", "Doctor",
        "specialty", "General", "active", true)), 201);
  }

  private JsonNode createAdmission(JsonNode patient, JsonNode room, long doctorId) throws Exception {
    return result(request("admin", "POST", "/api/v1/admissions", Map.of(
        "patientId", patient.path("id").asLong(), "doctorId", doctorId,
        "roomId", room.path("id").asLong())), 201);
  }
}
