package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.repository.AppUserRepository;
import com.example.hospital.repository.PatientRepository;
import com.example.hospital.security.Actor;
import com.example.hospital.security.DepartmentContext;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator-reviewed linking of a self-registered portal account to the real
 * patient record (#160).
 *
 * <p>Registration creates a {@code SELF-*} placeholder and never guesses an existing
 * identity, which is the right default. This is the reviewed repair path: an
 * administrator supplies the evidence that the account belongs to an existing
 * patient, and approval repoints the account. The placeholder is detached rather
 * than deleted, so nothing that referenced it breaks.
 */
@Service
public class PatientAccountLinkService {
  private final Actor actor;
  private final JdbcTemplate jdbc;
  private final AppUserRepository users;
  private final PatientRepository patients;
  private final AuditService audit;

  public PatientAccountLinkService(
      Actor actor, JdbcTemplate jdbc, AppUserRepository users, PatientRepository patients,
      AuditService audit) {
    this.actor = actor;
    this.jdbc = jdbc;
    this.users = users;
    this.patients = patients;
    this.audit = audit;
  }

  public record LinkInput(
      @jakarta.validation.constraints.NotNull Long targetPatientId,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
          String evidence) {}

  public record ReviewInput(
      @jakarta.validation.constraints.Size(max = 500) String note) {}

  private long department() {
    return DepartmentContext.id();
  }

  private void admin() {
    if (!java.util.Set.of("ADMIN").contains(actor.user().getRole()))
      throw new AccessDeniedException("Administrator access is required.");
  }

  /**
   * Self-registered accounts still pointing at a placeholder, with the candidates an
   * administrator is likely to consider. Matching is a hint for the reviewer, never a
   * decision: a lone name match is not evidence of identity.
   */
  public List<Map<String, Object>> queue(int page, int size) {
    admin();
    long departmentId = department();
    int from = Math.max(0, page) * Math.max(1, size);
    List<Map<String, Object>> rows = jdbc.queryForList(
        """
        select u.id as "userId", u.username, u.email, u.email_verified as "emailVerified",
          u.created_at as "registeredAt", p.id as "placeholderPatientId",
          p.patient_identifier as "placeholderIdentifier", p.first_name as "placeholderFirstName",
          p.last_name as "placeholderLastName", p.date_of_birth as "placeholderDateOfBirth",
          l.id as "linkId", l.status as "linkStatus", l.evidence, l.created_at as "requestedAt"
        from app_users u
        join patients p on p.id = u.patient_id and p.department_id = ?
        left join patient_account_links l on l.user_id = u.id and l.status in ('PENDING','LINKED')
        where u.role = 'PATIENT' and p.patient_identifier like 'SELF-%'
          and exists (select 1 from patients real
                      where real.department_id = p.department_id
                        and real.id <> p.id
                        and (lower(real.first_name) = lower(p.first_name)
                          or lower(real.last_name) = lower(p.last_name)))
        order by p.created_at desc, u.id desc
        limit ? offset ?
        """,
        departmentId, Math.max(1, size), from);
    return rows;
  }

  @Transactional
  public Map<String, Object> request(long userId, LinkInput in) {
    admin();
    long departmentId = department();
    var account = users.findById(userId).orElseThrow(ApiException::missing);
    if (!"PATIENT".equals(account.getRole()))
      throw new ApiException(400, "NOT_A_PATIENT_ACCOUNT", "Only a patient account can be linked.");
    Long placeholderId = account.getPatientId();
    if (placeholderId == null)
      throw new ApiException(400, "NO_PLACEHOLDER", "This account is not linked to a record.");

    var placeholder = patients.findById(placeholderId).orElseThrow(ApiException::missing);
    if (!placeholder.getPatientIdentifier().startsWith("SELF-"))
      throw ApiException.conflict(
          "ALREADY_RESOLVED", "This account already points at a real patient record.");
    if (placeholder.getDepartmentId() != departmentId)
      throw new AccessDeniedException("Account belongs to another department.");

    long targetId = in.targetPatientId();
    if (targetId == placeholderId)
      throw new ApiException(400, "SAME_RECORD", "Choose a different existing patient record.");
    var target = patients.findById(targetId).orElseThrow(ApiException::missing);
    if (target.getDepartmentId() != departmentId)
      throw new AccessDeniedException("Target patient belongs to another department.");

    // Linking must not take a record that another account already owns, and must not
    // steal one. The unique index on app_users.patient_id is the backstop; this makes
    // the refusal legible.
    if (Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*)>0 from app_users where patient_id=? and id<>?",
        Boolean.class, targetId, userId)))
      throw ApiException.conflict(
          "TARGET_ALREADY_LINKED", "Another account already points at that patient.");
    if (Boolean.TRUE.equals(jdbc.queryForObject(
        "select count(*)>0 from patient_account_links where user_id=? and status in ('PENDING','LINKED')",
        Boolean.class, userId)))
      throw ApiException.conflict(
          "LINK_ALREADY_OPEN", "This account already has a link awaiting review.");

    Long id = jdbc.queryForObject(
        """
        insert into patient_account_links
          (department_id, user_id, placeholder_patient_id, target_patient_id, evidence, requested_by)
        values (?,?,?,?,?,?) returning id
        """,
        Long.class, departmentId, userId, placeholderId, targetId,
        in.evidence().trim(), actor.user().getId());
    audit.log("PATIENT_ACCOUNT_LINK_REQUESTED", "PatientAccountLink", id, "UI",
        Map.of("userId", userId, "targetPatientId", targetId));
    return detail(departmentId, id);
  }

  @Transactional
  public Map<String, Object> review(long linkId, boolean approve, ReviewInput in) {
    admin();
    long departmentId = department();
    var rows = jdbc.queryForList(
        "select user_id, placeholder_patient_id, target_patient_id, status, evidence"
            + " from patient_account_links where department_id=? and id=? for update",
        departmentId, linkId);
    if (rows.isEmpty()) throw ApiException.missing();
    var row = rows.getFirst();
    if (!"PENDING".equals(row.get("status")))
      throw ApiException.conflict("ALREADY_REVIEWED", "This request was already reviewed.");
    long userId = ((Number) row.get("user_id")).longValue();
    long placeholderId = ((Number) row.get("placeholder_patient_id")).longValue();
    long targetId = ((Number) row.get("target_patient_id")).longValue();

    if (approve) {
      var account = users.findById(userId).orElseThrow(ApiException::missing);
      if (account.getPatientId() == null || account.getPatientId() != placeholderId)
        throw ApiException.conflict(
            "ACCOUNT_MOVED", "The account no longer points at the reviewed record. Recheck.");
      if (Boolean.TRUE.equals(jdbc.queryForObject(
          "select count(*)>0 from app_users where patient_id=? and id<>?",
          Boolean.class, targetId, userId)))
        throw ApiException.conflict(
            "TARGET_ALREADY_LINKED", "Another account now points at that patient.");

      // Repoint the account. The placeholder row is deliberately kept: an admission,
      // consent, AI session or link may already reference it, and removing a record a
      // chart points at is exactly the kind of silent data loss this review flow exists
      // to prevent. Archiving it is a separate, explicit decision.
      account.setPatientId(targetId);
      users.saveAndFlush(account);
    }

    jdbc.update(
        "update patient_account_links set status=?, reviewed_by=?, reviewed_at=now(),"
            + " review_note=?, updated_at=now() where department_id=? and id=?",
        approve ? "LINKED" : "REJECTED", actor.user().getId(),
        in.note() == null || in.note().isBlank() ? null : in.note().trim(),
        departmentId, linkId);
    audit.log(approve ? "PATIENT_ACCOUNT_LINKED" : "PATIENT_ACCOUNT_LINK_REJECTED",
        "PatientAccountLink", linkId, "UI", Map.of("userId", userId, "targetPatientId", targetId));
    return detail(departmentId, linkId);
  }

  private Map<String, Object> detail(long departmentId, long id) {
    return jdbc.queryForMap(
        """
        select id, user_id as "userId", placeholder_patient_id as "placeholderPatientId",
          target_patient_id as "targetPatientId", evidence, status,
          review_note as "reviewNote", created_at as "createdAt", reviewed_at as "reviewedAt"
        from patient_account_links where department_id=? and id=?
        """,
        departmentId, id);
  }
}
