package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientAccountLinkService {
  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  public PatientAccountLinkService(JdbcTemplate jdbc, Actor actor, AuditService audit) {
    this.jdbc = jdbc; this.actor = actor; this.audit = audit;
  }

  public List<Map<String, Object>> queue() {
    actor.admin();
    Long department = DepartmentContext.id() < 0 ? null : DepartmentContext.id();
    if (department == null) return List.of();
    return jdbc.queryForList("""
        select u.id as "userId", u.username, u.email, p.first_name as "registeredFirstName",
          p.last_name as "registeredLastName", p.date_of_birth as "registeredDateOfBirth",
          p.patient_identifier as "temporaryPatientIdentifier", p.id as "temporaryPatientId"
        from app_users u join patients p on p.id=u.patient_id
        where u.role='PATIENT' and u.enabled=true and u.email_verified=true
          and p.department_id=? and p.patient_identifier like 'SELF-%'
        order by u.created_at, u.id
        """, department);
  }

  @Transactional
  public Map<String, Object> link(long userId, long patientId, String evidence) {
    actor.admin();
    Long department = DepartmentContext.id() < 0 ? null : DepartmentContext.id();
    if (department == null) throw new ApiException(400, "DEPARTMENT_REQUIRED", "Choose a department first.");
    String review = evidence == null ? "" : evidence.strip();
    if (review.isBlank() || review.length() > 300)
      throw new ApiException(400, "EVIDENCE_REQUIRED", "Record the evidence reviewed (1 to 300 characters).");
    Map<String, Object> account;
    try {
      account = jdbc.queryForMap("""
          select u.id, u.patient_id, p.patient_identifier from app_users u
          join patients p on p.id=u.patient_id
          where u.id=? and u.role='PATIENT' and u.enabled=true and u.email_verified=true
            and p.department_id=? and p.patient_identifier like 'SELF-%' for update of u
          """, userId, department);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      throw new ApiException(404, "ACCOUNT_NOT_ELIGIBLE", "That verified portal account is no longer awaiting review.");
    }
    Integer targetCount = jdbc.query("""
        select id from patients where id=? and department_id=? and patient_identifier not like 'SELF-%' for update
        """, rs -> rs.next() ? 1 : 0, patientId, department);
    if (targetCount == 0)
      throw new ApiException(404, "PATIENT_NOT_FOUND", "Choose an existing patient record in this department.");
    Long alreadyLinked = jdbc.query("select id from app_users where patient_id=? and id<>? for update",
        rs -> rs.next() ? rs.getLong(1) : null, patientId, userId);
    if (alreadyLinked != null)
      throw new ApiException(409, "PATIENT_ALREADY_LINKED", "That patient already has a linked portal account.");
    int changed;
    try {
      changed = jdbc.update("""
          update app_users set patient_id=?, version=version+1, updated_at=now()
          where id=? and patient_id=? and email_verified=true and enabled=true
          """, patientId, userId, ((Number) account.get("patient_id")).longValue());
    } catch (org.springframework.dao.DuplicateKeyException ex) {
      throw new ApiException(409, "PATIENT_ALREADY_LINKED", "That patient already has a linked portal account.");
    }
    if (changed != 1) throw new ApiException(409, "ACCOUNT_CHANGED", "The account changed during review. Refresh and try again.");
    audit.logForDepartment(department, "PATIENT_PORTAL_ACCOUNT_LINKED", "AppUser", userId, "UI",
        Map.of("patientId", patientId, "reviewedBy", actor.user().getId(), "evidence", review));
    return Map.of("userId", userId, "patientId", patientId, "status", "LINKED");
  }
}
