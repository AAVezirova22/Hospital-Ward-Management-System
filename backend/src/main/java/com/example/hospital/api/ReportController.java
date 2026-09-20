package com.example.hospital.api;

import com.example.hospital.service.HospitalService;
import com.example.hospital.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ReportService reports;
  private final HospitalService hospital;

  public ReportController(ReportService reports, HospitalService hospital) {
    this.reports = reports;
    this.hospital = hospital;
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
    return hospital.rooms(0);
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
    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=procedure-report-department-" + departmentId + ".csv")
        .body(out.toString());
  }
}
