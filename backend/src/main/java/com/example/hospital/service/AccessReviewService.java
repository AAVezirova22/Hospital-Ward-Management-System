package com.example.hospital.service;

import com.example.hospital.api.ApiException;
import com.example.hospital.security.Actor;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic access review for hospital owners: every workforce member of the hospital with account
 * state, last sign-in, department roles, linked doctor profile and the latest review decision.
 * Patient accounts and clinical records are never included.
 */
@Service
@Transactional(readOnly = true)
public class AccessReviewService {
  public static final Set<String> OUTCOMES = Set.of("KEEP", "CHANGE", "REVOKE");

  public record DepartmentAccess(
      long departmentId, String department, String role, Long doctorId, String doctorName) {}

  public record Review(String outcome, String note, Instant reviewedAt, String reviewedBy) {}

  public record Member(
      long userId,
      String username,
      boolean enabled,
      Instant lastLoginAt,
      boolean inactive,
      boolean owner,
      List<DepartmentAccess> departments,
      Review latestReview) {}

  public record Report(
      long hospitalId,
      String hospital,
      Instant generatedAt,
      int inactiveAfterDays,
      Map<String, Long> outcomes,
      List<Member> members) {}

  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;

  public AccessReviewService(JdbcTemplate jdbc, Actor actor, AuditService audit) {
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
  }

  public Report report(long hospitalId, int inactiveAfterDays) {
    if (inactiveAfterDays < 1 || inactiveAfterDays > 3650)
      throw new ApiException(400, "VALIDATION_ERROR", "inactiveAfterDays must be between 1 and 3650.");
    String hospital = requireOwner(hospitalId);
    Instant now = Instant.now();
    Instant inactiveBefore = now.minus(Duration.ofDays(inactiveAfterDays));
    Map<Long, List<DepartmentAccess>> departments = new LinkedHashMap<>();
    jdbc.query(
        """
        select dm.user_id, d.id, d.name, dm.role, dm.doctor_id, doc.first_name, doc.last_name
        from department_memberships dm
        join departments d on d.id = dm.department_id
        left join doctors doc on doc.id = dm.doctor_id
        where d.hospital_id = ?
        order by d.name, d.id
        """,
        rs -> {
          Long doctorId = (Long) rs.getObject(5);
          String doctorName =
              doctorId == null ? null : (rs.getString(6) + " " + rs.getString(7)).strip();
          departments
              .computeIfAbsent(rs.getLong(1), id -> new ArrayList<>())
              .add(new DepartmentAccess(rs.getLong(2), rs.getString(3), rs.getString(4), doctorId, doctorName));
        },
        hospitalId);
    Map<Long, Review> reviews = new LinkedHashMap<>();
    jdbc.query(
        """
        select distinct on (r.user_id) r.user_id, r.outcome, r.note, r.reviewed_at, reviewer.username
        from access_reviews r join app_users reviewer on reviewer.id = r.reviewed_by
        where r.hospital_id = ?
        order by r.user_id, r.reviewed_at desc, r.id desc
        """,
        rs -> {
          reviews.put(
              rs.getLong(1),
              new Review(rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getString(5)));
        },
        hospitalId);
    List<Member> members =
        jdbc.query(
            """
            select u.id, u.username, u.enabled, u.last_login_at, coalesce(hm.owner, false)
            from app_users u
            left join hospital_memberships hm on hm.user_id = u.id and hm.hospital_id = ?
            where u.role <> 'PATIENT'
              and (hm.user_id is not null or exists (
                select 1 from department_memberships dm join departments d on d.id = dm.department_id
                where dm.user_id = u.id and d.hospital_id = ?))
            order by lower(u.username), u.id
            """,
            (rs, row) -> {
              long userId = rs.getLong(1);
              Timestamp lastLogin = rs.getTimestamp(4);
              Instant lastLoginAt = lastLogin == null ? null : lastLogin.toInstant();
              return new Member(
                  userId,
                  rs.getString(2),
                  rs.getBoolean(3),
                  lastLoginAt,
                  lastLoginAt == null || lastLoginAt.isBefore(inactiveBefore),
                  rs.getBoolean(5),
                  departments.getOrDefault(userId, List.of()),
                  reviews.get(userId));
            },
            hospitalId,
            hospitalId);
    Map<String, Long> outcomes = new LinkedHashMap<>();
    for (String outcome : List.of("KEEP", "CHANGE", "REVOKE", "UNREVIEWED")) outcomes.put(outcome, 0L);
    for (Member member : members) {
      String key = member.latestReview() == null ? "UNREVIEWED" : member.latestReview().outcome();
      outcomes.merge(key, 1L, Long::sum);
    }
    return new Report(hospitalId, hospital, now, inactiveAfterDays, outcomes, members);
  }

  /** Records the owner's decision. Changing or revoking access stays a separate, explicit action. */
  @Transactional
  public Review record(long hospitalId, long userId, String outcome, String note) {
    requireOwner(hospitalId);
    String decision = outcome == null ? "" : outcome.strip().toUpperCase(Locale.ROOT);
    if (!OUTCOMES.contains(decision))
      throw new ApiException(400, "INVALID_OUTCOME", "Choose KEEP, CHANGE or REVOKE.");
    String text = note == null || note.isBlank() ? null : note.strip();
    if (text != null && text.length() > 300)
      throw new ApiException(400, "VALIDATION_ERROR", "Keep the review note under 300 characters.");
    boolean member =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
                select exists (select 1 from hospital_memberships where hospital_id = ? and user_id = ?)
                    or exists (select 1 from department_memberships dm join departments d on d.id = dm.department_id
                               where d.hospital_id = ? and dm.user_id = ?)
                """,
                Boolean.class,
                hospitalId,
                userId,
                hospitalId,
                userId));
    if (!member) throw new ApiException(404, "NOT_A_MEMBER", "That account is not a member of this hospital.");
    Instant now = Instant.now();
    jdbc.update(
        "insert into access_reviews(hospital_id, user_id, outcome, note, reviewed_by, reviewed_at) values (?,?,?,?,?,?)",
        hospitalId,
        userId,
        decision,
        text,
        actor.user().getId(),
        Timestamp.from(now));
    audit.log(
        "ACCESS_REVIEWED", "AppUser", userId, "UI", Map.of("hospitalId", hospitalId, "outcome", decision));
    return new Review(decision, text, now, actor.user().getUsername());
  }

  private String requireOwner(long hospitalId) {
    List<String> names =
        jdbc.queryForList(
            """
            select h.name from hospitals h join hospital_memberships m on m.hospital_id = h.id
            where h.id = ? and m.user_id = ? and m.owner = true
            """,
            String.class,
            hospitalId,
            actor.user().getId());
    if (names.isEmpty())
      throw new ApiException(403, "HOSPITAL_OWNER_REQUIRED", "Only a hospital owner can review access.");
    return names.getFirst();
  }
}
