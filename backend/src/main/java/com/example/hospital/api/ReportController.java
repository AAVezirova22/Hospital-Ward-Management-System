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
      @RequestParam(required = false) Long doctorId) {
    return reports.procedures(from, to, patientId, doctorId);
  }

  @GetMapping(value = "/procedures.csv", produces = "text/csv")
  public ResponseEntity<String> csv(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) Long patientId,
      @RequestParam(required = false) Long doctorId) {
    var report = reports.procedures(from, to, patientId, doctorId);
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    var out = new StringBuilder("Department,Record,Admission,Procedure,Performed at,Cost EUR\r\n");
    for (Object o : (List<?>) report.get("rows")) {
      var row = (Map<?, ?>) o;
      var r = (Map<?, ?>) row.get("record");
      out.append(
          departmentId
              + ","
              + r.get("id")
              + ","
              + r.get("admissionId")
              + ","
              + r.get("medicalProcedureId")
              + ","
              + r.get("performedAt")
              + ","
              + r.get("priceAtExecution")
              + "\r\n");
    }
    // Records who exported what scope; the exported rows themselves are never stored.
    var filters = new java.util.LinkedHashMap<String, Object>();
    filters.put("export", "procedures.csv");
    filters.put("from", from);
    filters.put("to", to);
    filters.put("patientId", patientId);
    filters.put("doctorId", doctorId);
    filters.put("rows", ((List<?>) report.get("rows")).size());
    audit.log("DATA_EXPORTED", "Report", null, "UI", filters);
    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=procedure-report-department-" + departmentId + ".csv")
        .body(out.toString());
  }
}
