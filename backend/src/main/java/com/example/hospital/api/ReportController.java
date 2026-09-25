package com.example.hospital.api;

import com.example.hospital.service.AuditService;
import com.example.hospital.service.ReportService;
import com.example.hospital.service.DischargeReminderService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ReportService reports;
  private final AuditService audit;
  private final DischargeReminderService reminders;

public ReportController(
    ReportService reports,
    AuditService audit,
    DischargeReminderService reminders) {
  this.reports = reports;
  this.audit = audit;
  this.reminders = reminders;
}

  @GetMapping("/dashboard")
  public Object dashboard() {
    return reports.dashboard();
  }

  @GetMapping("/census")
  public Object census(
      @RequestParam(required = false) Long roomId, @RequestParam(required = false) Long doctorId) {
    return reports.census(roomId, doctorId);
  }

  @GetMapping("/capacity")
  public Object capacity() {
    return reports.capacity();
  }

  @GetMapping(value = "/census.csv", produces = "text/csv")
  public ResponseEntity<String> censusCsv(
      @RequestParam(required = false) Long roomId,
      @RequestParam(required = false) Long doctorId) {
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    List<Map<String, Object>> admissions = reports.census(roomId, doctorId);
    var out = new StringBuilder(
        "Department,Admission ID,Admission number,Status,Admitted at,Expected discharge,Patient ID,Patient identifier,Patient name,Doctor ID,Doctor identifier,Doctor name,Current room ID,Current room\r\n");
    for (var view : admissions) {
      var admission = (Map<?, ?>) view.get("admission");
      var patient = (Map<?, ?>) view.get("patient");
      var doctor = (Map<?, ?>) view.get("doctor");
      var assignment = (Map<?, ?>) view.get("assignment");
      Map<?, ?> room = null;
      if (assignment != null) {
        for (Object historyEntry : (List<?>) view.get("rooms")) {
          var history = (Map<?, ?>) historyEntry;
          var historyAssignment = (Map<?, ?>) history.get("assignment");
          if (java.util.Objects.equals(historyAssignment.get("id"), assignment.get("id"))) {
            room = (Map<?, ?>) history.get("room");
            break;
          }
        }
      }
      out.append(departmentId).append(',')
          .append(csvValue(admission.get("id"))).append(',')
          .append(csvText(admission.get("admissionNumber"))).append(',')
          .append(csvText(admission.get("status"))).append(',')
          .append(csvValue(admission.get("admissionDateTime"))).append(',')
          .append(csvValue(admission.get("expectedDischargeDate"))).append(',')
          .append(csvValue(patient.get("id"))).append(',')
          .append(csvText(patient.get("patientIdentifier"))).append(',')
          .append(csvText(readableName(patient.get("firstName"), patient.get("lastName")))).append(',')
          .append(csvValue(doctor.get("id"))).append(',')
          .append(csvText(doctor.get("doctorIdentifier"))).append(',')
          .append(csvText(readableName(doctor.get("firstName"), doctor.get("lastName")))).append(',')
          .append(csvValue(assignment == null ? null : assignment.get("roomId"))).append(',')
          .append(csvText(room == null ? null : room.get("roomNumber"))).append("\r\n");
    }
    var filters = new java.util.LinkedHashMap<String, Object>();
    filters.put("export", "census.csv");
    filters.put("roomId", roomId);
    filters.put("doctorId", doctorId);
    filters.put("rows", admissions.size());
    audit.log("DATA_EXPORTED", "Report", null, "UI", filters);
    String suffix = (roomId == null ? "" : "-room-" + roomId)
        + (doctorId == null ? "" : "-doctor-" + doctorId);
    return csvResponse(out, "census-report-department-" + departmentId + "-" + LocalDate.now() + suffix + ".csv");
  }

  @GetMapping(value = "/capacity.csv", produces = "text/csv")
  public ResponseEntity<String> capacityCsv() {
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    List<Map<String, Object>> rooms = reports.capacity();
    var out = new StringBuilder(
        "Department,Room ID,Room number,Active,Bed count,Occupied beds,Held beds,Available beds,Capabilities\r\n");
    for (var room : rooms) {
      out.append(departmentId).append(',')
          .append(csvValue(room.get("id"))).append(',')
          .append(csvText(room.get("roomNumber"))).append(',')
          .append(csvValue(room.get("active"))).append(',')
          .append(csvValue(room.get("bedCount"))).append(',')
          .append(csvValue(room.get("occupiedBeds"))).append(',')
          .append(csvValue(room.get("heldBeds"))).append(',')
          .append(csvValue(room.get("availableBeds"))).append(',')
          .append(csvText(room.get("capabilities"))).append("\r\n");
    }
    var filters = new java.util.LinkedHashMap<String, Object>();
    filters.put("export", "capacity.csv");
    filters.put("rows", rooms.size());
    audit.log("DATA_EXPORTED", "Report", null, "UI", filters);
    return csvResponse(out, "capacity-report-department-" + departmentId + "-" + LocalDate.now() + ".csv");
  }

  private static ResponseEntity<String> csvResponse(StringBuilder csv, String filename) {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=" + filename)
        .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(csv.toString());
  }

  private static String csvValue(Object value) {
    if (value instanceof Number || value instanceof Boolean) return value.toString();
    return csvText(value);
  }

  @GetMapping("/doctor-workload")
  public Object doctorWorkload(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return reports.doctorWorkload(from, to);
  }

  @GetMapping("/room-utilization")
  public Object roomUtilization(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam String bucket,
      @RequestParam(required = false) Long roomId,
      @RequestParam(required = false) Long patientId) {
    return reports.roomUtilization(from, to, bucket, roomId, patientId);
  }

  @GetMapping("/discharge-reminders")
  @PreAuthorize("hasAnyRole('ADMIN', 'MEDICAL_STAFF', 'DOCTOR')")
  public Object dischargeReminders() {
    return reminders.recentOutcomes();
  }

  @GetMapping("/procedures")
  public Object report(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) Long patientId,
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) Long medicalProcedureId) {
    return reports.procedures(from, to, patientId, doctorId, medicalProcedureId);
  }

  @GetMapping(value = "/procedures.csv", produces = "text/csv")
  public ResponseEntity<String> csv(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) Long patientId,
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) Long medicalProcedureId) {
    var report = reports.procedures(from, to, patientId, doctorId, medicalProcedureId);
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    var rows = (List<?>) report.get("rows");
    var out = new StringBuilder(
        "Department,Record,Admission,Procedure,Performed at,Cost EUR,Admission number,Patient ID,Patient identifier,Patient name,Doctor ID,Doctor identifier,Doctor name,Procedure code,Procedure name\r\n");
    for (Object o : rows) {
      var row = (Map<?, ?>) o;
      var r = (Map<?, ?>) row.get("record");
      var patient = (Map<?, ?>) row.get("patient");
      var doctor = (Map<?, ?>) row.get("doctor");
      var procedure = (Map<?, ?>) row.get("procedure");
      out.append(departmentId).append(',')
          .append(r.get("id")).append(',')
          .append(r.get("admissionId")).append(',')
          .append(r.get("medicalProcedureId")).append(',')
          .append(r.get("performedAt")).append(',')
          .append(r.get("priceAtExecution")).append(',')
          .append(csvText(r.get("admissionNumber"))).append(',')
          .append(patient.get("id")).append(',')
          .append(csvText(patient.get("patientIdentifier"))).append(',')
          .append(csvText(readableName(patient.get("firstName"), patient.get("lastName")))).append(',')
          .append(doctor.get("id")).append(',')
          .append(csvText(doctor.get("doctorIdentifier"))).append(',')
          .append(csvText(readableName(doctor.get("firstName"), doctor.get("lastName")))).append(',')
          .append(csvText(procedure.get("procedureCode"))).append(',')
          .append(csvText(procedure.get("procedureName"))).append("\r\n");
    }
    // Records who exported what scope; the exported rows themselves are never stored.
    var filters = new java.util.LinkedHashMap<String, Object>();
    filters.put("export", "procedures.csv");
    filters.put("from", from);
    filters.put("to", to);
    filters.put("patientId", patientId);
    filters.put("doctorId", doctorId);
    filters.put("medicalProcedureId", medicalProcedureId);
    filters.put("rows", ((List<?>) report.get("rows")).size());
    audit.log("DATA_EXPORTED", "Report", null, "UI", filters);
    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=procedure-report-department-" + departmentId + ".csv")
        .body(out.toString());
  }

  private static String readableName(Object first, Object last) {
    return ((first == null ? "" : first.toString()) + " " + (last == null ? "" : last.toString())).trim();
  }

  private static String csvText(Object value) {
    String cell = value == null ? "" : value.toString();
    if (beginsWithSpreadsheetFormula(cell)) cell = "'" + cell;
    return "\"" + cell.replace("\"", "\"\"") + "\"";
  }

  private static boolean beginsWithSpreadsheetFormula(String value) {
    int index = 0;
    while (index < value.length()) {
      char c = value.charAt(index);
      if (!Character.isWhitespace(c) && !Character.isISOControl(c)) break;
      index++;
    }
    if (index == value.length()) return false;
    char first = value.charAt(index);
    return first == '=' || first == '+' || first == '-' || first == '@';
  }
}
