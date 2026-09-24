package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditExportIntegrationTest extends HospitalSupport {
  private static final String CSV_HEADER =
      "Department ID,Audit ID,Actor ID,Event type,Entity type,Entity ID,Source,Timestamp,Metadata";

  @Test
  void csvExportMatchesFilteredAuditPageAndHonorsDepartmentScope() throws Exception {
    var homePatient = createPatient();
    long homeDepartmentId = departmentForPatient(homePatient.path("id").asLong());
    long actorId = users.findByUsername("admin").orElseThrow().getId();
    LocalDate homeDate = LocalDate.now(zoneForDepartment(homeDepartmentId));
    String homeFilters = filters(actorId, homePatient.path("id").asLong(), homeDate, homeDate);

    var page = result(request("admin", "GET", "/api/v1/audit" + homeFilters, null, homeDepartmentId), 200);
    var homeCsv = export("admin", homeDepartmentId, homeFilters, 10);
    var homeRows = parseCsv(homeCsv);
    assertThat(page.path("total").asLong()).isEqualTo(1);
    assertThat(homeRows).hasSize(2);
    assertThat(homeRows.get(0)).containsExactly(
        "Department ID", "Audit ID", "Actor ID", "Event type", "Entity type", "Entity ID",
        "Source", "Timestamp", "Metadata");
    assertThat(homeRows.get(1)).containsExactly(
        Long.toString(homeDepartmentId),
        page.path("events").get(0).path("id").asText(),
        Long.toString(actorId),
        "PATIENT_CREATED",
        "Patient",
        homePatient.path("id").asText(),
        "UI",
        page.path("events").get(0).path("timestamp").asText(),
        page.path("events").get(0).path("metadata").asText());
    var exportAudit = result(request("admin", "GET",
        "/api/v1/audit?eventType=DATA_EXPORTED&page=0&size=1", null, homeDepartmentId), 200);
    String exportAuditEntry = exportAudit.path("events").get(0).toString();
    assertThat(exportAudit.path("events").get(0).path("metadata").asText())
        .contains("audit.csv", "rows=1", "entityId=" + homePatient.path("id").asLong());
    assertThat(exportAuditEntry)
        .doesNotContain(
            homePatient.path("patientIdentifier").asText(), CSV_HEADER, "PATIENT_CREATED");

    var otherDepartment = result(request("admin", "POST", "/api/v1/workspaces/hospitals", Map.of(
        "name", "Audit Export Clinic " + unique(), "departmentName", "Imaging"), homeDepartmentId), 201);
    long otherDepartmentId = otherDepartment.path("departmentId").asLong();
    var otherPatient = result(request("admin", "POST", "/api/v1/patients", Map.of(
        "patientIdentifier", "AUDIT-EXPORT-" + unique(),
        "firstName", "Scoped",
        "lastName", "Export",
        "dateOfBirth", "1980-01-01"), otherDepartmentId), 201);
    LocalDate otherDate = LocalDate.now(zoneForDepartment(otherDepartmentId));
    String otherFilters = filters(actorId, otherPatient.path("id").asLong(), otherDate, otherDate);

    var hidden = parseCsv(export("admin", homeDepartmentId, otherFilters, 10));
    var visible = parseCsv(export("admin", otherDepartmentId, otherFilters, 10));
    assertThat(hidden).hasSize(1);
    assertThat(visible).hasSize(2);
    assertThat(visible.get(1).get(0)).isEqualTo(Long.toString(otherDepartmentId));
    assertThat(visible.get(1).get(5)).isEqualTo(otherPatient.path("id").asText());
    request("staff", "GET", "/api/v1/audit/export.csv", null)
        .andExpect(status().isForbidden());
  }

  @Test
  void exportRejectsRequestsOverTheExplicitBound() throws Exception {
    var firstPatient = createPatient();
    createPatient();
    long departmentId = departmentForPatient(firstPatient.path("id").asLong());
    long actorId = users.findByUsername("admin").orElseThrow().getId();
    LocalDate today = LocalDate.now(zoneForDepartment(departmentId));
    String query = filters(actorId, null, today, today);

    request("admin", "GET", "/api/v1/audit/export.csv" + query + "&limit=1", null, departmentId)
        .andExpect(status().isBadRequest());
    request("admin", "GET", "/api/v1/audit/export.csv?limit=1001", null, departmentId)
        .andExpect(status().isBadRequest());
  }

  @Test
  void csvQuotesFormulaProtectsAndEscapesAuditTextFields() throws Exception {
    var patient = createPatient();
    long departmentId = departmentForPatient(patient.path("id").asLong());
    long actorId = users.findByUsername("admin").orElseThrow().getId();
    long entityId = patient.path("id").asLong() + 2_000_000_000L;
    Instant timestamp = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.MICROS);
    String formulaEvent = "=SUM(1,2)";
    String quotedEntity = "Ward, \"quoted\"\r\nnext";
    String formulaSource = "@source";
    String formulaMetadata = "+1, \"metadata\"\r\nnext";
    long eventId = jdbc.queryForObject("""
        insert into audit_events
          (department_id, user_id, event_type, entity_type, entity_id, source, timestamp, metadata)
        values (?, ?, ?, ?, ?, ?, ?, ?) returning id
        """, Long.class, departmentId, actorId, formulaEvent, quotedEntity, entityId,
        formulaSource, Timestamp.from(timestamp), formulaMetadata);
    LocalDate day = timestamp.atZone(zoneForDepartment(departmentId)).toLocalDate();

    var rows = parseCsv(export("admin", departmentId,
        "?entityId=" + entityId + "&from=" + day + "&to=" + day, 10));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(1)).containsExactly(
        Long.toString(departmentId),
        Long.toString(eventId),
        Long.toString(actorId),
        "'" + formulaEvent,
        quotedEntity,
        Long.toString(entityId),
        "'" + formulaSource,
        timestamp.toString(),
        "'" + formulaMetadata);
  }

  private String export(String username, long departmentId, String filters, int limit) throws Exception {
    var response = request(username, "GET", "/api/v1/audit/export.csv" + filters + "&limit=" + limit,
        null, departmentId).andExpect(status().isOk()).andReturn().getResponse();
    assertThat(response.getContentType()).startsWith("text/csv");
    assertThat(response.getHeader("Content-Disposition"))
        .contains("audit-department-" + departmentId + ".csv");
    return response.getContentAsString();
  }

  private String filters(long actorId, Long entityId, LocalDate from, LocalDate to) {
    return "?eventType=patient_created&actorId=" + actorId
        + "&entityType=pAtIeNt&source=ui&from=" + from + "&to=" + to
        + (entityId == null ? "" : "&entityId=" + entityId);
  }

  private long departmentForPatient(long patientId) {
    return jdbc.queryForObject("select department_id from patients where id=?", Long.class, patientId);
  }

  private ZoneId zoneForDepartment(long departmentId) {
    return ZoneId.of(jdbc.queryForObject(
        "select time_zone from departments where id=?", String.class, departmentId));
  }

  private List<List<String>> parseCsv(String csv) {
    var records = new ArrayList<List<String>>();
    var record = new ArrayList<String>();
    var field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          field.append(c);
        }
      } else if (c == '"' && field.isEmpty()) {
        quoted = true;
      } else if (c == ',') {
        record.add(field.toString());
        field.setLength(0);
      } else if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
        record.add(field.toString());
        records.add(record);
        record = new ArrayList<>();
        field.setLength(0);
        i++;
      } else {
        field.append(c);
      }
    }
    if (!field.isEmpty() || !record.isEmpty()) {
      record.add(field.toString());
      records.add(record);
    }
    return records;
  }
}
