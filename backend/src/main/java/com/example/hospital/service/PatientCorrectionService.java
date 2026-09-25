package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientCorrectionService {
  public record RequestInput(String firstName, String lastName, LocalDate dateOfBirth,
      String address, String phoneNumber) {}
  public record DecisionInput(String decision, String note) {}

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;

  public PatientCorrectionService(JdbcTemplate jdbc, Actor actor, AuditService audit) {
    this.jdbc = jdbc; this.actor = actor; this.audit = audit;
  }

  @Transactional
  public Map<String, Object> submit(RequestInput input) {
    if (!actor.user().getRole().equals("PATIENT")) throw new org.springframework.security.access.AccessDeniedException("Patient account required");
    if (input.dateOfBirth() != null && input.dateOfBirth().isAfter(LocalDate.now()))
      throw new ApiException(400, "VALIDATION_ERROR", "Date of birth cannot be in the future.");
    long patientId = actor.user().getPatientId() == null ? -1 : actor.user().getPatientId();
    Long departmentId = jdbc.query("select department_id from patients where id=?", rs ->
        rs.next() ? rs.getLong(1) : null, patientId);
    if (departmentId == null) throw new ApiException(404, "PATIENT_NOT_FOUND", "Your patient record is unavailable.");
    String first = clean(input.firstName(), 100, "First name");
    String last = clean(input.lastName(), 100, "Last name");
    String address = clean(input.address(), 500, "Address");
    String phone = clean(input.phoneNumber(), 40, "Phone number");
    if (first == null && last == null && input.dateOfBirth() == null && address == null && phone == null)
      throw new ApiException(400, "VALIDATION_ERROR", "Enter at least one correction.");
    Long id = jdbc.queryForObject("""
        insert into patient_correction_requests(department_id, patient_id, requested_by, first_name, last_name, date_of_birth, address, phone_number)
        values (?,?,?,?,?,?,?,?) returning id
        """, Long.class, departmentId, patientId, actor.user().getId(), first, last, input.dateOfBirth(), address, phone);
    audit.logForDepartment(departmentId, "PATIENT_CORRECTION_SUBMITTED", "PatientCorrectionRequest", id, "PORTAL", Map.of("patientId", patientId));
    return one(id);
  }

  public List<Map<String, Object>> mine() {
    if (!actor.user().getRole().equals("PATIENT")) throw new org.springframework.security.access.AccessDeniedException("Patient account required");
    return jdbc.queryForList("""
        select id, first_name as "firstName", last_name as "lastName", date_of_birth as "dateOfBirth", address, phone_number as "phoneNumber", status, review_note as "reviewNote", created_at as "createdAt", reviewed_at as "reviewedAt"
        from patient_correction_requests where requested_by=? order by created_at desc, id desc
        """, actor.user().getId());
  }

  public List<Map<String, Object>> queue() {
    actor.staff();
    Long department = DepartmentContext.id();
    if (department == null) return List.of();
    return jdbc.queryForList("""
        select r.id, r.patient_id as "patientId", p.patient_identifier as "patientIdentifier", p.first_name as "currentFirstName", p.last_name as "currentLastName", r.first_name as "firstName", r.last_name as "lastName", r.date_of_birth as "dateOfBirth", r.address, r.phone_number as "phoneNumber", r.status, r.created_at as "createdAt", u.username as "requestedBy"
        from patient_correction_requests r join patients p on p.id=r.patient_id join app_users u on u.id=r.requested_by
        where r.department_id=? and r.status='PENDING' order by r.created_at, r.id
        """, department);
  }

  @Transactional
  public Map<String, Object> decide(long id, DecisionInput input) {
    actor.staff();
    Long department = DepartmentContext.id();
    String decision = input.decision() == null ? "" : input.decision().strip().toUpperCase();
    if (!List.of("APPROVE", "REJECT").contains(decision)) throw new ApiException(400, "INVALID_DECISION", "Choose APPROVE or REJECT.");
    String note = clean(input.note(), 300, "Review note");
    Map<String, Object> row;
    try {
      row = jdbc.queryForMap("select * from patient_correction_requests where id=? and department_id=? and status='PENDING' for update", id, department);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      throw new ApiException(404, "REQUEST_NOT_FOUND", "That pending correction request was not found.");
    }
    long patientId = ((Number) row.get("patient_id")).longValue();
    if (decision.equals("APPROVE")) {
      jdbc.update("""
          update patients set first_name=coalesce(?, first_name), last_name=coalesce(?, last_name), date_of_birth=coalesce(?, date_of_birth), address=coalesce(?, address), phone_number=coalesce(?, phone_number), version=version+1, updated_at=now()
          where id=? and department_id=?
          """, row.get("first_name"), row.get("last_name"), row.get("date_of_birth"), row.get("address"), row.get("phone_number"), patientId, department);
    }
    Instant now = Instant.now();
    String status = decision.equals("APPROVE") ? "APPROVED" : "REJECTED";
    jdbc.update("update patient_correction_requests set status=?, review_note=?, reviewed_by=?, reviewed_at=? where id=?",
        status, note, actor.user().getId(), Timestamp.from(now), id);
    audit.log("PATIENT_CORRECTION_" + status, "PatientCorrectionRequest", id, "UI", Map.of("patientId", patientId));
    return one(id);
  }

  private Map<String, Object> one(long id) {
    return jdbc.queryForMap("""
        select id, first_name as "firstName", last_name as "lastName", date_of_birth as "dateOfBirth", address, phone_number as "phoneNumber", status, review_note as "reviewNote", created_at as "createdAt", reviewed_at as "reviewedAt"
        from patient_correction_requests where id=?
        """, id);
  }

  private static String clean(String value, int max, String label) {
    if (value == null || value.isBlank()) return null;
    String result = value.strip();
    if (result.length() > max) throw new ApiException(400, "VALIDATION_ERROR", label + " is too long.");
    return result;
  }
}
