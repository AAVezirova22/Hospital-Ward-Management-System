package com.example.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcedureCsvReadableExportTest extends HospitalSupport {
  @Test
  void exportKeepsStableIdsAndAddsEscapedReadableNamesAndAdmissionReference() throws Exception {
    String marker = unique();
    String patientIdentifier = "CSV-PATIENT-" + marker;
    String doctorIdentifier = "CSV-DOCTOR-" + marker;
    String procedureCode = "CSV-PROC-" + marker;
    String patientFirst = "=1+1";
    String patientLast = "Patient, \"Quoted\"\r\nNext";
    String doctorFirst = "+SUM(1,2)";
    String doctorLast = "Doctor, \"Quoted\"\r\nNext";
    String procedureName = "@SUM(A1:A2), \"MRI\"\r\nNext";

    var patient = result(request("admin", "POST", "/api/v1/patients", Map.of(
        "patientIdentifier", patientIdentifier,
        "firstName", patientFirst,
        "lastName", patientLast,
        "dateOfBirth", "1980-01-01")), 201);
    var doctor = result(request("admin", "POST", "/api/v1/doctors", Map.of(
        "doctorIdentifier", doctorIdentifier,
        "firstName", doctorFirst,
        "lastName", doctorLast,
        "specialty", "General",
        "active", true)), 201);
    var procedure = result(request("admin", "POST", "/api/v1/procedures", Map.of(
        "procedureCode", procedureCode,
        "procedureName", procedureName,
        "currentCost", 37.25,
        "active", true)), 201);
    var room = room(1);
    var admission = result(request("admin", "POST", "/api/v1/admissions", Map.of(
        "patientId", patient.path("id").asLong(),
        "doctorId", doctor.path("id").asLong(),
        "roomId", room.path("id").asLong())), 201);
    var performedAt = Instant.now().toString();
    var record = result(request("admin", "POST", "/api/v1/admissions/" + admission.path("id").asLong()
        + "/procedures", Map.of(
            "medicalProcedureId", procedure.path("id").asLong(),
            "doctorId", doctor.path("id").asLong(),
            "performedAt", performedAt)), 201);

    var response = request("admin", "GET",
        "/api/v1/reports/procedures.csv?from=2020-01-01&to=2030-01-01&patientId="
            + patient.path("id").asLong(), null)
        .andExpect(status().isOk()).andReturn().getResponse();
    String csv = response.getContentAsString();
    var jsonReport = result(request("admin", "GET",
        "/api/v1/reports/procedures?from=2020-01-01&to=2030-01-01&patientId="
            + patient.path("id").asLong(), null), 200);
    var reportRecord = jsonReport.path("rows").get(0).path("record");
    assertThat(reportRecord.path("admissionNumber").asText())
        .isEqualTo(admission.path("admissionNumber").asText());
    List<List<String>> parsed = parseCsv(csv);
    assertThat(parsed).hasSize(2);
    assertThat(parsed.get(0)).containsExactly(
        "Department", "Record", "Admission", "Procedure", "Performed at", "Cost EUR",
        "Admission number", "Patient ID", "Patient identifier", "Patient name", "Doctor ID",
        "Doctor identifier", "Doctor name", "Procedure code", "Procedure name");
    long departmentId = jdbc.queryForObject(
        "select department_id from rooms where id=?", Long.class, room.path("id").asLong());
    assertThat(parsed.get(1)).containsExactly(
        Long.toString(departmentId),
        record.path("id").asText(),
        admission.path("id").asText(),
        procedure.path("id").asText(),
        reportRecord.path("performedAt").asText(),
        "37.25",
        reportRecord.path("admissionNumber").asText(),
        patient.path("id").asText(),
        patientIdentifier,
        "'=1+1 Patient, \"Quoted\"\r\nNext",
        doctor.path("id").asText(),
        doctorIdentifier,
        "'+SUM(1,2) Doctor, \"Quoted\"\r\nNext",
        procedureCode,
        "'@SUM(A1:A2), \"MRI\"\r\nNext");
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
