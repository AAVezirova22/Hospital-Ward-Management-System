package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditHistoryFiltersTest extends HospitalSupport {
  @Test
  void combinesTrimmedCaseInsensitiveFiltersWithInclusiveDepartmentLocalDates() throws Exception {
    var patient = createPatient();
    long patientId = patient.path("id").asLong();
    long departmentId = departmentForPatient(patientId);
    String originalZone = jdbc.queryForObject(
        "select time_zone from departments where id=?", String.class, departmentId);
    LocalDate localDay = LocalDate.of(2025, 12, 31);
    Instant nearUtcMidnight = Instant.parse("2026-01-01T00:30:00Z");
    try {
      jdbc.update("update departments set time_zone='America/Los_Angeles' where id=?", departmentId);
      jdbc.update("update audit_events set timestamp=? where department_id=? and event_type=? and entity_id=?",
          Timestamp.from(nearUtcMidnight), departmentId, "PATIENT_CREATED", patientId);
      long actorId = users.findByUsername("admin").orElseThrow().getId();
      long otherActorId = users.findByUsername("staff").orElseThrow().getId();
      long otherEntityId = patientId + 1_000_000_000L;
      insertAuditEvent(departmentId, otherActorId, "PATIENT_CREATED", "Patient", patientId, "UI", nearUtcMidnight);
      insertAuditEvent(departmentId, actorId, "PATIENT_UPDATED", "Patient", patientId, "UI", nearUtcMidnight);
      insertAuditEvent(departmentId, actorId, "PATIENT_CREATED", "Ward", patientId, "UI", nearUtcMidnight);
      insertAuditEvent(departmentId, actorId, "PATIENT_CREATED", "Patient", otherEntityId, "UI", nearUtcMidnight);
      insertAuditEvent(departmentId, actorId, "PATIENT_CREATED", "Patient", patientId, "API", nearUtcMidnight);

      var filtered = auditWithParams("admin", departmentId, Map.of(
          "page", "0", "size", "1", "eventType", " patient_created ",
          "actorId", Long.toString(actorId), "entityType", " pAtIeNt ",
          "entityId", Long.toString(patientId), "source", " ui ",
          "from", localDay.toString(), "to", localDay.toString()));
      assertThat(filtered.path("total").asLong()).isEqualTo(1);
      assertThat(filtered.path("events").size()).isEqualTo(1);
      var event = filtered.path("events").get(0);
      assertThat(event.path("eventType").asText()).isEqualTo("PATIENT_CREATED");
      assertThat(event.path("userId").asLong()).isEqualTo(actorId);
      assertThat(event.path("entityType").asText()).isEqualTo("Patient");
      assertThat(event.path("entityId").asLong()).isEqualTo(patientId);
      assertThat(event.path("source").asText()).isEqualTo("UI");
      assertThat(nearUtcMidnight.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate())
          .isEqualTo(localDay);

      var nextDay = audit("admin", departmentId, filters(
          actorId, patientId, localDay.plusDays(1), localDay.plusDays(1)));
      assertThat(nextDay.path("total").asLong()).isZero();
      assertThat(nextDay.path("events").size()).isZero();

      var fromOnly = auditWithParams("admin", departmentId, Map.of(
          "eventType", "PATIENT_CREATED", "actorId", Long.toString(actorId),
          "entityType", "Patient", "entityId", Long.toString(patientId),
          "from", localDay.toString(), "source", "  "));
      var toOnly = auditWithParams("admin", departmentId, Map.of(
          "actorId", Long.toString(actorId), "entityType", "Patient",
          "entityId", Long.toString(patientId), "to", localDay.toString(),
          "eventType", "  ", "source", "UI"));
      assertThat(fromOnly.path("total").asLong()).isEqualTo(2);
      assertThat(toOnly.path("total").asLong()).isEqualTo(2);

      request("admin", "GET", "/api/v1/audit?from=" + localDay.plusDays(1)
          + "&to=" + localDay, null).andExpect(status().isBadRequest());
      request("admin", "GET", "/api/v1/audit?actorId=0", null)
          .andExpect(status().isBadRequest());
      request("admin", "GET", "/api/v1/audit?entityId=-1", null)
          .andExpect(status().isBadRequest());
    } finally {
      jdbc.update("update departments set time_zone=? where id=?", originalZone, departmentId);
    }
  }

  @Test
  void filtersRemainWithinSelectedDepartmentAndEndpointStillRequiresAdmin() throws Exception {
    var homePatient = createPatient();
    long homeDepartmentId = departmentForPatient(homePatient.path("id").asLong());
    var otherDepartment = result(request("admin", "POST", "/api/v1/workspaces/hospitals", Map.of(
        "name", "Audit Filter Clinic " + unique(), "departmentName", "Imaging"), homeDepartmentId), 201);
    long otherDepartmentId = otherDepartment.path("departmentId").asLong();
    var otherPatient = result(request("admin", "POST", "/api/v1/patients", Map.of(
        "patientIdentifier", "AUDIT-FILTER-" + unique(),
        "firstName", "Scoped",
        "lastName", "Patient",
        "dateOfBirth", "1980-01-01"), otherDepartmentId), 201);
    long actorId = users.findByUsername("admin").orElseThrow().getId();
    long otherPatientId = otherPatient.path("id").asLong();
    LocalDate homeToday = LocalDate.now(zoneForDepartment(homeDepartmentId));
    LocalDate otherToday = LocalDate.now(zoneForDepartment(otherDepartmentId));

    var homeResult = audit("admin", homeDepartmentId,
        filters(actorId, otherPatientId, homeToday, homeToday));
    var otherResult = audit("admin", otherDepartmentId,
        filters(actorId, otherPatientId, otherToday, otherToday));
    assertThat(homeResult.path("total").asLong()).isZero();
    assertThat(homeResult.path("events").size()).isZero();
    assertThat(otherResult.path("total").asLong()).isEqualTo(1);
    assertThat(otherResult.path("events").size()).isEqualTo(1);
    assertThat(otherResult.path("events").get(0).path("entityId").asLong()).isEqualTo(otherPatientId);

    request("staff", "GET", "/api/v1/audit", null).andExpect(status().isForbidden());
  }

  private JsonNode audit(String user, long departmentId, String query) throws Exception {
    return result(request(user, "GET", "/api/v1/audit" + query, null, departmentId), 200);
  }

  private JsonNode auditWithParams(String username, long departmentId, Map<String, String> params)
      throws Exception {
    var request = get("/api/v1/audit")
        .with(user(username))
        .header("X-Department-Id", Long.toString(departmentId));
    params.forEach(request::param);
    return result(mvc.perform(request), 200);
  }

  private String filters(long actorId, long entityId, LocalDate from, LocalDate to) {
    return "?page=0&size=1"
        + "&eventType=patient_created&actorId=" + actorId
        + "&entityType=pAtIeNt&entityId=" + entityId
        + "&source=ui&from=" + from + "&to=" + to;
  }

  private long departmentForPatient(long patientId) {
    return jdbc.queryForObject("select department_id from patients where id=?", Long.class, patientId);
  }

  private ZoneId zoneForDepartment(long departmentId) {
    String zone = jdbc.queryForObject(
        "select time_zone from departments where id=?", String.class, departmentId);
    return ZoneId.of(zone);
  }

  private void insertAuditEvent(long departmentId, long actorId, String eventType,
      String entityType, long entityId, String source, Instant timestamp) {
    jdbc.update("""
        insert into audit_events
          (department_id, user_id, event_type, entity_type, entity_id, source, timestamp, metadata)
        values (?, ?, ?, ?, ?, ?, ?, '{}')
        """, departmentId, actorId, eventType, entityType, entityId, source,
        Timestamp.from(timestamp));
  }
}
