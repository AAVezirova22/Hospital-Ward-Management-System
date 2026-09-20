package com.example.hospital.api;

import com.example.hospital.api.Inputs.*;
import com.example.hospital.repository.AuditEventRepository;
import com.example.hospital.security.Actor;
import com.example.hospital.service.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HospitalController {
  private final HospitalService h;
  private final UserService users;
  private final AuditEventRepository audit;
  private final Actor actor;

  public HospitalController(HospitalService h, UserService u, AuditEventRepository a, Actor actor) {
    this.h = h;
    users = u;
    audit = a;
    this.actor = actor;
  }

  @GetMapping("/patients")
  public Object patients(@RequestParam(defaultValue = "") String q) {
    return h.patients(q);
  }

  @GetMapping("/patients/{id}")
  public Object patient(@PathVariable Long id) {
    return h.summary(id);
  }

  @PostMapping("/patients")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addPatient(@Valid @RequestBody PatientInput in) {
    return h.savePatient(null, in);
  }

  @PutMapping("/patients/{id}")
  public Object editPatient(@PathVariable Long id, @Valid @RequestBody PatientInput in) {
    return h.savePatient(id, in);
  }

  @GetMapping("/doctors")
  public Object doctors() {
    return h.doctors();
  }

  @PostMapping("/doctors")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addDoctor(@Valid @RequestBody DoctorInput in) {
    return h.saveDoctor(null, in);
  }

  @PutMapping("/doctors/{id}")
  public Object editDoctor(@PathVariable Long id, @Valid @RequestBody DoctorInput in) {
    return h.saveDoctor(id, in);
  }

  @GetMapping("/rooms")
  public Object rooms(@RequestParam(defaultValue = "0") int minFree) {
    return h.rooms(minFree);
  }

  @PostMapping("/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addRoom(@Valid @RequestBody RoomInput in) {
    return h.saveRoom(null, in);
  }

  @PutMapping("/rooms/{id}")
  public Object editRoom(@PathVariable Long id, @Valid @RequestBody RoomInput in) {
    return h.saveRoom(id, in);
  }

  @GetMapping("/procedures")
  public Object procedures() {
    return h.procedures();
  }

  @PostMapping("/procedures")
  @ResponseStatus(HttpStatus.CREATED)
  public Object addProcedure(@Valid @RequestBody ProcedureInput in) {
    return h.saveProcedure(null, in);
  }

  @PutMapping("/procedures/{id}")
  public Object editProcedure(@PathVariable Long id, @Valid @RequestBody ProcedureInput in) {
    return h.saveProcedure(id, in);
  }

  @GetMapping("/admissions")
  public Object admissions() {
    return h.admissions().stream().map(h::admissionView).toList();
  }

  @GetMapping("/admissions/{id}")
  public Object admission(@PathVariable Long id) {
    return h.admissionView(h.admission(id));
  }

  @PostMapping("/admissions")
  @ResponseStatus(HttpStatus.CREATED)
  public Object admit(@Valid @RequestBody AdmissionInput in) {
    return h.admit(in, "UI");
  }

  @PostMapping("/admissions/{id}/transfer")
  public Object transfer(@PathVariable Long id, @Valid @RequestBody TransferInput in) {
    return h.transfer(id, in, "UI");
  }

  @PostMapping("/admissions/{id}/discharge")
  public Object discharge(@PathVariable Long id, @Valid @RequestBody DischargeInput in) {
    return h.discharge(id, in.version(), "UI");
  }

  public record DoctorChange(
      @jakarta.validation.constraints.NotNull Long doctorId,
      @jakarta.validation.constraints.NotNull Long version) {}

  @PostMapping("/admissions/{id}/doctor")
  public Object doctor(@PathVariable Long id, @Valid @RequestBody DoctorChange in) {
    return h.changeDoctor(id, in.doctorId(), in.version());
  }

  @PostMapping("/admissions/{id}/procedures")
  @ResponseStatus(HttpStatus.CREATED)
  public Object record(@PathVariable Long id, @Valid @RequestBody RecordProcedureInput in) {
    return h.recordProcedure(id, in);
  }

  @GetMapping("/reports/dashboard")
  public Object dashboard() {
    return h.dashboard();
  }

  @GetMapping("/reports/census")
  public Object census(
      @RequestParam(required = false) Long roomId, @RequestParam(required = false) Long doctorId) {
    return h.census(roomId, doctorId);
  }

  @GetMapping("/reports/capacity")
  public Object capacity() {
    return h.rooms(0);
  }

  @GetMapping("/reports/procedures")
  public Object report(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) Long patientId,
      @RequestParam(required = false) Long doctorId) {
    return h.procedureReport(from, to, patientId, doctorId);
  }

  @GetMapping(value = "/reports/procedures.csv", produces = "text/csv")
  public ResponseEntity<String> csv(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) Long patientId,
      @RequestParam(required = false) Long doctorId) {
    var report = h.procedureReport(from, to, patientId, doctorId);
    long departmentId = com.example.hospital.security.DepartmentContext.id();
    var out = new StringBuilder("Department,Record,Admission,Procedure,Performed at,Cost EUR\r\n");
    for (Object o : (List<?>) report.get("rows")) {
      var row = (Map<?, ?>) o;
      var r = (com.example.hospital.domain.PerformedProcedure) row.get("record");
      out.append(
          departmentId
              + ","
              + r.id
              + ","
              + r.admissionId
              + ","
              + r.medicalProcedureId
              + ","
              + r.performedAt
              + ","
              + r.priceAtExecution
              + "\r\n");
    }
    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=procedure-report-department-" + departmentId + ".csv")
        .body(out.toString());
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  public Object users() {
    return users.list();
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@Valid @RequestBody UserInput in) {
    return users.save(null, in);
  }

  @PutMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object user(@PathVariable Long id, @Valid @RequestBody UserInput in) {
    return users.save(id, in);
  }

  @GetMapping("/audit")
  @PreAuthorize("hasRole('ADMIN')")
  public Object audit() {
    return audit
        .findAll(
            org.springframework.data.domain.PageRequest.of(
                0, 100, org.springframework.data.domain.Sort.by("timestamp").descending()))
        .getContent();
  }
}
