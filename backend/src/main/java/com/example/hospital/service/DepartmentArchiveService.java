package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only archival for a department that has stopped operating (#162).
 *
 * <p>A unit is never deleted. Its admissions, performed procedures and reports are
 * the record of what happened while it ran; deleting the unit would either orphan
 * that history or destroy it. Archiving marks the unit closed, which refuses new
 * clinical writes and new members while leaving every existing row readable and
 * every historical report reproducible.
 */
@Service
public class DepartmentArchiveService {
  private final Actor actor;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  public DepartmentArchiveService(Actor actor, JdbcTemplate jdbc, AuditService audit) {
    this.actor = actor;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  /**
   * Only a hospital owner may close a unit. A department admin manages the work
   * inside the unit but must not be able to close it, since that is a decision
   * about the hospital's estate.
   */
  private void owner(long departmentId) {
    Long hospitalId = jdbc.queryForObject(
        "select hospital_id from departments where id=?", Long.class, departmentId);
    if (hospitalId == null) throw ApiException.missing();
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*)>0 from hospital_memberships where hospital_id=? and user_id=? and owner=true",
        Boolean.class, hospitalId, actor.user().getId())))
      throw new ApiException(403, "HOSPITAL_OWNER_REQUIRED",
          "Only a hospital owner can archive or restore a department.");
  }

  public Map<String, Object> state(long departmentId) {
    return jdbc.queryForMap("""
        select d.id, d.name, d.hospital_id as "hospitalId", d.status,
          d.archived_at as "archivedAt", d.archive_reason as "archiveReason",
          u.username as "archivedBy"
        from departments d left join app_users u on u.id = d.archived_by
        where d.id = ?
        """, departmentId);
  }

  @Transactional
  public Map<String, Object> archive(long departmentId, String reason) {
    owner(departmentId);
    String trimmed = reason == null || reason.isBlank()
        ? null : reason.trim();
    if (trimmed != null && trimmed.length() > 500)
      throw new ApiException(400, "REASON_TOO_LONG", "Use a reason of 500 characters or fewer.");
    if ("ARCHIVED".equals(status(departmentId)))
      throw ApiException.conflict("ALREADY_ARCHIVED", "This department is already archived.");

    // Archiving a unit with patients still admitted would leave them stranded with
    // no way to be discharged, so the owner has to close them out first. This is the
    // one precondition: it is a clinical decision, not a technical one.
    long openAdmissions = jdbc.queryForObject(
        "select count(*) from admissions where department_id=? and status='ACTIVE'",
        Long.class, departmentId);
    if (openAdmissions > 0)
      throw ApiException.conflict(
          "OPEN_ADMISSIONS", "Discharge or transfer the "
              + openAdmissions + " still-admitted patient(s) before archiving.");

    jdbc.update("""
        update departments set status='ARCHIVED', archived_at=now(), archived_by=?,
          archive_reason=? where id=?
        """, actor.user().getId(), trimmed, departmentId);
    audit.log("DEPARTMENT_ARCHIVED", "Department", departmentId, "UI",
        trimmed == null ? Map.of() : Map.of("reason", trimmed));
    return state(departmentId);
  }

  @Transactional
  public Map<String, Object> restore(long departmentId, String reason) {
    owner(departmentId);
    if (!"ARCHIVED".equals(status(departmentId)))
      throw ApiException.conflict("NOT_ARCHIVED", "This department is not archived.");
    String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
    if (trimmed != null && trimmed.length() > 500)
      throw new ApiException(400, "REASON_TOO_LONG", "Use a reason of 500 characters or fewer.");

    jdbc.update("""
        update departments set status='ACTIVE', archived_at=null, archived_by=null,
          archive_reason=? where id=?
        """, trimmed, departmentId);
    audit.log("DEPARTMENT_RESTORED", "Department", departmentId, "UI",
        trimmed == null ? Map.of() : Map.of("reason", trimmed));
    return state(departmentId);
  }

  /** The write guard clinical services call before creating anything in this unit. */
  public void requireWritable(long departmentId) {
    if ("ARCHIVED".equals(status(departmentId)))
      throw ApiException.conflict(
          "DEPARTMENT_ARCHIVED", "This department is archived and is read-only.");
  }

  /**
   * Archived units the caller is a member of, so a picker can label them read-only
   * instead of offering them for new work.
   */
  public List<Long> archivedIds() {
    return jdbc.queryForList("""
        select d.id from departments d
        where d.status = 'ARCHIVED'
          and exists (select 1 from department_memberships m
                      where m.department_id = d.id and m.user_id = ?)
        order by d.id
        """, Long.class, actor.user().getId());
  }

  /** Active units only, for pickers that must not offer a closed unit. */
  public long activeDepartmentCount(long hospitalId) {
    return jdbc.queryForObject(
        "select count(*) from departments where hospital_id=? and status='ACTIVE'",
        Long.class, hospitalId);
  }

  private String status(long departmentId) {
    var rows = jdbc.queryForList("select status from departments where id=?", departmentId);
    if (rows.isEmpty()) throw ApiException.missing();
    return (String) rows.getFirst().get("status");
  }
}
