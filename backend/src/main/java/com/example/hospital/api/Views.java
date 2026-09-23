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
      row.put("id", p.getId());
      row.put("patientIdentifier", p.getPatientIdentifier());
      row.put("firstName", p.getFirstName());
      row.put("lastName", p.getLastName());
      row.put("dateOfBirth", p.getDateOfBirth());
      return row;
    }
  }

  public static Map<String, Object> patient(Patient p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.getId());
    row.put("version", p.getVersion());
    row.put("patientIdentifier", p.getPatientIdentifier());
    row.put("firstName", p.getFirstName());
    row.put("lastName", p.getLastName());
    row.put("dateOfBirth", p.getDateOfBirth());
    row.put("address", p.getAddress());
    row.put("phoneNumber", p.getPhoneNumber());
    return row;
  }

  public static Map<String, Object> doctor(Doctor d) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", d.getId());
    row.put("version", d.getVersion());
    row.put("doctorIdentifier", d.getDoctorIdentifier());
    row.put("firstName", d.getFirstName());
    row.put("lastName", d.getLastName());
    row.put("specialty", d.getSpecialty());
    row.put("active", d.isActive());
    return row;
  }

  public static Map<String, Object> procedure(MedicalProcedure p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.getId());
    row.put("version", p.getVersion());
    row.put("procedureCode", p.getProcedureCode());
    row.put("procedureName", p.getProcedureName());
    row.put("currentCost", p.getCurrentCost());
    row.put("active", p.isActive());
    return row;
  }

  public static Map<String, Object> admission(Admission a) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", a.getId());
    row.put("version", a.getVersion());
    row.put("admissionNumber", a.getAdmissionNumber());
    row.put("patientId", a.getPatientId());
    row.put("attendingDoctorId", a.getAttendingDoctorId());
    row.put("admissionDateTime", a.getAdmissionDateTime());
    row.put("dischargeDateTime", a.getDischargeDateTime());
    row.put("expectedDischargeDate", a.getExpectedDischargeDate());
    row.put("status", a.getStatus());
    return row;
  }

  public static Map<String, Object> performed(PerformedProcedure p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.getId());
    row.put("admissionId", p.getAdmissionId());
    row.put("medicalProcedureId", p.getMedicalProcedureId());
    row.put("performedByDoctorId", p.getPerformedByDoctorId());
    row.put("performedAt", p.getPerformedAt());
    row.put("priceAtExecution", p.getPriceAtExecution());
    row.put("note", p.getNote());
    return row;
  }

  public static Map<String, Object> room(Room r) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", r.getId());
    row.put("version", r.getVersion());
    row.put("roomNumber", r.getRoomNumber());
    row.put("bedCount", r.getBedCount());
    row.put("active", r.isActive());
    return row;
  }

  public static Map<String, Object> bedHold(BedHold hold) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", hold.getId());
    row.put("version", hold.getVersion());
    row.put("createdAt", hold.getCreatedAt());
    row.put("updatedAt", hold.getUpdatedAt());
    row.put("roomId", hold.getRoomId());
    row.put("bedCount", hold.getBedCount());
    row.put("reason", hold.getReason());
    row.put("startsAt", hold.getStartsAt());
    row.put("endsAt", hold.getEndsAt());
    row.put("createdBy", hold.getCreatedBy());
    row.put("cancelledAt", hold.getCancelledAt());
    return row;
  }

  public static Map<String, Object> assignment(RoomAssignment a) {
    if (a == null) return null;
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", a.getId());
    row.put("version", a.getVersion());
    row.put("admissionId", a.getAdmissionId());
    row.put("roomId", a.getRoomId());
    row.put("assignedAt", a.getAssignedAt());
    row.put("releasedAt", a.getReleasedAt());
    row.put("reason", a.getReason());
    row.put("createdBy", a.getCreatedBy());
    return row;
  }

  public static Map<String, Object> account(AppUser u) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", u.getId());
    row.put("version", u.getVersion());
    row.put("username", u.getUsername());
    row.put("role", u.getRole());
    row.put("enabled", u.isEnabled());
    row.put("doctorId", u.getDoctorId());
    row.put("patientId", u.getPatientId());
    row.put("email", u.getEmail());
    row.put("emailVerified", u.isEmailVerified());
    row.put("requestedRole", u.getRequestedRole());
    row.put("accountRole", u.getAccountRole());
    row.put("departmentRole", u.getDepartmentRole());
    return row;
  }

  public static Map<String, Object> audit(AuditEvent e) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", e.getId());
    row.put("userId", e.getUserId());
    row.put("eventType", e.getEventType());
    row.put("entityType", e.getEntityType());
    row.put("entityId", e.getEntityId());
    row.put("source", e.getSource());
    row.put("timestamp", e.getTimestamp());
    row.put("metadata", e.getMetadata());
    return row;
  }

  public static Map<String, Object> pendingAction(AiPendingAction a) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", a.getId());
    row.put("version", a.getVersion());
    row.put("userId", a.getUserId());
    row.put("actionType", a.getActionType());
    row.put("payload", a.getPayload());
    row.put("status", a.getStatus());
    row.put("expiresAt", a.getExpiresAt());
    row.put("confirmedAt", a.getConfirmedAt());
    return row;
  }
}

