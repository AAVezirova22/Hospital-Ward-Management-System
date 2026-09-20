package com.example.hospital.api;

import com.example.hospital.domain.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Views {
  private Views() {}

  public static Map<String, Object> PatientDirectory(Patient p) {
    return PatientDirectory.of(p);
  }

  public record PatientDirectory(
      Long id, String patientIdentifier, String firstName, String lastName, LocalDate dateOfBirth) {
    public static Map<String, Object> of(Patient p) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", p.id);
      row.put("patientIdentifier", p.patientIdentifier);
      row.put("firstName", p.firstName);
      row.put("lastName", p.lastName);
      row.put("dateOfBirth", p.dateOfBirth);
      return row;
    }
  }

  public static Map<String, Object> patient(Patient p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.id);
    row.put("version", p.version);
    row.put("patientIdentifier", p.patientIdentifier);
    row.put("firstName", p.firstName);
    row.put("lastName", p.lastName);
    row.put("dateOfBirth", p.dateOfBirth);
    row.put("address", p.address);
    row.put("phoneNumber", p.phoneNumber);
    return row;
  }

  public static Map<String, Object> doctor(Doctor d) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", d.id);
    row.put("version", d.version);
    row.put("doctorIdentifier", d.doctorIdentifier);
    row.put("firstName", d.firstName);
    row.put("lastName", d.lastName);
    row.put("specialty", d.specialty);
    row.put("active", d.active);
    return row;
  }

  public static Map<String, Object> procedure(MedicalProcedure p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.id);
    row.put("version", p.version);
    row.put("procedureCode", p.procedureCode);
    row.put("procedureName", p.procedureName);
    row.put("currentCost", p.currentCost);
    row.put("active", p.active);
    return row;
  }

  public static Map<String, Object> admission(Admission a) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", a.id);
    row.put("version", a.version);
    row.put("admissionNumber", a.admissionNumber);
    row.put("patientId", a.patientId);
    row.put("attendingDoctorId", a.attendingDoctorId);
    row.put("admissionDateTime", a.admissionDateTime);
    row.put("dischargeDateTime", a.dischargeDateTime);
    row.put("expectedDischargeDate", a.expectedDischargeDate);
    row.put("status", a.status);
    return row;
  }

  public static Map<String, Object> performed(PerformedProcedure p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.id);
    row.put("admissionId", p.admissionId);
    row.put("medicalProcedureId", p.medicalProcedureId);
    row.put("performedByDoctorId", p.performedByDoctorId);
    row.put("performedAt", p.performedAt);
    row.put("priceAtExecution", p.priceAtExecution);
    row.put("note", p.note);
    return row;
  }

  public static Map<String, Object> room(Room r) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", r.id);
    row.put("version", r.version);
    row.put("roomNumber", r.roomNumber);
    row.put("bedCount", r.bedCount);
    row.put("active", r.active);
    return row;
  }

  public static Map<String, Object> assignment(RoomAssignment a) {
    if (a == null) return null;
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", a.id);
    row.put("version", a.version);
    row.put("admissionId", a.admissionId);
    row.put("roomId", a.roomId);
    row.put("assignedAt", a.assignedAt);
    row.put("releasedAt", a.releasedAt);
    row.put("reason", a.reason);
    row.put("createdBy", a.createdBy);
    return row;
  }

  public static Map<String, Object> account(AppUser u) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", u.id);
    row.put("version", u.version);
    row.put("username", u.username);
    row.put("role", u.role);
    row.put("enabled", u.enabled);
    row.put("doctorId", u.doctorId);
    row.put("patientId", u.patientId);
    row.put("email", u.email);
    row.put("emailVerified", u.emailVerified);
    row.put("requestedRole", u.requestedRole);
    row.put("accountRole", u.accountRole);
    row.put("departmentRole", u.departmentRole);
    return row;
  }

  public static Map<String, Object> audit(AuditEvent e) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", e.id);
    row.put("userId", e.userId);
    row.put("eventType", e.eventType);
    row.put("entityType", e.entityType);
    row.put("entityId", e.entityId);
    row.put("source", e.source);
    row.put("timestamp", e.timestamp);
    row.put("metadata", e.metadata);
    return row;
  }
}

